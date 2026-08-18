package win.iqwqi.xiangece.core.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import win.iqwqi.xiangece.core.offline.OfflinePackArchive
import win.iqwqi.xiangece.core.offline.ResumableHttpFileDownloader
import java.util.concurrent.TimeUnit

data class OfflineOcrPackFile(
    val localName: String,
    val bytes: Long,
    val sha256: String,
    val domesticUrl: String,
    val officialUrl: String,
    val mirrorUrls: List<String> = emptyList(),
) {
    val downloadUrls: List<String>
        get() = (listOf(domesticUrl) + mirrorUrls + officialUrl)
            .filter(String::isNotBlank)
            .distinct()
}

data class OfflineOcrPackState(
    val installed: Boolean = false,
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val currentFile: String = "",
    val bundled: Boolean = false,
    val paused: Boolean = false,
    val errorMessage: String? = null,
) {
    val progress: Float
        get() = if (totalBytes <= 0L) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

object OfflineOcrPackCatalog {
    const val PP_OCRV5_MOBILE_ID = "pp_ocrv5_mobile_zh_en_onnx"
    val chineseMobile = listOf(
        OfflineOcrPackFile(
            localName = "det.onnx",
            bytes = 4_826_518L,
            sha256 = "a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d",
            domesticUrl = "https://hf-mirror.com/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx?download=true",
            officialUrl = "https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx?download=true",
            mirrorUrls = listOf(
                "https://hf-cdn.sufy.com/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx?download=true",
                "https://alpha.hf-mirror.com/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx?download=true",
            ),
        ),
        OfflineOcrPackFile(
            localName = "rec.onnx",
            bytes = 16_534_782L,
            sha256 = "da72dc72ca4dc220df0dfde68c1dedc31c58d3e76a25871122e5056227d50092",
            domesticUrl = "https://hf-mirror.com/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx?download=true",
            officialUrl = "https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx?download=true",
            mirrorUrls = listOf(
                "https://hf-cdn.sufy.com/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx?download=true",
                "https://alpha.hf-mirror.com/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx?download=true",
            ),
        ),
        OfflineOcrPackFile(
            localName = "rec.yml",
            bytes = 148_345L,
            sha256 = "5dfeb2777f6d0db8177d8128a8acfcf6e6276dc4ac73ea3bf0dc06d6a5e85d8e",
            domesticUrl = "https://hf-mirror.com/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/main/inference.yml?download=true",
            officialUrl = "https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/main/inference.yml?download=true",
            mirrorUrls = listOf(
                "https://hf-cdn.sufy.com/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/main/inference.yml?download=true",
                "https://alpha.hf-mirror.com/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/main/inference.yml?download=true",
            ),
        ),
    )

    val totalBytes: Long = chineseMobile.sumOf { it.bytes }
}

@Singleton
class OfflineOcrPackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(readInstalledState())
    val state: StateFlow<OfflineOcrPackState> = _state.asStateFlow()

    init {
        if (_state.value.installed) {
            scope.launch {
                if (!verifyInstalledFiles()) markCorrupted()
            }
        }
    }
    private var downloadJob: Job? = null
    @Volatile private var activeCall: Call? = null
    private val downloadClient = httpClient.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun isChinesePackInstalled(): Boolean = _state.value.installed && modelDirectory().isDirectory

    private fun hasBundledPack(): Boolean = runCatching {
        context.assets.open(BUNDLED_ASSET_PATH).close()
        true
    }.getOrDefault(false)

    fun modelDirectory(): File = File(
        context.filesDir,
        "ocr-offline/${OfflineOcrPackCatalog.PP_OCRV5_MOBILE_ID}",
    )

    fun modelFile(localName: String): File = File(modelDirectory(), localName)

    fun markCorrupted(message: String = "离线 OCR 包校验失败，请重新导入或下载") {
        val target = modelDirectory()
        File(target, INSTALL_MARKER).delete()
        OfflineOcrPackCatalog.chineseMobile.forEach { file ->
            File(target, file.localName).delete()
        }
        _state.value = readInstalledState().copy(
            installed = false,
            downloading = false,
            paused = false,
            errorMessage = message,
        )
    }

    suspend fun verifyInstalledFiles(): Boolean = withContext(Dispatchers.IO) {
        if (!_state.value.installed) return@withContext false
        OfflineOcrPackCatalog.chineseMobile.all { file ->
            val candidate = File(modelDirectory(), file.localName)
            candidate.isFile && candidate.length() == file.bytes && sha256(candidate) == file.sha256
        }
    }

    fun startChineseDownload() {
        if (downloadJob?.isActive == true || isChinesePackInstalled()) return
        val job = scope.launch {
            if (hasBundledPack()) importBundledPack() else install()
        }
        downloadJob = job
        job.invokeOnCompletion { if (downloadJob === job) downloadJob = null }
    }

    fun cancelDownload() {
        activeCall?.cancel()
        downloadJob?.cancel()
        _state.value = _state.value.copy(downloading = false, currentFile = "", paused = true, errorMessage = null)
    }

    fun importChinesePack(uri: Uri) {
        if (downloadJob?.isActive == true || isChinesePackInstalled()) return
        val job = scope.launch { importPack(uri) }
        downloadJob = job
        job.invokeOnCompletion { if (downloadJob === job) downloadJob = null }
    }
    fun deleteChinesePack() {
        if (downloadJob?.isActive == true) return
        modelDirectory().deleteRecursively()
        _state.value = OfflineOcrPackState(totalBytes = OfflineOcrPackCatalog.totalBytes)
    }

    private suspend fun importBundledPack() {
        val temporaryZip = File(context.cacheDir, "ocr-bundled-pack.zip")
        try {
            _state.value = _state.value.copy(
                downloading = true,
                paused = false,
                currentFile = "正在读取内置离线包",
                totalBytes = OfflineOcrPackCatalog.totalBytes,
                errorMessage = null,
            )
            context.assets.open(BUNDLED_ASSET_PATH).use { input ->
                temporaryZip.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            importPack(Uri.fromFile(temporaryZip))
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(downloading = false, paused = true, currentFile = "")
            throw cancelled
        } catch (error: Throwable) {
            Log.e("OfflineOcrPack", "Bundled OCR pack installation failed", error)
            _state.value = _state.value.copy(
                downloading = false,
                paused = false,
                currentFile = "",
                errorMessage = "内置离线 OCR 安装没有完成，可以稍后重试。",
            )
        } finally {
            temporaryZip.delete()
        }
    }

    private suspend fun importPack(uri: Uri) {
        val pack = OfflineOcrPackCatalog.chineseMobile
        val target = modelDirectory()
        val staging = File(target.parentFile, ".${OfflineOcrPackCatalog.PP_OCRV5_MOBILE_ID}.import-${System.nanoTime()}")
        try {
            _state.value = OfflineOcrPackState(
                downloading = true,
                currentFile = "本地 ZIP",
                totalBytes = OfflineOcrPackCatalog.totalBytes,
            )
            val expected = pack.associate { it.localName to it.bytes }
            val extracted = OfflinePackArchive.extract(context.contentResolver, uri, staging, expected)
            target.mkdirs()
            File(target, INSTALL_MARKER).delete()
            var imported = 0L
            pack.forEach { file ->
                val source = extracted.getValue(file.localName)
                check(source.length() == file.bytes && sha256(source) == file.sha256) {
                    "离线 OCR 资源校验失败：${file.localName}"
                }
                val part = File(target, file.localName + ".part")
                source.copyTo(part, overwrite = true)
                promotePart(part, File(target, file.localName), file.localName)
                imported += file.bytes
                _state.value = _state.value.copy(
                    downloadedBytes = imported,
                    currentFile = file.localName,
                )
            }
            val marker = File(target, INSTALL_MARKER)
            val markerPart = File(target, "$INSTALL_MARKER.part")
            markerPart.writeText(OfflineOcrPackCatalog.PP_OCRV5_MOBILE_ID, Charsets.UTF_8)
            if (marker.exists()) marker.delete()
            check(markerPart.renameTo(marker)) { "无法写入 OCR 安装标记" }
            _state.value = OfflineOcrPackState(
                installed = true,
                downloadedBytes = OfflineOcrPackCatalog.totalBytes,
                totalBytes = OfflineOcrPackCatalog.totalBytes,
            )
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(downloading = false, paused = true, currentFile = "")
            throw cancelled
        } catch (error: Throwable) {
            Log.e("OfflineOcrPack", "OCR pack import failed", error)
            _state.value = _state.value.copy(
                downloading = false,
                paused = false,
                currentFile = "",
                errorMessage = "这个离线 OCR 包无法使用，请选择对应的 ZIP 文件后重试。",
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun install() {
        val target = modelDirectory().apply { mkdirs() }
        var downloaded = OfflineOcrPackCatalog.chineseMobile.sumOf { file ->
            val finalFile = File(target, file.localName)
            if (finalFile.isFile && finalFile.length() == file.bytes && sha256(finalFile) == file.sha256) {
                file.bytes
            } else {
                File(target, file.localName + ".part")
                    .takeIf(File::isFile)
                    ?.length()
                    ?.coerceAtMost(file.bytes)
                    ?: 0L
            }
        }
        _state.value = OfflineOcrPackState(
            downloading = true,
            downloadedBytes = downloaded,
            totalBytes = OfflineOcrPackCatalog.totalBytes,
        )
        try {
            for (file in OfflineOcrPackCatalog.chineseMobile) {
                val finalFile = File(target, file.localName)
                val before = if (finalFile.isFile && finalFile.length() == file.bytes && sha256(finalFile) == file.sha256) {
                    file.bytes
                } else {
                    File(target, file.localName + ".part")
                        .takeIf(File::isFile)
                        ?.length()
                        ?.coerceAtMost(file.bytes)
                        ?: 0L
                }
                val completedBefore = downloaded - before
                _state.value = _state.value.copy(
                    currentFile = file.localName,
                    downloadedBytes = downloaded,
                    errorMessage = null,
                    paused = false,
                )
                downloadAndVerify(file, target) { current ->
                    _state.value = _state.value.copy(
                        downloadedBytes = completedBefore + current,
                    )
                }
                downloaded = completedBefore + file.bytes
            }
            val marker = File(target, INSTALL_MARKER)
            val markerPart = File(target, "$INSTALL_MARKER.part")
            markerPart.writeText(OfflineOcrPackCatalog.PP_OCRV5_MOBILE_ID, Charsets.UTF_8)
            if (marker.exists()) check(marker.delete()) { "无法更新 OCR 安装标记" }
            check(markerPart.renameTo(marker)) { "无法写入 OCR 安装标记" }
            _state.value = OfflineOcrPackState(installed = true, downloadedBytes = OfflineOcrPackCatalog.totalBytes, totalBytes = OfflineOcrPackCatalog.totalBytes)
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(downloading = false, currentFile = "", paused = true, errorMessage = null)
            throw cancelled
        } catch (error: Throwable) {
            Log.e("OfflineOcrPack", "OCR pack download failed", error)
            _state.value = _state.value.copy(downloading = false, currentFile = "", paused = false, errorMessage = "下载暂未完成，已保留已下载内容；可以稍后重试。")
        }
    }

    private suspend fun downloadAndVerify(file: OfflineOcrPackFile, target: File, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val finalFile = File(target, file.localName)
        if (finalFile.isFile) {
            if (finalFile.length() == file.bytes && sha256(finalFile) == file.sha256) return@withContext
            finalFile.delete()
        }
        val partFile = File(target, file.localName + ".part")
        if (partFile.isFile && partFile.length() >= file.bytes) {
            if (partFile.length() == file.bytes && sha256(partFile) == file.sha256) {
                promotePart(partFile, finalFile, file.localName)
                return@withContext
            }
            partFile.delete()
        }
        ResumableHttpFileDownloader.download(
            client = downloadClient,
            urls = file.downloadUrls,
            partFile = partFile,
            expectedBytes = file.bytes,
            expectedSha256 = file.sha256,
            label = file.localName,
            onProgress = onProgress,
            onCallChanged = { activeCall = it },
        )
        promotePart(partFile, finalFile, file.localName)
    }
    private fun promotePart(partFile: File, finalFile: File, name: String) {
        if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            check(partFile.delete()) { "无法清理临时 OCR 文件: " + name }
        }
    }
    private fun readInstalledState(): OfflineOcrPackState {
        val target = modelDirectory()
        val downloaded = OfflineOcrPackCatalog.chineseMobile.sumOf { file ->
            val finalFile = File(target, file.localName)
            when {
                finalFile.isFile && finalFile.length() == file.bytes -> file.bytes
                else -> File(target, file.localName + ".part")
                    .takeIf(File::isFile)
                    ?.length()
                    ?.coerceAtMost(file.bytes)
                    ?: 0L
            }
        }
        val marker = File(target, INSTALL_MARKER)
        val markerValid = marker.isFile && runCatching { marker.readText(Charsets.UTF_8).trim() == OfflineOcrPackCatalog.PP_OCRV5_MOBILE_ID }.getOrDefault(false)
        val installed = markerValid && downloaded == OfflineOcrPackCatalog.totalBytes
        return OfflineOcrPackState(
            installed = installed,
            bundled = hasBundledPack(),
            paused = !installed && downloaded > 0L,
            downloadedBytes = downloaded,
            totalBytes = OfflineOcrPackCatalog.totalBytes,
        )
    }
    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            var count: Int
            while (input.read(buffer).also { count = it } >= 0) {
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun close() {
        activeCall?.cancel()
        scope.cancel()
    }

    private companion object {
        const val INSTALL_MARKER = ".installed"
        const val BUNDLED_ASSET_PATH = "offline-packs/ocr-zh-en.zip"
    }
}

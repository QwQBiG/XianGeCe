package win.iqwqi.xiangece.feature.diting.offline

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

/** Model files are optional app data; the APK remains small when the user does not install them. */
data class DitingOfflinePackFile(
    val name: String,
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

data class DitingOfflinePack(
    val id: String,
    val title: String,
    val description: String,
    val totalBytes: Long,
    val files: List<DitingOfflinePackFile>,
)

data class DitingOfflinePackState(
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

object DitingOfflinePackCatalog {
    const val CHINESE_PARAFORMER_ID = "sherpa_streaming_paraformer_zh_en_int8"
    val chineseParaformer = DitingOfflinePack(
        id = CHINESE_PARAFORMER_ID,
        title = "中文/英文专业离线识别包",
        description = "免费、本机运行、中文优先，支持 English、中英混合和部分中文方言；约 226 MiB（下载约 237 MB）。",
        totalBytes = 165_462_184L + 71_664_561L + 75_756L,
        files = listOf(
            DitingOfflinePackFile(
                name = "encoder.int8.onnx",
                bytes = 165_462_184L,
                sha256 = "81a70226a8934e6ed92aa1d4fc486b428b5398e2f2619ed4897b7294cab90e9a",
                domesticUrl = "https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/encoder.int8.onnx?download=true",
                officialUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/encoder.int8.onnx?download=true",
                mirrorUrls = listOf(
                    "https://hf-cdn.sufy.com/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/encoder.int8.onnx?download=true",
                    "https://alpha.hf-mirror.com/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/encoder.int8.onnx?download=true",
                ),
            ),
            DitingOfflinePackFile(
                name = "decoder.int8.onnx",
                bytes = 71_664_561L,
                sha256 = "f3cca9f77bb9d93c8fcbfb63ae617b6b1ee96818df3aa3b151c40658fe38594f",
                domesticUrl = "https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/decoder.int8.onnx?download=true",
                officialUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/decoder.int8.onnx?download=true",
                mirrorUrls = listOf(
                    "https://hf-cdn.sufy.com/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/decoder.int8.onnx?download=true",
                    "https://alpha.hf-mirror.com/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/decoder.int8.onnx?download=true",
                ),
            ),
            DitingOfflinePackFile(
                name = "tokens.txt",
                bytes = 75_756L,
                sha256 = "59aba8873a2ed1e122c25fee421e25f283b63290efbde85c1f01a853d83cb6e6",
                domesticUrl = "https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/tokens.txt?download=true",
                officialUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/tokens.txt?download=true",
                mirrorUrls = listOf(
                    "https://hf-cdn.sufy.com/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/tokens.txt?download=true",
                    "https://alpha.hf-mirror.com/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/tokens.txt?download=true",
                ),
            ),
        ),
    )
}

@Singleton
class DitingOfflinePackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(readInstalledState())
    val state: StateFlow<DitingOfflinePackState> = _state.asStateFlow()

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

    fun startChineseDownload() {
        if (downloadJob?.isActive == true || isChinesePackInstalled()) return
        val job = scope.launch {
            if (hasBundledPack()) importBundledPack() else install(DitingOfflinePackCatalog.chineseParaformer)
        }
        downloadJob = job
        job.invokeOnCompletion { if (downloadJob === job) downloadJob = null }
    }

    fun cancelDownload() {
        activeCall?.cancel()
        downloadJob?.cancel()
        _state.value = _state.value.copy(downloading = false, paused = true, errorMessage = null)
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
        _state.value = DitingOfflinePackState(totalBytes = DitingOfflinePackCatalog.chineseParaformer.totalBytes)
    }

    fun modelDirectory(): File = File(context.filesDir, "diting-offline/${DitingOfflinePackCatalog.CHINESE_PARAFORMER_ID}")

    fun markCorrupted(message: String = "离线语音包校验失败，请重新导入或下载") {
        val target = modelDirectory()
        File(target, INSTALL_MARKER).delete()
        DitingOfflinePackCatalog.chineseParaformer.files.forEach { file ->
            File(target, file.name).delete()
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
        DitingOfflinePackCatalog.chineseParaformer.files.all { file ->
            val candidate = File(modelDirectory(), file.name)
            candidate.isFile && candidate.length() == file.bytes && sha256(candidate) == file.sha256
        }
    }

    private suspend fun importBundledPack() {
        val temporaryZip = File(context.cacheDir, "diting-bundled-pack.zip")
        try {
            _state.value = _state.value.copy(downloading = true, paused = false, currentFile = "正在读取内置离线包", totalBytes = DitingOfflinePackCatalog.chineseParaformer.totalBytes, errorMessage = null)
            context.assets.open(BUNDLED_ASSET_PATH).use { input ->
                temporaryZip.outputStream().use { output -> input.copyTo(output) }
            }
            importPack(Uri.fromFile(temporaryZip))
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(downloading = false, paused = true, currentFile = "")
            throw cancelled
        } catch (error: Throwable) {
            Log.e("DitingOfflinePack", "Bundled speech pack installation failed", error)
            _state.value = _state.value.copy(downloading = false, paused = false, currentFile = "", errorMessage = "内置离线语音安装没有完成，可以稍后重试。")
        } finally {
            temporaryZip.delete()
        }
    }

    private suspend fun importPack(uri: Uri) {
        val pack = DitingOfflinePackCatalog.chineseParaformer
        val target = modelDirectory()
        val staging = File(target.parentFile, ".${pack.id}.import-${System.nanoTime()}")
        try {
            _state.value = DitingOfflinePackState(
                downloading = true,
                currentFile = "本地 ZIP",
                totalBytes = pack.totalBytes,
            )
            val expected = pack.files.associate { it.name to it.bytes }
            val extracted = OfflinePackArchive.extract(context.contentResolver, uri, staging, expected)
            target.mkdirs()
            File(target, INSTALL_MARKER).delete()
            var imported = 0L
            pack.files.forEach { file ->
                val source = extracted.getValue(file.name)
                check(source.length() == file.bytes && sha256(source) == file.sha256) {
                    "离线资源校验失败：${file.name}"
                }
                val part = File(target, file.name + ".part")
                source.copyTo(part, overwrite = true)
                promotePart(part, File(target, file.name), file.name)
                imported += file.bytes
                _state.value = _state.value.copy(
                    downloadedBytes = imported,
                    currentFile = file.name,
                )
            }
            val marker = File(target, INSTALL_MARKER)
            val markerPart = File(target, "$INSTALL_MARKER.part")
            markerPart.writeText(pack.id, Charsets.UTF_8)
            if (marker.exists()) marker.delete()
            check(markerPart.renameTo(marker)) { "无法写入模型安装标记" }
            _state.value = DitingOfflinePackState(
                installed = true,
                downloadedBytes = pack.totalBytes,
                totalBytes = pack.totalBytes,
            )
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(downloading = false, paused = true, currentFile = "")
            throw cancelled
        } catch (error: Throwable) {
            Log.e("DitingOfflinePack", "Speech pack import failed", error)
            _state.value = _state.value.copy(
                downloading = false,
                paused = false,
                currentFile = "",
                errorMessage = "这个离线语音包无法使用，请选择对应的 ZIP 文件后重试。",
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun install(pack: DitingOfflinePack) {
        val target = modelDirectory()
        target.mkdirs()
        var downloaded = pack.files.sumOf { file ->
            val finalFile = File(target, file.name)
            if (finalFile.isFile && finalFile.length() == file.bytes && sha256(finalFile) == file.sha256) {
                file.bytes
            } else {
                File(target, file.name + ".part")
                    .takeIf(File::isFile)
                    ?.length()
                    ?.coerceAtMost(file.bytes)
                    ?: 0L
            }
        }
        _state.value = DitingOfflinePackState(downloading = true, paused = false, downloadedBytes = downloaded, totalBytes = pack.totalBytes)
        try {
            for (file in pack.files) {
                val finalFile = File(target, file.name)
                val before = if (finalFile.isFile && finalFile.length() == file.bytes && sha256(finalFile) == file.sha256) {
                    file.bytes
                } else {
                    File(target, file.name + ".part")
                        .takeIf(File::isFile)
                        ?.length()
                        ?.coerceAtMost(file.bytes)
                        ?: 0L
                }
                val completedBefore = downloaded - before
                _state.value = _state.value.copy(currentFile = file.name, paused = false, downloadedBytes = downloaded)
                downloadAndVerify(file, target) { current ->
                    _state.value = _state.value.copy(
                        downloadedBytes = completedBefore + current,
                        totalBytes = pack.totalBytes,
                    )
                }
                downloaded = completedBefore + file.bytes
            }
            val marker = File(target, INSTALL_MARKER)
            val markerPart = File(target, "$INSTALL_MARKER.part")
            markerPart.writeText(pack.id, Charsets.UTF_8)
            if (marker.exists()) check(marker.delete()) { "无法更新模型安装标记" }
            check(markerPart.renameTo(marker)) { "无法写入模型安装标记" }
            _state.value = DitingOfflinePackState(installed = true, downloadedBytes = pack.totalBytes, totalBytes = pack.totalBytes)
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(downloading = false, currentFile = "", paused = true, errorMessage = null)
            throw cancelled
        } catch (error: Throwable) {
            Log.e("DitingOfflinePack", "Speech pack download failed", error)
            _state.value = _state.value.copy(downloading = false, currentFile = "", paused = false, errorMessage = "下载暂未完成，已保留已下载内容；可以稍后重试。")
        }
    }

    private suspend fun downloadAndVerify(file: DitingOfflinePackFile, target: File, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val finalFile = File(target, file.name)
        if (finalFile.isFile) {
            if (finalFile.length() == file.bytes && sha256(finalFile) == file.sha256) return@withContext
            finalFile.delete()
        }
        val partFile = File(target, file.name + ".part")
        if (partFile.isFile && partFile.length() >= file.bytes) {
            if (partFile.length() == file.bytes && sha256(partFile) == file.sha256) {
                promotePart(partFile, finalFile, file.name)
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
            label = file.name,
            onProgress = onProgress,
            onCallChanged = { activeCall = it },
        )
        promotePart(partFile, finalFile, file.name)
    }
    private fun promotePart(partFile: File, finalFile: File, name: String) {
        if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            check(partFile.delete()) { "无法清理临时模型文件: " + name }
        }
    }
    private fun readInstalledState(): DitingOfflinePackState {
        val pack = DitingOfflinePackCatalog.chineseParaformer
        val target = modelDirectory()
        val downloaded = pack.files.sumOf { file ->
            val finalFile = File(target, file.name)
            when {
                finalFile.isFile && finalFile.length() == file.bytes -> file.bytes
                else -> File(target, file.name + ".part")
                    .takeIf(File::isFile)
                    ?.length()
                    ?.coerceAtMost(file.bytes)
                    ?: 0L
            }
        }
        val marker = File(target, INSTALL_MARKER)
        val markerValid = marker.isFile && runCatching { marker.readText(Charsets.UTF_8).trim() == pack.id }.getOrDefault(false)
        val installed = markerValid && downloaded == pack.totalBytes
        return DitingOfflinePackState(
            installed = installed,
            bundled = hasBundledPack(),
            paused = !installed && downloaded > 0L,
            downloadedBytes = downloaded,
            totalBytes = pack.totalBytes,
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
        const val BUNDLED_ASSET_PATH = "offline-packs/diting-zh-en-int8.zip"
    }
}

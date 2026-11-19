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
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.OkHttpClient
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
    val selectedPackId: String = DitingOfflinePackCatalog.SENSE_VOICE_ID,
    val installedPackIds: Set<String> = emptySet(),
) {
    val progress: Float
        get() = if (totalBytes <= 0L) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

object DitingOfflinePackCatalog {
    const val CHINESE_PARAFORMER_ID = "sherpa_streaming_paraformer_zh_en_int8"
    const val SENSE_VOICE_ID = "sherpa_sense_voice_small_int8"
    const val FUN_ASR_NANO_ID = "sherpa_funasr_nano_int8"
    val senseVoice: DitingOfflinePack get() = DitingModernPacks.senseVoice
    val funAsrNano: DitingOfflinePack get() = DitingModernPacks.funAsrNano
    val packs: List<DitingOfflinePack> get() = listOf(senseVoice, funAsrNano, chineseParaformer)
    fun find(id: String): DitingOfflinePack? = packs.firstOrNull { it.id == id }

    fun initialSelection(saved: String?, installedIds: Set<String>): String = when {
        saved != null && find(saved) != null -> saved
        CHINESE_PARAFORMER_ID in installedIds -> CHINESE_PARAFORMER_ID
        else -> SENSE_VOICE_ID
    }
    val chineseParaformer = DitingOfflinePack(
        id = CHINESE_PARAFORMER_ID,
        title = "中英双语 · Paraformer",
        description = "适合中文和英文课堂。可下载使用，也可导入资源包。",
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
    private val preferences = context.getSharedPreferences("diting-offline-selection", Context.MODE_PRIVATE)
    @Volatile private var selectedId = DitingOfflinePackCatalog.initialSelection(
        preferences.getString("selectedPackId", null), installedIds(),
    )
    private val _state = MutableStateFlow(readInstalledState())
    val state: StateFlow<DitingOfflinePackState> = _state.asStateFlow()
    private val verificationMutex = Mutex()
    @Volatile private var verifiedInstallFingerprint: String? = null

    init {
        preferences.edit().putString("selectedPackId", selectedId).apply()
        // Full hashing runs before loading the recognizer. Do not reread a large
        // optional model at every app launch, including when only using the timetable.
    }
    @Volatile private var downloadJob: Job? = null
    @Volatile private var activeCall: Call? = null
    private val downloadClient = httpClient.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun isChinesePackInstalled(): Boolean = _state.value.installed && modelDirectory().isDirectory

    fun selectedPack(): DitingOfflinePack = requireNotNull(DitingOfflinePackCatalog.find(selectedId))

    @Synchronized
    fun selectPack(id: String): Boolean {
        if (DitingOfflinePackCatalog.find(id) == null || downloadJob != null) return false
        if (!preferences.edit().putString("selectedPackId", id).commit()) return false
        selectedId = id
        verifiedInstallFingerprint = null
        _state.value = readInstalledState()
        return true
    }

    private fun hasBundledPack(): Boolean = runCatching {
        if (selectedId != DitingOfflinePackCatalog.CHINESE_PARAFORMER_ID) return@runCatching false
        context.assets.open(BUNDLED_ASSET_PATH).close()
        true
    }.getOrDefault(false)

    @Synchronized
    fun startChineseDownload() {
        if (downloadJob != null || isChinesePackInstalled()) return
        val pack = selectedPack()
        _state.value = _state.value.copy(downloading = true, paused = false, errorMessage = null, currentFile = "正在检查资源")
        val job = scope.launch {
            if (hasBundledPack()) importBundledPack() else install(pack)
        }
        downloadJob = job
        finishOperation(job)
    }

    @Synchronized
    fun cancelDownload() {
        val job = downloadJob ?: return
        job.cancel()
        activeCall?.cancel()
        _state.value = _state.value.copy(paused = true, errorMessage = null)
    }

    @Synchronized
    fun importChinesePack(uri: Uri) {
        if (downloadJob != null || isChinesePackInstalled()) return
        _state.value = _state.value.copy(downloading = true, paused = false, errorMessage = null, currentFile = "本地 ZIP")
        val job = scope.launch { importPack(uri) }
        downloadJob = job
        finishOperation(job)
    }

    private fun finishOperation(job: Job) {
        job.invokeOnCompletion { error ->
            synchronized(this) {
                if (downloadJob === job) {
                    downloadJob = null
                    activeCall = null
                    _state.value = _state.value.copy(
                        downloading = false, currentFile = "",
                        paused = error is CancellationException || _state.value.paused,
                    )
                }
            }
        }
    }
    @Synchronized
    fun deleteChinesePack() {
        if (downloadJob != null) return
        val deleted = modelDirectory().deleteRecursively()
        verifiedInstallFingerprint = null
        _state.value = readInstalledState().copy(errorMessage = if (deleted) null else "部分资源未能删除，请稍后重试")
    }

    fun modelDirectory(packId: String = selectedId): File {
        require(DitingOfflinePackCatalog.find(packId) != null) { "未知的离线语音包" }
        return File(context.filesDir, "diting-offline/$packId")
    }

    @Synchronized
    fun markCorrupted(message: String = "离线语音包校验失败，请重新导入或下载", packId: String = selectedId) {
        val target = modelDirectory(packId)
        verifiedInstallFingerprint = null
        File(target, INSTALL_MARKER).delete()
        // Keep good files for a later repair; the downloader verifies and replaces only bad files.
        if (selectedId != packId) {
            _state.value = _state.value.copy(installedPackIds = installedIds())
            return
        }
        _state.value = readInstalledState().copy(
            installed = false,
            downloading = false,
            paused = false,
            errorMessage = message,
        )
    }

    suspend fun verifyInstalledFiles(packId: String = selectedId): Boolean = withContext(Dispatchers.IO) {
        val currentFingerprint = installedFingerprint(packId) ?: return@withContext false
        if (verifiedInstallFingerprint == currentFingerprint) return@withContext true
        verificationMutex.withLock {
            val lockedFingerprint = installedFingerprint(packId) ?: return@withLock false
            if (verifiedInstallFingerprint == lockedFingerprint) return@withLock true
            val valid = requireNotNull(DitingOfflinePackCatalog.find(packId)).files.all { file ->
                val candidate = File(modelDirectory(packId), file.name)
                candidate.isFile && sha256(candidate) == file.sha256
            }
            verifiedInstallFingerprint = lockedFingerprint.takeIf { valid }
            valid
        }
    }

    private suspend fun importBundledPack() {
        val temporaryZip = File(context.cacheDir, "diting-bundled-pack.zip")
        try {
            _state.value = _state.value.copy(downloading = true, paused = false, currentFile = "正在读取内置离线包", totalBytes = selectedPack().totalBytes, errorMessage = null)
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
        val pack = selectedPack()
        val target = modelDirectory()
        val staging = File(target.parentFile, ".${pack.id}.import-${System.nanoTime()}")
        try {
            _state.value = readInstalledState().copy(
                downloading = true,
                paused = false,
                currentFile = "本地 ZIP",
                totalBytes = pack.totalBytes,
            )
            val operationContext = coroutineContext
            val extracted = context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选离线资源" }
                DitingPackFiles.extract(input, staging, pack) { operationContext.ensureActive() }
            }
            target.mkdirs()
            File(target, INSTALL_MARKER).delete()
            var imported = 0L
            pack.files.forEach { file ->
                coroutineContext.ensureActive()
                val source = extracted.getValue(file.name)
                val part = File(target, file.name + ".part")
                check(part.parentFile!!.mkdirs() || part.parentFile!!.isDirectory)
                if (part.exists()) check(part.delete())
                check(source.renameTo(part)) { "无法安装资源文件：${file.name}" }
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
            _state.value = readInstalledState()
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
        val target = modelDirectory(pack.id)
        try {
            check(target.mkdirs() || target.isDirectory) { "无法创建模型目录" }
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
            _state.value = readInstalledState().copy(downloading = true, paused = false, downloadedBytes = downloaded, totalBytes = pack.totalBytes)
            for (file in pack.files) {
                coroutineContext.ensureActive()
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
            _state.value = readInstalledState()
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(downloading = false, currentFile = "", paused = true, errorMessage = null)
            throw cancelled
        } catch (error: Throwable) {
            Log.e("DitingOfflinePack", "Speech pack download failed", error)
            _state.value = _state.value.copy(downloading = false, currentFile = "", paused = false, errorMessage = "下载暂未完成，已保留已下载内容；可以稍后重试。")
        }
    }

    private suspend fun downloadAndVerify(file: DitingOfflinePackFile, target: File, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val finalFile = DitingPackFiles.destination(target, file.name)
        check(finalFile.parentFile!!.mkdirs() || finalFile.parentFile!!.isDirectory)
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
        if (finalFile.exists()) check(finalFile.delete()) { "无法替换模型文件: " + name }
        if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            check(partFile.delete()) { "无法清理临时模型文件: " + name }
        }
    }
    private fun readInstalledState(): DitingOfflinePackState {
        val pack = selectedPack()
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
        val installedIds = installedIds()
        val installed = pack.id in installedIds
        return DitingOfflinePackState(
            installed = installed,
            bundled = hasBundledPack(),
            paused = !installed && downloaded > 0L,
            downloadedBytes = downloaded,
            totalBytes = pack.totalBytes,
            selectedPackId = pack.id,
            installedPackIds = installedIds,
        )
    }
    private fun installedIds(): Set<String> = DitingOfflinePackCatalog.packs
        .filter { installedFingerprint(it.id) != null }.mapTo(linkedSetOf()) { it.id }

    internal fun installedFingerprint(packId: String): String? {
        val pack = requireNotNull(DitingOfflinePackCatalog.find(packId))
        val target = modelDirectory(packId)
        val marker = File(target, INSTALL_MARKER)
        val markerValid = marker.isFile && runCatching {
            marker.readText(Charsets.UTF_8).trim() == pack.id
        }.getOrDefault(false)
        if (!markerValid) return null
        val parts = mutableListOf(packId, marker.lastModified().toString())
        for (expected in pack.files) {
            val file = File(target, expected.name)
            if (!file.isFile || file.length() != expected.bytes) return null
            parts += "${expected.name}:${file.length()}:${file.lastModified()}"
        }
        return parts.joinToString("|")
    }

    private suspend fun sha256(file: File): String {
        val operationContext = coroutineContext
        return DitingPackFiles.sha256(file) { operationContext.ensureActive() }
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

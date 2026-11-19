package win.iqwqi.xiangece.feature.diting.offline

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Timing is relative to this PCM segment, never an absolute recording timestamp. */
data class DitingOfflineTranscript(
    val text: String,
    val language: String = "zh/en",
    val confidence: Float? = null,
    val durationMillis: Long = 0,
    val modelPackId: String = DitingOfflinePackCatalog.CHINESE_PARAFORMER_ID,
    val tokens: List<String> = emptyList(),
    val tokenTimestampsMillis: List<Long> = emptyList(),
    val timestampsEstimated: Boolean = true,
)

@Singleton
class DitingOfflineTranscriber @Inject constructor(
    private val packManager: DitingOfflinePackManager,
) {
    private val recognizerMutex = Mutex()
    private var recognizer: DitingOfflineRuntime? = null
    private var recognizerKey: String? = null
    fun isConfigured(): Boolean = packManager.isChinesePackInstalled()

    /** Loads the native model before the first classroom segment arrives. */
    suspend fun prepare(glossary: String = ""): Result<Unit> = withContext(Dispatchers.Default) {
        var result = prepareOnce(glossary)
        if (result.isFailure && result.exceptionOrNull() !is OutOfMemoryError && isConfigured()) {
            // ONNX 资源正在释放时可能出现瞬时初始化失败，只做一次有界重试。
            delay(PREPARE_RETRY_DELAY_MILLIS)
            result = prepareOnce(glossary)
        }
        result
    }

    private suspend fun prepareOnce(glossary: String): Result<Unit> = cancellableResult {
        recognizerMutex.withLock {
            try {
                getOrCreateRecognizer(glossary)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                releaseLocked()
                throw error
            }
        }
        Unit
    }

    suspend fun transcribe(audioFile: File, glossary: String): Result<DitingOfflineTranscript> = withContext(Dispatchers.Default) {
        cancellableResult {
            check(audioFile.isFile) { "音频分段不存在" }
            val operationContext = coroutineContext
            val audio = DitingPcmAudioReader.read(audioFile) { operationContext.ensureActive() }
            recognizerMutex.withLock {
                try {
                    val result = getOrCreateRecognizer(glossary).decode(audio) { operationContext.ensureActive() }
                    if (result.text.isBlank()) throw NoSpeechDetectedException()
                    result
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    // Release under the same lock, so a late error cannot unload another task's model.
                    if (error !is NoSpeechDetectedException) releaseLocked()
                    throw error
                }
            }
        }
    }

    suspend fun release() = recognizerMutex.withLock {
        releaseLocked()
    }

    private suspend fun getOrCreateRecognizer(glossary: String): DitingOfflineRuntime {
        coroutineContext.ensureActive()
        val pack = packManager.selectedPack()
        val modelDir = packManager.modelDirectory(pack.id)
        val fingerprint = packManager.installedFingerprint(pack.id)
            ?: error("所选离线语音包尚未安装或文件不完整")
        val supportedGlossary = DitingOfflineModelOptions.glossaryFor(pack.id, glossary)
        val key = "$fingerprint|$supportedGlossary"
        val existing = recognizer
        if (existing != null && recognizerKey == key) return existing
        if (!packManager.verifyInstalledFiles(pack.id)) {
            packManager.markCorrupted(packId = pack.id)
            error("所选离线语音包校验失败，请重新导入或下载")
        }
        coroutineContext.ensureActive()
        releaseLocked()
        val created = DitingOfflineModelOptions.create(pack, modelDir, supportedGlossary)
        try {
            coroutineContext.ensureActive()
        } catch (cancelled: CancellationException) {
            created.release()
            throw cancelled
        }
        recognizer = created
        recognizerKey = key
        return created
    }
    private fun releaseLocked() {
        val previous = recognizer
        recognizer = null
        recognizerKey = null
        previous?.release()
    }

    private suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
    private class NoSpeechDetectedException : IllegalStateException(
        "离线模型未识别到文字（音频可能过短、音量过低或只有环境声）",
    )

    private companion object {
        const val PREPARE_RETRY_DELAY_MILLIS = 300L
    }
}

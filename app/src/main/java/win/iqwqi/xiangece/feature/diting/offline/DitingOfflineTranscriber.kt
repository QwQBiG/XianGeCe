package win.iqwqi.xiangece.feature.diting.offline

import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Result of the on-device Paraformer pass. */
data class DitingOfflineTranscript(
    val text: String,
    val language: String = "zh/en",
    val confidence: Float? = null,
)

@Singleton
class DitingOfflineTranscriber @Inject constructor(
    private val packManager: DitingOfflinePackManager,
) {
    private val recognizerMutex = Mutex()
    private var recognizer: OnlineRecognizer? = null
    private var recognizerDirectory: String? = null
    fun isConfigured(): Boolean = packManager.isChinesePackInstalled()

    /** Loads the native model before the first classroom segment arrives. */
    suspend fun prepare(): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            check(isConfigured()) { "中文/英文离线识别包尚未安装" }
            recognizerMutex.withLock { getOrCreateRecognizer(packManager.modelDirectory()); Unit }
        }.also { result ->
            if (result.isFailure) releaseAfterFailure()
        }
    }

    suspend fun transcribe(audioFile: File, glossary: String): Result<DitingOfflineTranscript> = withContext(Dispatchers.Default) {
        val result = runCatching {
            check(isConfigured()) { "中文/英文离线识别包尚未安装" }
            check(audioFile.isFile) { "音频分段不存在" }
            val modelDir = packManager.modelDirectory()
            recognizerMutex.withLock {
                val activeRecognizer = getOrCreateRecognizer(modelDir)
                val stream = activeRecognizer.createStream(glossaryToHotwords(glossary))
                try {
                    feedWav(stream = stream, recognizer = activeRecognizer, audioFile = audioFile)
                    stream.inputFinished()
                    while (activeRecognizer.isReady(stream)) activeRecognizer.decode(stream)
                    val text = activeRecognizer.getResult(stream).text.trim()
                    check(text.isNotBlank()) { "离线模型未识别到文字（音频可能过短、音量过低或只有环境声）" }
                    DitingOfflineTranscript(text = text)
                } finally {
                    stream.release()
                }
            }
        }
        if (result.isFailure) releaseAfterFailure()
        result
    }

    suspend fun release() = recognizerMutex.withLock {
        recognizer?.release()
        recognizer = null
        recognizerDirectory = null
    }

    private suspend fun releaseAfterFailure() = recognizerMutex.withLock {
        recognizer?.release()
        recognizer = null
        recognizerDirectory = null
    }
    private suspend fun getOrCreateRecognizer(modelDir: File): OnlineRecognizer {
        val existing = recognizer
        if (existing != null && recognizerDirectory == modelDir.absolutePath) return existing
        if (!packManager.verifyInstalledFiles()) {
            packManager.markCorrupted()
            error("中文/英文离线识别包校验失败，请重新导入或下载")
        }
        existing?.release()
        val created = OnlineRecognizer(
            config = OnlineRecognizerConfig(
                modelConfig = OnlineModelConfig(
                    paraformer = OnlineParaformerModelConfig(
                        encoder = File(modelDir, "encoder.int8.onnx").absolutePath,
                        decoder = File(modelDir, "decoder.int8.onnx").absolutePath,
                    ),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = 2,
                    modelType = "paraformer",
                ),
                enableEndpoint = false,
            ),
        )
        recognizer = created
        recognizerDirectory = modelDir.absolutePath
        return created
    }
    private fun feedWav(
        stream: com.k2fsa.sherpa.onnx.OnlineStream,
        recognizer: OnlineRecognizer,
        audioFile: File,
    ) {
        FileInputStream(audioFile).use { input ->
            val header = ByteArray(44)
            var headerRead = 0
            while (headerRead < header.size) {
                val count = input.read(header, headerRead, header.size - headerRead)
                check(count > 0) { "WAV 文件头不完整" }
                headerRead += count
            }
            val bytes = ByteArray(16_384)
            var carryLowByte = -1
            while (true) {
                val count = input.read(bytes)
                if (count <= 0) break
                val samples = FloatArray((count + if (carryLowByte >= 0) 1 else 0) / 2)
                var byteIndex = 0
                var sampleIndex = 0
                if (carryLowByte >= 0) {
                    samples[sampleIndex++] = pcmSample(carryLowByte, bytes[0].toInt())
                    byteIndex = 1
                    carryLowByte = -1
                }
                while (byteIndex + 1 < count) {
                    samples[sampleIndex++] = pcmSample(bytes[byteIndex].toInt(), bytes[byteIndex + 1].toInt())
                    byteIndex += 2
                }
                if (byteIndex < count) {
                    carryLowByte = bytes[byteIndex].toInt() and 0xff
                }
                if (sampleIndex > 0) {
                    stream.acceptWaveform(samples.copyOf(sampleIndex), 16_000)
                    while (recognizer.isReady(stream)) recognizer.decode(stream)
                }
            }
        }
    }

    private fun pcmSample(lowByte: Int, highByte: Int): Float {
        val pcm = (highByte shl 8) or (lowByte and 0xff)
        return pcm / 32768.0f
    }
    private fun glossaryToHotwords(glossary: String): String = glossary
        .split(',', '，', '\n', '\r')
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(" ")
}
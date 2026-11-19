package win.iqwqi.xiangece.feature.diting.offline

import com.k2fsa.sherpa.onnx.OfflineFunAsrNanoModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import java.io.File

internal interface DitingOfflineRuntime {
    fun decode(audio: DitingPcmAudio, checkCancelled: () -> Unit): DitingOfflineTranscript
    fun release()
}

internal object DitingOfflineModelOptions {
    fun glossaryFor(packId: String, glossary: String): String {
        if (packId != DitingOfflinePackCatalog.FUN_ASR_NANO_ID) return ""
        val words = glossary.split(',', '，', ';', '；', '\n', '\r')
            .map(String::trim).filter { it.length in 1..32 }
            .filter { word -> word.all { it.isLetterOrDigit() || it in " .-+()（）" } }
            .distinct().take(24)
        return buildString {
            for (word in words) {
                if (length + word.length + 1 > 240) break
                if (isNotEmpty()) append(',')
                append(word)
            }
        }
    }

    fun create(pack: DitingOfflinePack, dir: File, glossary: String): DitingOfflineRuntime =
        if (pack.id == DitingOfflinePackCatalog.CHINESE_PARAFORMER_ID) {
            DitingLegacyRuntime(dir)
        } else {
            val model = when (pack.id) {
                DitingOfflinePackCatalog.SENSE_VOICE_ID -> OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = File(dir, "model.int8.onnx").absolutePath,
                        useInverseTextNormalization = true,
                    ),
                    tokens = File(dir, "tokens.txt").absolutePath,
                    numThreads = 2,
                )
                DitingOfflinePackCatalog.FUN_ASR_NANO_ID -> OfflineModelConfig(
                    funasrNano = OfflineFunAsrNanoModelConfig(
                        encoderAdaptor = File(dir, "encoder_adaptor.int8.onnx").absolutePath,
                        llm = File(dir, "llm.int8.onnx").absolutePath,
                        embedding = File(dir, "embedding.int8.onnx").absolutePath,
                        tokenizer = File(dir, "Qwen3-0.6B").absolutePath,
                        // 1.13.4 consumes this CSV in BuildUserPrompt, at recognizer construction.
                        hotwords = glossary,
                        maxNewTokens = 512,
                        temperature = 1e-6f,
                    ),
                    numThreads = 2,
                )
                else -> error("未知的离线语音包")
            }
            DitingBatchRuntime(pack.id, OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = model)))
        }
}

private class DitingBatchRuntime(
    private val packId: String,
    private val recognizer: OfflineRecognizer,
) : DitingOfflineRuntime {
    override fun decode(audio: DitingPcmAudio, checkCancelled: () -> Unit): DitingOfflineTranscript {
        checkCancelled()
        // createStream(hotwords) is transducer-only and exits native code for these models.
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(audio.samples, audio.sampleRate)
            checkCancelled()
            // JNI has no cancellation API. Never free this stream/model during decode.
            recognizer.decode(stream)
            checkCancelled()
            val result = recognizer.getResult(stream)
            return DitingOfflineTranscript(
                text = result.text.trim(),
                language = result.lang.ifBlank { "auto" },
                durationMillis = audio.durationMillis,
                modelPackId = packId,
                tokenTimestampsMillis = safeTimestamps(result.tokens.size, result.timestamps, audio.durationMillis),
                tokens = result.tokens.toList(),
                // Nano evenly distributes tokens over the clip; these are not forced alignments.
                timestampsEstimated = true,
            )
        } finally {
            stream.release()
        }
    }

    override fun release() = recognizer.release()
}

internal fun safeTimestamps(count: Int, seconds: FloatArray, durationMillis: Long): List<Long> {
    if (count != seconds.size || seconds.any { !it.isFinite() || it < 0 }) return emptyList()
    val values = seconds.map { (it * 1000).toLong() }
    if (values.zipWithNext().any { (a, b) -> a > b }) return emptyList()
    if (values.any { it > durationMillis }) return emptyList()
    return values
}

private class DitingLegacyRuntime(dir: File) : DitingOfflineRuntime {
    private val recognizer = OnlineRecognizer(
        config = OnlineRecognizerConfig(
            modelConfig = OnlineModelConfig(
                paraformer = OnlineParaformerModelConfig(
                    encoder = File(dir, "encoder.int8.onnx").absolutePath,
                    decoder = File(dir, "decoder.int8.onnx").absolutePath,
                ),
                tokens = File(dir, "tokens.txt").absolutePath,
                numThreads = 2,
                modelType = "paraformer",
            ),
            enableEndpoint = false,
        ),
    )

    override fun decode(audio: DitingPcmAudio, checkCancelled: () -> Unit): DitingOfflineTranscript {
        val stream = recognizer.createStream()
        try {
            var offset = 0
            while (offset < audio.samples.size) {
                checkCancelled()
                val end = minOf(offset + 8_192, audio.samples.size)
                stream.acceptWaveform(audio.samples.copyOfRange(offset, end), audio.sampleRate)
                while (recognizer.isReady(stream)) {
                    checkCancelled()
                    recognizer.decode(stream)
                }
                offset = end
            }
            stream.inputFinished()
            while (recognizer.isReady(stream)) {
                checkCancelled()
                recognizer.decode(stream)
            }
            checkCancelled()
            val result = recognizer.getResult(stream)
            return DitingOfflineTranscript(
                text = result.text.trim(),
                durationMillis = audio.durationMillis,
                modelPackId = DitingOfflinePackCatalog.CHINESE_PARAFORMER_ID,
                tokens = result.tokens.toList(),
                tokenTimestampsMillis = safeTimestamps(result.tokens.size, result.timestamps, audio.durationMillis),
                timestampsEstimated = true,
            )
        } finally {
            stream.release()
        }
    }

    override fun release() = recognizer.release()
}

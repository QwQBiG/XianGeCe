package win.iqwqi.xiangece.feature.diting.transcription

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

/**
 * Uses Android's on-device recognizer with an injected PCM source when the device supports it.
 * The recognizer never opens a second microphone capture in this mode.
 */
@Singleton
class DitingSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val feederScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recognizer: SpeechRecognizer? = null
    private var readSide: ParcelFileDescriptor? = null
    private var writeSide: ParcelFileDescriptor? = null
    private var feederJob: Job? = null
    private var pcmChannel: Channel<ByteArray>? = null
    private var finishSignal: CompletableDeferred<Unit>? = null
    private val stopping = AtomicBoolean(false)
    private val audioGapReported = AtomicBoolean(false)
    private var segmentedCallbackObserved = false

    fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun start(
        languageMode: String,
        biasingStrings: List<String>,
        onPartial: (String) -> Unit,
        onFinal: (String, String, Float?) -> Unit,
        onError: (String) -> Unit,
        onAudioGap: () -> Unit = {},
    ): Boolean {
        if (!isAvailable() || recognizer != null) return false
        return runCatching {
            stopping.set(false)
            audioGapReported.set(false)
            val pipe = ParcelFileDescriptor.createPipe()
            readSide = pipe[0]
            writeSide = pipe[1]
            segmentedCallbackObserved = false
            finishSignal = CompletableDeferred()
            val value = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            value.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    resultText(partialResults)?.let(onPartial)
                }

                override fun onResults(results: android.os.Bundle?) {
                    if (!segmentedCallbackObserved) {
                        results?.let { bundle ->
                            resultText(bundle)?.let { onFinal(it, detectedLanguage(bundle), confidence(bundle)) }
                        }
                        finishSignal?.complete(Unit)
                    }
                }

                override fun onSegmentResults(segmentResults: android.os.Bundle) {
                    segmentedCallbackObserved = true
                    resultText(segmentResults)?.let { onFinal(it, detectedLanguage(segmentResults), confidence(segmentResults)) }
                }

                override fun onEndOfSegmentedSession() {
                    finishSignal?.complete(Unit)
                }

                override fun onError(error: Int) {
                    finishSignal?.complete(Unit)
                    onError("系统识别服务错误：$error")
                }

                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readSide)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE, true)
                    if (languageMode == "auto" || languageMode == "mixed") {
                        putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    }
                    if (languageMode == "mixed") {
                        putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, true)
                        putStringArrayListExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                            arrayListOf("zh-CN", "en-US"),
                        )
                    }
                }
                if (biasingStrings.isNotEmpty()) {
                    putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(biasingStrings))
                }
                when (languageMode) {
                    "zh" -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    "en" -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    "auto" -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    "mixed" -> Unit
                    else -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                }
            }
            pcmChannel = Channel(
                capacity = 256,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
                onUndeliveredElement = {
                    if (!stopping.get() && audioGapReported.compareAndSet(false, true)) onAudioGap()
                },
            )
            feederJob = feederScope.launch {
                val output = FileOutputStream(writeSide!!.fileDescriptor)
                try {
                    for (bytes in pcmChannel!!) output.write(bytes)
                    output.flush()
                } finally {
                    output.close()
                }
            }
            recognizer = value
            value.startListening(intent)
        }.onFailure {
            stopping.set(true)
            pcmChannel?.close()
            pcmChannel = null
            feederJob?.cancel()
            feederJob = null
            runCatching { recognizer?.destroy() }
            recognizer = null
            closeResources()
            finishSignal = null
        }.isSuccess
    }

    fun offerPcm(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        pcmChannel?.trySend(buffer.copyOfRange(offset, offset + length))
    }

    fun stop() {
        stopping.set(true)
        finishSignal?.complete(Unit)
        pcmChannel?.close()
        pcmChannel = null
        feederJob?.cancel()
        feederJob = null
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        closeResources()
        finishSignal = null
    }

    /** Lets the recognizer emit its final result before the service tears it down. */
    suspend fun finish(timeoutMillis: Long = 2_500L) {
        val value = recognizer ?: return
        stopping.set(true)
        pcmChannel?.close()
        pcmChannel = null
        feederJob?.let { withTimeoutOrNull(timeoutMillis) { it.join() } }
        runCatching { value.stopListening() }
        finishSignal?.let { withTimeoutOrNull(timeoutMillis) { it.await() } }
        runCatching { value.destroy() }
        recognizer = null
        feederJob = null
        finishSignal = null
        closeResources()
    }

    private fun closeResources() {
        runCatching { writeSide?.close() }
        runCatching { readSide?.close() }
        writeSide = null
        readSide = null
    }

    private fun resultText(bundle: android.os.Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun detectedLanguage(bundle: android.os.Bundle): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            bundle.getString(SpeechRecognizer.DETECTED_LANGUAGE).orEmpty()
        } else ""

    private fun confidence(bundle: android.os.Bundle): Float? =
        bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()?.takeIf { it >= 0f }
}
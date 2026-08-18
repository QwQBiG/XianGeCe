package win.iqwqi.xiangece.feature.diting.audio

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class DitingAudioChunk(
    val sequence: Int,
    val file: File,
    val startMillis: Long,
    val endMillis: Long,
)

data class DitingRecordingResult(
    val audioBytes: Long,
    val durationMillis: Long,
    val failure: Throwable? = null,
)

/** Captures PCM once and writes both a playable local recording and short WAV chunks. */
class DitingPcmRecorder(
    private val files: DitingAudioFiles,
    private val segmentFile: (Int) -> File,
    private val onChunkReady: suspend (DitingAudioChunk) -> Unit,
    private val onPcmRead: ((ByteArray, Int, Int) -> Unit)? = null,
    private val preferredInputDevice: AudioDeviceInfo? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var fullWriter: DitingWavFileWriter? = null
    private var chunkWriter: DitingWavFileWriter? = null
    private var loopJob: Job? = null
    private var chunkWorker: Job? = null
    private val chunkChannel = Channel<DitingAudioChunk>(Channel.UNLIMITED)
    @Volatile private var paused = false
    @Volatile private var stopRequested = false
    @Volatile private var failure: Throwable? = null
    @Volatile private var totalBytes = 0L
    private var chunkBytes = 0L
    private var sequence = 0
    private val writerMutex = Mutex()

    @SuppressLint("MissingPermission")
    fun start(): Result<Unit> = runCatching {
        val minimumBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
        )
        require(minimumBuffer > 0) { "设备不支持课堂录音格式" }
        val bufferSize = maxOf(minimumBuffer, TARGET_CHUNK_BYTES / 4, 4_096)
        val record = createAudioRecord(bufferSize)
        audioRecord = record
        files.directory.mkdirs()
        files.segmentsDirectory.mkdirs()
        fullWriter = DitingWavFileWriter(files.recording)
        chunkWorker = scope.launch {
            for (chunk in chunkChannel) {
                runCatching { onChunkReady(chunk) }
                    .onFailure { error ->
                        // 分段转写或数据库回调失败时，完整录音仍然是可用资产；
                        // 不把它升级为录音失败，用户可以在结束后重试该分段。
                        Log.e("DitingPcmRecorder", "Chunk callback failed for ${chunk.sequence}", error)
                    }
            }
        }
        loopJob = scope.launch {
            try {
                readLoop(record, ByteArray(4_096), bufferSize)
            } catch (error: Throwable) {
                failure = error
                stopRequested = true
            }
        }
    }.onFailure {
        releaseAudioEffects()
        audioRecord?.release()
        audioRecord = null
        fullWriter?.close()
        fullWriter = null
        chunkChannel.close()
        chunkWorker?.cancel()
        scope.cancel()
    }

    fun reassertPreferredInputRoute() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (preferredInputDevice == null) return
        val record = audioRecord ?: return
        if (!requestPreferredInputRoute(record)) {
            Log.w("DitingPcmRecorder", "The system did not accept the preferred built-in microphone route")
        }
    }

    private fun requestPreferredInputRoute(record: AudioRecord): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val device = preferredInputDevice ?: return false
        return runCatching { record.setPreferredDevice(device) }.getOrDefault(false)
    }

    fun currentInputRouteLabel(): String {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioRecord?.routedDevice?.type
        } else {
            null
        }
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "手机麦克风"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机麦克风"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙通话麦克风"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙 LE 耳机麦克风"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 耳机麦克风"
            null -> "麦克风路由未确认"
            else -> "其他麦克风路由"
        }
    }
    fun currentDurationMillis(): Long = bytesToMillis(totalBytes)

    suspend fun pause() {
        if (paused || stopRequested) return
        paused = true
        audioRecord?.runCatching { stop() }
        writerMutex.withLock { finalizeChunk() }
    }

    suspend fun resume() {
        if (!paused || stopRequested) return
        val record = checkNotNull(audioRecord) { "录音设备已释放" }
        try {
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                record.startRecording()
            }
            // 暂停期间蓝牙或系统音频策略可能发生变化，恢复后再次请求手机麦克风。
            preferredInputDevice?.let { device -> if (!requestPreferredInputRoute(record)) Log.w("DitingPcmRecorder", "Preferred microphone route request was rejected: type=${device.type}") }
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风恢复失败" }
            paused = false
        } catch (error: Throwable) {
            // 保持暂停状态，让上层可以安全地结束并保留已经录下的内容。
            paused = true
            throw error
        }
    }

    suspend fun stop(): DitingRecordingResult {
        stopRequested = true
        audioRecord?.runCatching { stop() }
        loopJob?.join()
        writerMutex.withLock { finalizeChunk() }
        chunkChannel.close()
        chunkWorker?.join()
        chunkWorker = null
        fullWriter?.close()
        fullWriter = null
        releaseAudioEffects()
        audioRecord?.release()
        audioRecord = null
        scope.cancel()
        return DitingRecordingResult(
            audioBytes = totalBytes,
            durationMillis = bytesToMillis(totalBytes),
            failure = failure,
        )
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(bufferSize: Int): AudioRecord {
        // 先使用普通麦克风输入，避免蓝牙通话通道把耳机麦克风作为录音源；
        // 只有设备不支持时才退回语音识别输入。
        val record = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
        ).asSequence().mapNotNull { source ->
            runCatching {
                AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
            }.getOrNull()?.also { candidate ->
                if (candidate.state != AudioRecord.STATE_INITIALIZED) candidate.release()
            }
        }.firstOrNull { it.state == AudioRecord.STATE_INITIALIZED }
            ?: error("麦克风初始化失败")
        return try {
            preferredInputDevice?.let { device -> if (!requestPreferredInputRoute(record)) Log.w("DitingPcmRecorder", "Preferred microphone route request was rejected: type=${device.type}") }
            noiseSuppressor = createNoiseSuppressor(record.audioSessionId)
            automaticGainControl = createAutomaticGainControl(record.audioSessionId)
            record.startRecording()
            // setPreferredDevice 是“优先请求”而不是系统级强制；启动后再次请求，
            // 尽量抵抗蓝牙设备连接时音频策略的自动切换。
            preferredInputDevice?.let { device -> if (!requestPreferredInputRoute(record)) Log.w("DitingPcmRecorder", "Preferred microphone route request was rejected: type=${device.type}") }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && preferredInputDevice != null) {
                val actualType = record.routedDevice?.type
                if (actualType != null && actualType != preferredInputDevice.type) {
                    Log.w("DitingPcmRecorder", "Preferred input route was not selected: actual=$actualType preferred=${preferredInputDevice.type}")
                }
            }
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风未进入录音状态" }
            record
        } catch (error: Throwable) {
            releaseAudioEffects()
            record.release()
            throw error
        }
    }

    private fun reopenAudioRecord(current: AudioRecord, bufferSize: Int): AudioRecord? {
        if (stopRequested || paused) return null
        releaseAudioEffects()
        runCatching { current.release() }
        audioRecord = null
        return runCatching {
            createAudioRecord(bufferSize).also { audioRecord = it }
        }.getOrNull()
    }
    private fun createNoiseSuppressor(sessionId: Int): NoiseSuppressor? =
        runCatching { NoiseSuppressor.create(sessionId)?.also { it.enabled = true } }.getOrNull()

    private fun createAutomaticGainControl(sessionId: Int): AutomaticGainControl? =
        runCatching { AutomaticGainControl.create(sessionId)?.also { it.enabled = true } }.getOrNull()

    private fun releaseAudioEffects() {
        runCatching { noiseSuppressor?.release() }
        runCatching { automaticGainControl?.release() }
        noiseSuppressor = null
        automaticGainControl = null
    }

    private suspend fun readLoop(initialRecord: AudioRecord, buffer: ByteArray, recordBufferSize: Int) {
        var record = initialRecord
        var recoveryAttempts = 0
        while (scope.isActive && !stopRequested) {
            if (paused) {
                delay(50)
                continue
            }
            val count = record.read(buffer, 0, buffer.size)
            if (count > 0) {
                recoveryAttempts = 0
                onPcmRead?.invoke(buffer, 0, count)
                writerMutex.withLock {
                    fullWriter?.write(buffer, 0, count)
                    if (chunkWriter == null) chunkWriter = DitingWavFileWriter(segmentFile(sequence))
                    chunkWriter?.write(buffer, 0, count)
                    totalBytes += count
                    chunkBytes += count
                    if (chunkBytes >= TARGET_CHUNK_BYTES) finalizeChunk()
                }
            } else if (count < 0) {
                // pause() stops AudioRecord so that the microphone is actually released.
                // Some devices also return ERROR_DEAD_OBJECT when an audio route changes;
                // rebuild the recorder a few times before declaring the classroom failed.
                if (paused && !stopRequested) {
                    delay(50)
                    continue
                }
                if (!stopRequested && recoveryAttempts < MAX_REOPEN_ATTEMPTS) {
                    recoveryAttempts += 1
                    delay(REOPEN_DELAY_MILLIS)
                    reopenAudioRecord(record, recordBufferSize)?.let { replacement ->
                        record = replacement
                        continue
                    }
                }
                if (stopRequested) break
                failure = IllegalStateException("麦克风读取失败：$count")
                stopRequested = true
            }
        }
    }

    private suspend fun finalizeChunk() {
        val writer = chunkWriter ?: return
        writer.close()
        val start = bytesToMillis(totalBytes - chunkBytes)
        val end = bytesToMillis(totalBytes)
        chunkChannel.trySend(DitingAudioChunk(sequence, segmentFile(sequence), start, end))
        sequence += 1
        chunkWriter = null
        chunkBytes = 0
    }

    private fun bytesToMillis(bytes: Long): Long = bytes * 1_000L / BYTES_PER_SECOND

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SECOND = SAMPLE_RATE * 2
        private const val TARGET_CHUNK_BYTES = BYTES_PER_SECOND * 8
        private const val MAX_REOPEN_ATTEMPTS = 3
        private const val REOPEN_DELAY_MILLIS = 150L
    }
}

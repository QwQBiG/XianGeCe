package win.iqwqi.xiangece.feature.diting.transcription

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import okio.ByteString.Companion.toByteString

/** One bounded, already-recorded file per WebSocket task, not a continuous microphone session. */
internal class DitingFunAsrAdapter(
    private val sockets: WebSocket.Factory,
    private val startupTimeoutMillis: Long = 12_000,
    private val finalTimeoutMillis: Long = 15_000,
    private val frameDelayMillis: Long = 100,
) {
    suspend fun transcribe(
        file: File,
        endpoint: String,
        key: String,
        model: String,
        language: String,
        glossary: String,
    ): DitingCloudTranscript = withContext(Dispatchers.IO) {
        val wav = DitingCloudWav.inspect(file)
        val taskId = UUID.randomUUID().toString()
        val events = Channel<String>(64)
        val finishSent = AtomicBoolean(false)
        val runTask = DitingFunAsrProtocol.runTask(taskId, model, wav.sampleRate, language, glossary)
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $key").build()
        val socket = sockets.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!webSocket.send(runTask)) events.close(IOException("Fun-ASR 启动指令发送失败"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length > 262_144 || events.trySend(text).isFailure) {
                    events.close(IOException("Fun-ASR 事件过多或数据过大，请重试该片段"))
                    webSocket.cancel()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                events.close(response?.let { DitingCloudErrors.http(it.code) }
                    ?: IOException("Fun-ASR 连接失败或中断，请检查网络及 WebSocket 地址"))
                response?.close()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                events.close(IOException("Fun-ASR 连接提前关闭（$code），片段尚未确认完成"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                events.close(IOException("Fun-ASR 连接已关闭，片段尚未确认完成"))
            }
        })
        try {
            val started = withTimeoutOrNull(startupTimeoutMillis) {
                val event = DitingFunAsrProtocol.event(events.receive(), taskId)
                if (DitingFunAsrProtocol.eventName(event) != "task-started") {
                    throw IOException("Fun-ASR 未按协议确认任务启动")
                }
                true
            } ?: false
            if (!started) throw IOException("Fun-ASR 启动超时，请检查网络、接口和模型权限")
            withTimeoutOrNull(wav.durationMillis + finalTimeoutMillis) {
                exchange(socket, events, file, wav, taskId, finishSent)
            } ?: throw IOException("Fun-ASR 转写超时，尚未收到完整最终结果")
        } finally {
            // Includes caller cancellation/timeout and protocol failure; no detached uploader survives.
            socket.cancel()
            events.cancel()
        }
    }

    private suspend fun exchange(
        socket: WebSocket,
        events: Channel<String>,
        file: File,
        wav: DitingCloudWav,
        taskId: String,
        finishSent: AtomicBoolean,
    ): DitingCloudTranscript = coroutineScope {
        val uploader = launch {
            RandomAccessFile(file, "r").use { input ->
                input.seek(wav.offset)
                var remaining = wav.bytes
                val buffer = ByteArray(wav.sampleRate / 10 * 2) // 100 ms of mono PCM16.
                while (remaining > 0) {
                    ensureActive()
                    // OkHttp send() is buffered. Bound queued audio when the connection is slow.
                    while (socket.queueSize() > 262_144L) delay(20)
                    val count = minOf(remaining, buffer.size.toLong()).toInt()
                    input.readFully(buffer, 0, count)
                    if (!socket.send(buffer.toByteString(0, count))) throw IOException("Fun-ASR 音频发送失败")
                    remaining -= count
                    if (remaining > 0) delay(frameDelayMillis)
                }
            }
            finishSent.set(true)
            if (!socket.send(DitingFunAsrProtocol.finishTask(taskId))) {
                throw IOException("Fun-ASR 结束指令发送失败")
            }
        }
        val results = DitingFunAsrResults()
        var finished = false
        while (!finished) {
            val event = DitingFunAsrProtocol.event(events.receive(), taskId)
            when (DitingFunAsrProtocol.eventName(event)) {
                "result-generated" -> results.accept(event)
                "task-finished" -> {
                    if (!finishSent.get()) throw IOException("Fun-ASR 在音频发送完成前结束了任务")
                    finished = true
                }
                else -> throw IOException("Fun-ASR 返回了未预期的任务事件")
            }
        }
        uploader.join()
        val transcript = results.transcript()
        socket.close(1000, null)
        transcript
    }
}

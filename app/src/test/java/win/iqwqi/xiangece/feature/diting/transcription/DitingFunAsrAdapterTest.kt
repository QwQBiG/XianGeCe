package win.iqwqi.xiangece.feature.diting.transcription

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test

class DitingFunAsrAdapterTest {
    @Test fun waitsForStartSendsOnlyPcmAndWaitsForTaskFinished() = runBlocking {
        withWav { file, pcm ->
            val socket = FakeFunSocket()
            val result = adapter(socket).transcribe(file, endpoint, "test-key", "fun-asr-realtime", "zh", "词表")
            assertEquals("完成文字。", result.text)
            assertEquals(listOf("run-task", "finish-task"), socket.actions)
            assertEquals("Bearer test-key", socket.request().header("Authorization"))
            assertArrayEquals(pcm, socket.audio.toByteArray())
            assertTrue(socket.cancelled)
            assertEquals("", result.language)
        }
    }

    @Test fun startupTimeoutDoesNotSendAudioAndCancelsSocket() = runBlocking {
        withWav { file, _ ->
            val socket = FakeFunSocket("wait-start")
            val failure = runCatching { adapter(socket, 20).transcribe(file, endpoint, "key", "fun-asr-realtime", "auto", "") }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(failure!!.message!!.contains("启动超时"))
            assertEquals(0, socket.audio.size())
            assertTrue(socket.cancelled)
        }
    }

    @Test fun callerCancellationStopsSocketAndKeepsSourceFile() = runBlocking {
        withWav { file, _ ->
            val original = file.readBytes()
            val socket = FakeFunSocket("wait-finish")
            val job = launch { adapter(socket).transcribe(file, endpoint, "key", "fun-asr-realtime", "auto", "") }
            withTimeout(2_000) { socket.finish.await() }
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertTrue(socket.cancelled)
            assertArrayEquals(original, file.readBytes())
        }
    }

    @Test fun unfinishedOrFailedOrClosedTaskNeverReturnsPartialSuccess() = runBlocking {
        for (mode in listOf("partial", "fail", "early-close", "send-fail", "wait-finish")) {
            withWav { file, _ ->
                val socket = FakeFunSocket(mode)
                val failure = runCatching { adapter(socket).transcribe(file, endpoint, "key", "fun-asr-realtime", "auto", "") }.exceptionOrNull()
                assertTrue("mode=$mode", failure is IOException)
                assertTrue(socket.cancelled)
                assertFalse(failure.toString().contains("private-server-text"))
            }
        }
    }

    @Test fun invalidOrOversizedWavIsRejectedBeforeConnecting() = runBlocking {
        withWav { file, _ ->
            val bytes = file.readBytes()
            bytes[22] = 2 // stereo is not accepted by this scoped adapter.
            file.writeBytes(bytes)
            val socket = FakeFunSocket()
            assertTrue(runCatching { adapter(socket).transcribe(file, endpoint, "key", "fun-asr-realtime", "auto", "") }.exceptionOrNull() is IOException)
            assertTrue(socket.actions.isEmpty())
        }
        withWav(sampleCount = 16_000 * 60 + 1) { file, _ ->
            assertTrue(runCatching { DitingCloudWav.inspect(file) }.exceptionOrNull() is IOException)
        }
    }

    private fun adapter(socket: FakeFunSocket, startTimeout: Long = 1_000) =
        DitingFunAsrAdapter(socket, startupTimeoutMillis = startTimeout, finalTimeoutMillis = 20, frameDelayMillis = 0)

    private suspend fun withWav(sampleCount: Int = 3_200, block: suspend (File, ByteArray) -> Unit) {
        val pcm = ByteArray(sampleCount * 2) { (it % 127).toByte() }
        // Include a metadata chunk: adapter must locate data rather than blindly skip 44 bytes.
        val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(46 + pcm.size); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(16_000); putInt(32_000); putShort(2); putShort(16)
            put("JUNK".toByteArray()); putInt(2); putShort(0)
            put("data".toByteArray()); putInt(pcm.size)
        }.array()
        val file = File.createTempFile("cloud-asr-test-", ".wav")
        try { file.writeBytes(header + pcm); block(file, pcm) } finally { file.delete() }
    }

    private val endpoint = "wss://example.invalid/asr"
}

private class FakeFunSocket(private val mode: String = "normal") : WebSocket, WebSocket.Factory {
    val actions = mutableListOf<String>()
    val audio = ByteArrayOutputStream()
    val finish = CompletableDeferred<Unit>()
    var cancelled = false
    private lateinit var listener: WebSocketListener
    private lateinit var requestValue: Request
    private var taskId = ""
    private var started = false

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        requestValue = request
        this.listener = listener
        listener.onOpen(this, Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(101).message("Switching Protocols").body("".toResponseBody()).build())
        return this
    }

    override fun request(): Request = requestValue
    override fun queueSize(): Long = 0
    override fun cancel() { cancelled = true }
    override fun close(code: Int, reason: String?): Boolean = true

    override fun send(bytes: ByteString): Boolean {
        check(started)
        if (mode == "send-fail") return false
        audio.write(bytes.toByteArray())
        return true
    }

    override fun send(text: String): Boolean {
        val header = Json.parseToJsonElement(text).jsonObject.getValue("header").jsonObject
        taskId = header.getValue("task_id").jsonPrimitive.content
        val action = header.getValue("action").jsonPrimitive.content
        actions.add(action)
        when (action) {
            "run-task" -> if (mode != "wait-start") {
                started = true
                emit("task-started")
                if (mode == "early-close") listener.onClosing(this, 1000, "private-server-text")
            }
            "finish-task" -> {
                finish.complete(Unit)
                when (mode) {
                    "fail" -> listener.onMessage(this, """{"header":{"task_id":"$taskId","event":"task-failed","error_code":"CLIENT_ERROR","error_message":"private-server-text"}}""")
                    "wait-finish" -> Unit
                    else -> {
                        emit("result-generated", "未完成", false)
                        if (mode != "partial") {
                            emit("result-generated", "完成文字。", true)
                            emit("result-generated", "完成文字。", true)
                        }
                        emit("task-finished")
                    }
                }
            }
        }
        return true
    }

    private fun emit(event: String, text: String = "", finalized: Boolean = false) {
        listener.onMessage(this, buildJsonObject {
            putJsonObject("header") { put("task_id", taskId); put("event", event) }
            putJsonObject("payload") { putJsonObject("output") { putJsonObject("sentence") {
                put("sentence_id", 1); put("begin_time", 0); put("text", text); put("sentence_end", finalized)
            } } }
        }.toString())
    }
}

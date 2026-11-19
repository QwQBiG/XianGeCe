package win.iqwqi.xiangece.feature.diting.transcription

import java.io.File
import java.io.IOException
import kotlin.reflect.KClass
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test

class DitingCompatibleAsrAdapterTest {
    @Test fun preservesMultipartModelLanguageGlossaryAndCustomAuth() = runBlocking {
        val file = File.createTempFile("compatible-asr-", ".wav")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            val calls = FakeCompatibleCall(200, """{"text":"transcript","language":"zh"}""")
            val result = DitingCompatibleAsrAdapter(calls).transcribe(file, "https://example.invalid/audio/transcriptions", "secret", "X-Api-Key: {key}", "custom-model", "zh", "课堂术语")
            assertEquals("transcript", result.text)
            assertEquals("zh", result.language)
            assertEquals("secret", calls.request().header("X-Api-Key"))
            val content = Buffer().also { calls.request().body!!.writeTo(it) }.readUtf8()
            assertTrue(content.contains("custom-model"))
            assertTrue(content.contains("课堂术语"))
            assertTrue(content.contains("name=\"language\""))
            assertTrue(content.contains("name=\"file\""))
        } finally { file.delete() }
    }

    @Test fun errorResponseBodyAndNetworkExceptionsNeverLeak() = runBlocking {
        val file = File.createTempFile("compatible-asr-", ".wav")
        try {
            for (status in listOf(401, 429, 500)) {
                val failure = runCatching {
                    DitingCompatibleAsrAdapter(FakeCompatibleCall(status, "secret transcript")).transcribe(file, "https://example.invalid/audio/transcriptions", "secret", "", "whisper-1", "auto", "")
                }.exceptionOrNull()
                assertTrue(failure is IOException)
                assertFalse(failure.toString().contains("secret"))
                assertTrue(failure!!.message!!.contains(status.toString()))
            }
        } finally { file.delete() }
    }

    @Test fun cancellationCancelsHttpUpload() = runBlocking {
        val file = File.createTempFile("compatible-asr-", ".wav")
        try {
            val calls = FakeCompatibleCall(200, "", wait = true)
            val job = launch {
                DitingCompatibleAsrAdapter(calls).transcribe(file, "https://example.invalid/audio/transcriptions", "key", "", "whisper-1", "auto", "")
            }
            withTimeout(2_000) { calls.queued.await() }
            job.cancelAndJoin()
            assertTrue(calls.cancelled)
        } finally { file.delete() }
    }
}

private class FakeCompatibleCall(private val status: Int, private val body: String, private val wait: Boolean = false) : Call, Call.Factory {
    lateinit var requestValue: Request
    var cancelled = false
    val queued = CompletableDeferred<Unit>()
    override fun newCall(request: Request): Call { requestValue = request; return this }
    override fun request(): Request = requestValue
    override fun execute(): Response = error("blocking calls must not be used")
    override fun enqueue(responseCallback: Callback) {
        queued.complete(Unit)
        if (!wait) responseCallback.onResponse(this, Response.Builder().request(requestValue).protocol(Protocol.HTTP_1_1)
            .code(status).message("test").body(body.toResponseBody()).build())
    }
    override fun cancel() { cancelled = true }
    override fun isExecuted(): Boolean = queued.isCompleted
    override fun isCanceled(): Boolean = cancelled
    override fun timeout(): Timeout = Timeout.NONE
    override fun clone(): Call = FakeCompatibleCall(status, body, wait)
    override fun addEventListener(eventListener: EventListener) = Unit
    override fun <T : Any> tag(type: KClass<T>): T? = null
    override fun <T> tag(type: Class<out T>): T? = null
    override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
}

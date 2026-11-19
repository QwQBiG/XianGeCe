package win.iqwqi.xiangece.core.ai

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CancellableRequestTest {
    @Test fun ownerCancellationStopsTheRequest() = runTest {
        val call = FakeCall()
        val job = async { cancellableResult { call.awaitResponseBody() } }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(call.isCanceled())
        assertTrue(job.isCancelled)
    }

    @Test fun successReturnsBodyAndFailureDoesNotExposeServerContent() = runTest {
        val success = FakeCall()
        val job = async { success.awaitResponseBody() }
        runCurrent()
        success.answer(200, "original text")
        assertEquals("original text", job.await())
        val failure = FakeCall()
        val failed = async { cancellableResult { failure.awaitResponseBody() } }
        runCurrent()
        failure.answer(401, "server echoed a secret")
        val message = failed.await().exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("401"))
        assertFalse(message.contains("secret"))
    }

    private class FakeCall : Call by OkHttpClient().newCall(Request.Builder().url("https://example.invalid/").build()) {
        var callback: Callback? = null
        private var cancelled = false
        override fun request() = Request.Builder().url("https://example.invalid/").build()
        override fun execute(): Response = error("Only asynchronous requests are expected")
        override fun enqueue(responseCallback: Callback) { callback = responseCallback }
        override fun cancel() { cancelled = true }
        override fun isExecuted() = callback != null
        override fun isCanceled() = cancelled
        override fun timeout() = Timeout()
        override fun clone(): Call = FakeCall()
        fun answer(code: Int, body: String) {
            callback!!.onResponse(this, Response.Builder().request(request())
                .protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody()).build())
        }
    }
}

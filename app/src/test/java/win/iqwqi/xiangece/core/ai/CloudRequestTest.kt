package win.iqwqi.xiangece.core.ai

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class CloudRequestTest {
    private suspend fun request(text: String, code: Int = 200, knownLength: Boolean = true): String {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).code(code).message("Synthetic")
                .protocol(Protocol.HTTP_1_1).body(object : ResponseBody() {
                    override fun contentType(): MediaType? = null
                    override fun contentLength() = if (knownLength) text.toByteArray(Charsets.UTF_8).size.toLong() else -1L
                    override fun source() = Buffer().writeUtf8(text)
                }).build()
        }.build()
        return try {
            client.newCall(Request.Builder().url("https://service-test.invalid/v1").build()).awaitResponseBody()
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun normalUtf8AndUnknownLengthResponsesRemainReadable() = runBlocking {
        val text = "{\"text\":\"课程、通知与课堂笔记\"}"
        assertEquals(text, request(text))
        assertEquals(text, request(text, knownLength = false))
        assertEquals("", request(""))
    }

    @Test fun failuresGiveActionableReasonsWithoutEchoingResponseBody() = runBlocking {
        for (code in listOf(400, 401, 402, 403, 404, 429, 503)) {
            val failure = runCatching { request("SECRET-KEY-AND-PRIVATE-MATERIAL", code) }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(failure!!.message.orEmpty().contains(code.toString()))
            assertFalse(failure.message.orEmpty().contains("SECRET"))
        }
    }

    @Test fun oversizedBodiesAreRejectedWithOrWithoutContentLength() = runBlocking {
        val text = "x".repeat(4 * 1024 * 1024 + 1)
        for (known in listOf(true, false)) {
            val failure = runCatching { request(text, knownLength = known) }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(failure!!.message.orEmpty().contains("内容过长"))
        }
    }
}

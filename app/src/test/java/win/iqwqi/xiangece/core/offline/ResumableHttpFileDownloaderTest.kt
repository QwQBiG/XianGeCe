package win.iqwqi.xiangece.core.offline

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumableHttpFileDownloaderTest {
    @Test
    fun badFullSourceIsDiscardedBeforeNextSource() = runBlocking {
        val payload = testPayload()
        val server = testServer(payload, wrongRange = false)
        val part = File.createTempFile("diting-download-", ".part")
        try {
            ResumableHttpFileDownloader.download(
                client = testClient(),
                urls = listOf(server.url("/bad"), server.url("/good")),
                partFile = part,
                expectedBytes = payload.size.toLong(),
                expectedSha256 = sha256(payload),
                label = "test",
                onProgress = {},
                onCallChanged = {},
            )
            assertArrayEquals(payload, part.readBytes())
        } finally {
            part.delete()
            server.close()
        }
    }

    @Test
    fun wrongContentRangeFallsBackToAnotherSource() = runBlocking {
        val payload = testPayload()
        val server = testServer(payload, wrongRange = true)
        val part = File.createTempFile("diting-range-", ".part")
        try {
            ResumableHttpFileDownloader.download(
                client = testClient(),
                urls = listOf(server.url("/bad-range"), server.url("/good")),
                partFile = part,
                expectedBytes = payload.size.toLong(),
                expectedSha256 = sha256(payload),
                label = "test",
                onProgress = {},
                onCallChanged = {},
            )
            assertArrayEquals(payload, part.readBytes())
        } finally {
            part.delete()
            server.close()
        }
    }
    @Test
    fun existingPartResumesFromItsExactOffset() = runBlocking {
        val payload = testPayload()
        val server = testServer(payload, wrongRange = false)
        val part = File.createTempFile("diting-resume-", ".part")
        val prefixLength = 1_234_567
        try {
            part.outputStream().use { it.write(payload, 0, prefixLength) }
            ResumableHttpFileDownloader.download(
                client = testClient(),
                urls = listOf(server.url("/good")),
                partFile = part,
                expectedBytes = payload.size.toLong(),
                expectedSha256 = sha256(payload),
                label = "test",
                onProgress = {},
                onCallChanged = {},
            )
            assertEquals(payload.size.toLong(), part.length())
            assertArrayEquals(payload, part.readBytes())
        } finally {
            part.delete()
            server.close()
        }
    }

    private fun testPayload(): ByteArray = ByteArray(8 * 1024 * 1024 + 12_345) { index ->
        ((index * 31 + 17) and 0xff).toByte()
    }

    private fun testClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun testServer(payload: ByteArray, wrongRange: Boolean): TestServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            respond(exchange, payload, wrongRange)
        }
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.start()
        return TestServer(server, executor)
    }

    private fun respond(exchange: HttpExchange, payload: ByteArray, wrongRange: Boolean) {
        try {
            val range = exchange.requestHeaders.getFirst("Range")
            val requested = parseRange(range, payload.size)
            val path = exchange.requestURI.path
            val isBadFullSource = path == "/bad"
            val isWrongRangeSource = wrongRange && path == "/bad-range"
            if (isBadFullSource) {
                val bad = ByteArray(payload.size) { 0x5a }
                exchange.sendResponseHeaders(200, bad.size.toLong())
                exchange.responseBody.use { it.write(bad) }
                return
            }
            val start = requested.first
            val end = requested.second
            val body = payload.copyOfRange(start, end + 1)
            val reportedStart = if (isWrongRangeSource) start + 1 else start
            exchange.responseHeaders.add("Content-Range", "bytes $reportedStart-$end/${payload.size}")
            exchange.sendResponseHeaders(206, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        } finally {
            exchange.close()
        }
    }

    private fun parseRange(value: String?, size: Int): Pair<Int, Int> {
        if (value.isNullOrBlank()) return 0 to size - 1
        val match = Regex("bytes=(\\d+)-(\\d+)").matchEntire(value) ?: error(value)
        return match.groupValues[1].toInt() to match.groupValues[2].toInt().coerceAtMost(size - 1)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private class TestServer(
        private val server: HttpServer,
        private val executor: java.util.concurrent.ExecutorService,
    ) {
        fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"
        fun close() {
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
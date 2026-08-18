package win.iqwqi.xiangece.core.offline

import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads large optional resources in small HTTP ranges. A partial file is always a
 * valid prefix, so a failed request can resume from the exact byte already written.
 */
object ResumableHttpFileDownloader {
    private const val CHUNK_BYTES = 8L * 1024L * 1024L
    private const val RETRIES_PER_SOURCE = 2

    suspend fun download(
        client: OkHttpClient,
        urls: List<String>,
        partFile: File,
        expectedBytes: Long,
        expectedSha256: String,
        label: String,
        onProgress: (Long) -> Unit,
        onCallChanged: (Call?) -> Unit,
    ) {
        require(urls.isNotEmpty()) { "没有可用的下载源" }
        val uniqueUrls = urls.filter(String::isNotBlank).distinct()
        require(uniqueUrls.isNotEmpty()) { "没有可用的下载源" }
        // 先用极小的 Range 请求并行探测源，避免在国内网络下反复等待不可达站点超时。
        // 探测失败的源仍保留在末尾，防止某些站点只拒绝探测请求但允许正式下载。
        val orderedUrls = orderSources(client, uniqueUrls)
        // 每一轮先轮换所有源，再对整组源进行下一轮重试，避免同一坏源连续阻塞。
        val attempts = (0 until RETRIES_PER_SOURCE).flatMap { orderedUrls }
        val errors = mutableListOf<String>()

        for (url in attempts) {
            try {
                downloadFromSource(client, url, partFile, expectedBytes, onProgress, onCallChanged)
                check(partFile.length() == expectedBytes) { "$label 文件大小不匹配" }
                check(sha256(partFile) == expectedSha256) { "$label 文件校验失败" }
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                // 某些代理可能返回完整但错误的内容；满大小的坏分片不能继续被后续源复用。
                if (partFile.isFile && partFile.length() >= expectedBytes) partFile.delete()
                val host = runCatching { URI(url).host }.getOrNull() ?: url
                errors += "$host：${error.message ?: "连接失败"}"
            }
        }
        val distinctErrors = errors.distinct()
        val diagnostic = distinctErrors.take(MAX_DIAGNOSTICS).joinToString("；")
            .let { if (distinctErrors.size > MAX_DIAGNOSTICS) "$it；其余 ${distinctErrors.size - MAX_DIAGNOSTICS} 次失败已省略" else it }
        throw IllegalStateException("$label 下载失败（已自动探测并尝试多个源、支持断点续传）：$diagnostic")
    }

    private suspend fun orderSources(client: OkHttpClient, urls: List<String>): List<String> = coroutineScope {
        val probeClient = client.newBuilder()
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(PROBE_TIMEOUT_SECONDS + 1L, TimeUnit.SECONDS)
            .build()
        val probes = urls.mapIndexed { index, url ->
            async(Dispatchers.IO) {
                val reachable = runCatching {
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept-Encoding", "identity")
                        .header("Range", "bytes=0-0")
                        .build()
                    probeClient.newCall(request).execute().use { response ->
                        response.isSuccessful && response.code in setOf(200, 206)
                    }
                }.getOrDefault(false)
                index to reachable
            }
        }.awaitAll()
        val reachable = probes.filter { it.second }.sortedBy { it.first }.map { urls[it.first] }
        reachable + urls.filterNot(reachable::contains)
    }

    private suspend fun downloadFromSource(
        client: OkHttpClient,
        url: String,
        partFile: File,
        expectedBytes: Long,
        onProgress: (Long) -> Unit,
        onCallChanged: (Call?) -> Unit,
    ) {
        var offset = partFile.takeIf(File::isFile)?.length()?.coerceIn(0L, expectedBytes) ?: 0L
        while (offset < expectedBytes) {
            currentCoroutineContext().ensureActive()
            val requestedEnd = minOf(offset + CHUNK_BYTES, expectedBytes) - 1L
            val request = Request.Builder()
                .url(url)
                .header("Accept-Encoding", "identity")
                .header("Range", "bytes=$offset-$requestedEnd")
                .build()
            val call = client.newCall(request)
            onCallChanged(call)
            try {
                call.execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body
                    val isRangeResponse = response.code == 206
                    if (isRangeResponse) {
                        val contentRange = response.header("Content-Range")?.trim()
                            ?: error("分片响应缺少 Content-Range")
                        val match = CONTENT_RANGE_PATTERN.matchEntire(contentRange)
                            ?: error("分片响应范围无效：$contentRange")
                        val rangeStart = match.groupValues[1].toLong()
                        val rangeEnd = match.groupValues[2].toLong()
                        val rangeTotal = match.groupValues[3]
                        check(rangeStart == offset && rangeEnd == requestedEnd) {
                            "分片响应范围不匹配：$contentRange，应为 bytes $offset-$requestedEnd"
                        }
                        check(rangeTotal == "*" || rangeTotal.toLongOrNull() == expectedBytes) {
                            "分片响应文件大小不匹配：$contentRange"
                        }
                        val expectedChunkBytes = requestedEnd - offset + 1L
                        check(body.contentLength() < 0L || body.contentLength() == expectedChunkBytes) {
                            "分片响应长度不匹配"
                        }
                    }
                    if (!isRangeResponse && offset > 0L) {
                        offset = 0L
                        RandomAccessFile(partFile, "rw").use { it.setLength(0L) }
                    }
                    val bytesToRead = if (isRangeResponse) {
                        requestedEnd - offset + 1L
                    } else {
                        expectedBytes
                    }
                    RandomAccessFile(partFile, "rw").use { output ->
                        output.seek(offset)
                        body.byteStream().use { input ->
                            val buffer = ByteArray(1024 * 1024)
                            var remaining = bytesToRead
                            while (remaining > 0L) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                check(count >= 0) { "连接提前结束" }
                                if (count == 0) continue
                                output.write(buffer, 0, count)
                                remaining -= count.toLong()
                                offset += count.toLong()
                                onProgress(offset)
                            }
                        }
                        output.fd.sync()
                    }
                }
            } finally {
                onCallChanged(null)
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            var count: Int
            while (input.read(buffer).also { count = it } >= 0) {
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val PROBE_TIMEOUT_SECONDS = 4L
    private const val MAX_DIAGNOSTICS = 6
    private val CONTENT_RANGE_PATTERN = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)")
}

package win.iqwqi.xiangece.core.ai

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Cancels the network operation with its owner; never exposes server bodies in error messages. */
internal suspend fun Call.awaitResponseBody(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            val message = if (e is java.io.InterruptedIOException) "服务响应超时，内容已保留，可以稍后重试"
                else "连接未完成，请检查网络或接口配置"
            if (continuation.isActive) continuation.resumeWithException(IOException(message))
        }

        override fun onResponse(call: Call, response: Response) {
            val result = runCatching {
                response.use {
                    if (!it.isSuccessful) throw IOException(serviceErrorMessage(it.code))
                    val limit = 4L * 1024 * 1024
                    if (it.body.contentLength() > limit) throw IOException("服务返回内容过长，请减少本次材料")
                    val buffer = okio.Buffer()
                    val source = it.body.source()
                    var received = 0L
                    while (received <= limit) {
                        val count = source.read(buffer, minOf(8192L, limit + 1 - received))
                        if (count == -1L) break
                        received += count
                    }
                    if (received > limit) throw IOException("服务返回内容过长，请减少本次材料")
                    buffer.readUtf8()
                }
            }
            if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
        }
    })
}

internal fun serviceErrorMessage(code: Int): String = when (code) {
    400, 422 -> "服务不接受当前请求（$code），请检查模型是否支持这项功能"
    401 -> "密钥验证失败（401），请检查密钥及服务地域"
    402 -> "服务额度不足（402），请检查账户余额"
    403 -> "服务拒绝访问（403），请检查模型权限及服务地域"
    404 -> "未找到接口或模型（404），请检查服务地址及模型名称"
    429 -> "请求受限（429），请检查额度或稍后重试"
    in 500..599 -> "服务暂时不可用（$code），内容已保留，请稍后重试"
    else -> "请求未完成（HTTP $code），请检查接口配置"
}

internal suspend inline fun <T> cancellableResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

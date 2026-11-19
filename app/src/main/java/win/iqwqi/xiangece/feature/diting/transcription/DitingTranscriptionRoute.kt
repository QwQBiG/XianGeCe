package win.iqwqi.xiangece.feature.diting.transcription

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class DitingRoutedTranscript<T>(val value: T, val engine: String, val cloudFailed: Boolean)

/** Local availability and inference are evaluated lazily, only after cloud failure or absence. */
internal suspend fun <T> transcribeCloudFirst(
    isSessionValid: suspend () -> Boolean,
    cloudConfigured: suspend () -> Boolean,
    cloud: suspend () -> Result<T>,
    localConfigured: suspend () -> Boolean,
    local: suspend () -> Result<T>,
): Result<DitingRoutedTranscript<T>> {
    suspend fun checkSession() {
        currentCoroutineContext().ensureActive()
        if (!isSessionValid()) throw CancellationException("Transcription session no longer accepts results")
    }
    suspend fun attempt(block: suspend () -> Result<T>): Result<T> {
        checkSession()
        val result = try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
        checkSession()
        (result.exceptionOrNull() as? CancellationException)?.let { throw it }
        return result
    }
    checkSession()
    var cloudFailure: Throwable? = null
    if (cloudConfigured()) {
        val result = attempt(cloud)
        if (result.isSuccess) return Result.success(DitingRoutedTranscript(result.getOrThrow(), "cloud", false))
        cloudFailure = result.exceptionOrNull()
    }
    checkSession()
    if (localConfigured()) {
        return attempt(local).map { DitingRoutedTranscript(it, "offline_onnx", cloudFailure != null) }
    }
    return Result.failure(cloudFailure ?: IllegalStateException("暂时无法识别文字，录音已保留"))
}

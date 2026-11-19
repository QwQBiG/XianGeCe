package win.iqwqi.xiangece.feature.diting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import win.iqwqi.xiangece.feature.diting.transcription.transcribeCloudFirst

class DitingTranscriptionRouteTest {
    @Test fun configuredCloudDoesNotEvenCheckLocalModel() = runTest {
        val result = transcribeCloudFirst(
            isSessionValid = { true }, cloudConfigured = { true },
            cloud = { Result.success("cloud") },
            localConfigured = { error("Local model must remain untouched") },
            local = { error("Local model must remain untouched") },
        ).getOrThrow()
        assertEquals("cloud", result.engine)
        assertFalse(result.cloudFailed)
    }

    @Test fun failedCloudFallsBackToLocal() = runTest {
        val result = transcribeCloudFirst(
            isSessionValid = { true }, cloudConfigured = { true },
            cloud = { Result.failure<String>(IllegalStateException("offline network")) },
            localConfigured = { true }, local = { Result.success("local") },
        ).getOrThrow()
        assertEquals("local", result.value)
        assertEquals("offline_onnx", result.engine)
        assertTrue(result.cloudFailed)
    }

    @Test fun noCloudConfigurationStaysLocal() = runTest {
        val result = transcribeCloudFirst(
            isSessionValid = { true }, cloudConfigured = { false },
            cloud = { error("Unconfigured cloud must not be called") },
            localConfigured = { true }, local = { Result.success("local") },
        ).getOrThrow()
        assertFalse(result.cloudFailed)
    }

    @Test fun expiredSessionDoesNotStartLocalFallback() = runTest {
        var valid = true
        try {
            transcribeCloudFirst<String>(
                isSessionValid = { valid }, cloudConfigured = { true },
                cloud = { valid = false; Result.failure(IllegalStateException("timeout")) },
                localConfigured = { error("Expired session allocated local model") },
                local = { error("Expired session started local inference") },
            )
            fail("Session expiry must cancel the route")
        } catch (_: CancellationException) { }
    }

    @Test fun wrappedCancellationDoesNotStartFallback() = runTest {
        try {
            transcribeCloudFirst<String>(
                isSessionValid = { true }, cloudConfigured = { true },
                cloud = { Result.failure(CancellationException("stopped")) },
                localConfigured = { error("Cancellation triggered fallback") },
                local = { error("Cancellation triggered inference") },
            )
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}

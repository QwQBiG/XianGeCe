package win.iqwqi.xiangece.core.update

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicSourcePolicyTest {
    @Test
    fun newEditionDoesNotRequestLegacyUpdateFeed() = runBlocking {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor {
            requests.incrementAndGet()
            error("No update network call is allowed for this edition")
        }.build()
        val repository = AppUpdateRepository(ApplicationProvider.getApplicationContext(), client, Json)
        assertEquals(AppUpdateState.Idle, repository.check())
        assertEquals(0, requests.get())
    }

    @Test
    fun installedBuildContainsOfflinePolicyAndHistoricalBoundary() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val license = context.assets.open("policies/license.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val privacy = context.assets.open("policies/privacy.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertTrue(license.contains("Apache-2.0"))
        assertTrue(license.contains("QwQBiG") && license.contains("保留所有权利"))
        assertTrue(license.contains("历史版本"))
        assertTrue(privacy.contains("2026 年 9 月 13 日"))
        assertTrue(privacy.contains("不包含接口密钥或课堂原始音频"))
    }
}

package win.iqwqi.xiangece.core.offline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import win.iqwqi.xiangece.core.ocr.OfflineOcrPackCatalog
import win.iqwqi.xiangece.core.ocr.OfflineOcrPackManager
import win.iqwqi.xiangece.core.ocr.OfflineOcrService
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackManager
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflineTranscriber

/** Opt-in ABI/import smoke check, not a recognition-quality benchmark. No network calls. */
@RunWith(AndroidJUnit4::class)
class OfflinePackRuntimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val source = File(context.getExternalFilesDir(null), "r9-packs")
    private fun requireStagedFiles() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("stagedModelPacks") == "true")
        assertTrue("Stage ZIPs in app external files/r9-packs before running", source.isDirectory)
    }

    @Test fun ocrZipSelectionPersistsAndBothEnginesRun() = runBlocking(Dispatchers.Default) {
        requireStagedFiles()
        val manager = OfflineOcrPackManager(context, OkHttpClient())
        val service = OfflineOcrService(context, manager)
        val sample = File(context.cacheDir, "ocr-runtime-synthetic.png")
        val bitmap = Bitmap.createBitmap(640, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText("MODEL 123", 40f, 120f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; textSize = 56f
            })
        }
        sample.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            for ((id, zip) in listOf(OfflineOcrPackCatalog.PP_OCRV5_MOBILE_ID to "ocr-zh-en.zip",
                OfflineOcrPackCatalog.PP_OCRV5_SERVER_ID to "ocr-zh-en-server.zip")) {
                assertTrue(manager.selectPack(id))
                manager.importChinesePack(Uri.fromFile(File(source, zip)))
                val state = withTimeout(180_000) { manager.state.first { !it.downloading } }
                assertTrue(state.errorMessage, state.installed)
                assertTrue(manager.verifyInstalledFiles())
                assertTrue(service.recognize(sample).page.text.isNotBlank())
                service.release()
            }
            val restarted = OfflineOcrPackManager(context, OkHttpClient())
            assertEquals(2, restarted.state.value.installedPackIds.size)
            assertEquals(OfflineOcrPackCatalog.PP_OCRV5_SERVER_ID, restarted.state.value.selectedPackId)
        } finally { service.release(); sample.delete() }
    }

    @Test fun speechZipSelectionPersistsAndNativeModelsLoad() = runBlocking(Dispatchers.Default) {
        requireStagedFiles()
        val manager = DitingOfflinePackManager(context, OkHttpClient())
        val engine = DitingOfflineTranscriber(manager)
        try {
            for ((id, zip) in listOf("sherpa_sense_voice_small_int8" to "diting-sensevoice-int8.zip",
                "sherpa_funasr_nano_int8" to "diting-funasr-nano-int8.zip")) {
                assertTrue(manager.selectPack(id))
                manager.importChinesePack(Uri.fromFile(File(source, zip)))
                val state = withTimeout(240_000) { manager.state.first { !it.downloading } }
                assertTrue(state.errorMessage, state.installed)
                engine.prepare().getOrThrow()
                engine.release()
            }
            val restarted = DitingOfflinePackManager(context, OkHttpClient())
            assertTrue(restarted.state.value.installedPackIds.containsAll(listOf(
                "sherpa_sense_voice_small_int8", "sherpa_funasr_nano_int8")))
            assertEquals("sherpa_funasr_nano_int8", restarted.state.value.selectedPackId)
        } finally { engine.release() }
    }
}

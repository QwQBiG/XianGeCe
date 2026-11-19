package win.iqwqi.xiangece.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import win.iqwqi.xiangece.data.settings.*
import win.iqwqi.xiangece.ui.companion.CompanionHomeHeader
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

@RunWith(AndroidJUnit4::class)
class HomePanoramaMatrixTest {
    @get:Rule val compose = createComposeRule()
    private data class Case(val width: Int = 320, val scale: Float = 1f, val dark: Boolean = false,
        val outfit: CompanionOutfit = CompanionOutfit.CAMPUS, val display: CompanionDisplay = CompanionDisplay.STANDARD)

    @Test fun panoramaAndCompactFitWidthsFontsAndOutfits() {
        var case by mutableStateOf(Case())
        compose.setContent {
            val density = LocalDensity.current
            val appearance = CompanionAppearance(outfit = case.outfit, display = case.display, motionEnabled = false)
            CompositionLocalProvider(LocalDensity provides Density(density.density, case.scale)) {
                XiangeceTheme(darkTheme = case.dark, companion = appearance) {
                    Surface(Modifier.requiredWidth(case.width.dp).testTag("panorama-preview")) {
                        CompanionHomeHeader(appearance, "早上好", "9月10日 星期四", "2026 秋季学期",
                            "第 1 周 · 共 20 周", 0.05f, {}, {}, {}, {})
                    }
                }
            }
        }
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null), "panorama-review").apply { mkdirs() }
        for (width in listOf(320, 360, 412)) for (scale in listOf(1f, 1.3f, 1.5f))
            for (dark in listOf(false, true)) for (outfit in CompanionOutfit.entries)
                for (display in listOf(CompanionDisplay.STANDARD, CompanionDisplay.COMPACT)) {
                    compose.runOnIdle { case = Case(width, scale, dark, outfit, display) }
                    compose.onNodeWithText("换装").assertIsDisplayed()
                    compose.onNodeWithContentDescription("搜索").assertIsDisplayed()
                    compose.onNodeWithContentDescription("新建事项").assertIsDisplayed()
                    val copy = compose.onNodeWithTag("home-copy").getUnclippedBoundsInRoot()
                    val art = compose.onNodeWithTag("home-portrait").getUnclippedBoundsInRoot()
                    assertTrue("Character intersects copy in $case", copy.right <= art.left + 0.5.dp)
                    val bitmap = compose.onNodeWithTag("panorama-preview").captureToImage().asAndroidBitmap()
                    val name = "${display.name.lowercase()}-${outfit.name.lowercase()}-$width-$scale-${if(dark) "night" else "day"}.png"
                    File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
    }
}

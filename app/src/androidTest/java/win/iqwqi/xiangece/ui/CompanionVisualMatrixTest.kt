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
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import win.iqwqi.xiangece.data.settings.*
import win.iqwqi.xiangece.ui.companion.CompanionHero
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

/** Exports actual Compose renders; isolated from account, timetable and task data. */
@RunWith(AndroidJUnit4::class)
class CompanionVisualMatrixTest {
    @get:Rule val compose = createComposeRule()
    private data class Case(val width: Int = 320, val scale: Float = 1f,
                            val dark: Boolean = false, val outfit: CompanionOutfit = CompanionOutfit.CAMPUS)

    @Test fun exportHeaderAcrossWidthsFontsThemesAndOutfits() {
        var case by mutableStateOf(Case())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, case.scale)) {
                XiangeceTheme(darkTheme = case.dark,
                    companion = CompanionAppearance(outfit = case.outfit, motionEnabled = false)) {
                    Surface(Modifier.requiredWidth(case.width.dp).testTag("preview")) {
                        Box(Modifier.padding(16.dp)) {
                            CompanionHero("下午好", "2026 秋季学期", "第 3 周 · 本学期正在进行中", 0.15f, {})
                        }
                    }
                }
            }
        }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null), "visual-review").apply { mkdirs() }
        for (width in listOf(320, 360, 412)) for (scale in listOf(1f, 1.3f, 1.5f))
            for (dark in listOf(false, true)) for (outfit in CompanionOutfit.entries) {
                compose.runOnIdle { case = Case(width, scale, dark, outfit) }
                compose.onNodeWithText("换装").assertIsDisplayed()
                val copyBounds = compose.onNodeWithTag("companion-copy").getUnclippedBoundsInRoot()
                val artBounds = compose.onNodeWithTag("companion-portrait").getUnclippedBoundsInRoot()
                org.junit.Assert.assertTrue("Text must not overlap character at $case", copyBounds.right <= artBounds.left + 0.5.dp)
                val bitmap = compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
                val name = "hero-${outfit.name.lowercase()}-$width-$scale-${if (dark) "night" else "day"}.png"
                File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
    }
}

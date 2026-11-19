package win.iqwqi.xiangece.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import win.iqwqi.xiangece.data.settings.CompanionAppearance
import win.iqwqi.xiangece.ui.companion.WardrobeSheet
import win.iqwqi.xiangece.ui.screens.AppearanceSheet
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

class FullScreenSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appearanceStaysFullHeightWhileScrollingAndChangingTheme() {
        var dark by mutableStateOf(false)
        var dismissed = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                XiangeceTheme(darkTheme = dark) {
                    AppearanceSheet(AppUiState(), {}, {}, {}, {}, { dismissed++ })
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night }
            val header = compose.onNodeWithText("外观与角色")
            val top = header.getUnclippedBoundsInRoot().top
            val scroll = compose.onNodeWithTag("appearance-scroll")
            scroll.performTouchInput { swipeDown() }
            compose.onNodeWithText("墨黑").performScrollTo().assertIsDisplayed()
            repeat(2) { scroll.performTouchInput { swipeUp() } }
            header.assertIsDisplayed()
            assertEquals(top, header.getUnclippedBoundsInRoot().top)
            compose.onNodeWithText("角色衣橱 · 校园").performScrollTo().assertIsDisplayed()
            checkFullHeight()
            capture("appearance-$night")
        }
        compose.runOnIdle { assertEquals(0, dismissed) }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test fun wardrobeKeepsHeaderAndApplyVisibleAtBothScrollEdges() {
        var dark by mutableStateOf(false)
        var applied = 0
        var dismissed = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                XiangeceTheme(darkTheme = dark) {
                    WardrobeSheet(CompanionAppearance(motionEnabled = false), "indigo",
                        { applied++ }, { dismissed++ }, motionAllowed = false)
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night }
            val header = compose.onNodeWithText("角色衣橱")
            val footer = compose.onNodeWithText("应用装扮")
            val top = header.getUnclippedBoundsInRoot().top
            val bottom = footer.getUnclippedBoundsInRoot().bottom
            val scroll = compose.onNodeWithTag("wardrobe-scroll")
            scroll.performTouchInput { swipeDown() }
            compose.onNodeWithText("轻动效").performScrollTo().assertIsDisplayed()
            repeat(2) { scroll.performTouchInput { swipeUp() } }
            header.assertIsDisplayed()
            footer.assertIsDisplayed()
            assertEquals(top, header.getUnclippedBoundsInRoot().top)
            assertEquals(bottom, footer.getUnclippedBoundsInRoot().bottom)
            capture("wardrobe-bottom-$night")
            compose.onNodeWithText("同一个她，两种日常。").performScrollTo().assertIsDisplayed()
            checkFullHeight()
            capture("wardrobe-$night")
        }
        compose.onNodeWithText("取消").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0, applied); assertEquals(1, dismissed) }
    }

    private fun checkFullHeight() {
        val metrics = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics
        val bounds = compose.onNodeWithTag("full-screen-sheet").getUnclippedBoundsInRoot()
        assertTrue("Sheet top: $bounds", bounds.top.value <= 48f)
        assertTrue("Sheet bottom: $bounds", bounds.bottom.value >= metrics.heightPixels / metrics.density - 72f)
    }

    private fun capture(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = File(context.getExternalFilesDir(null), "full-screen-settings").apply { mkdirs() }
        val bitmap = compose.onNodeWithTag("full-screen-sheet").captureToImage().asAndroidBitmap()
        File(output, "$name.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}

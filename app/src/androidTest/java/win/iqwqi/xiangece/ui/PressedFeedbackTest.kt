package win.iqwqi.xiangece.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import win.iqwqi.xiangece.data.settings.CompanionAppearance
import win.iqwqi.xiangece.ui.companion.CompanionHomeHeader
import win.iqwqi.xiangece.ui.components.CoursePaletteChoices
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

class PressedFeedbackTest {
    @get:Rule val compose = createComposeRule()
    @Test fun holdingPortraitDoesNotPaintGreyRectangleButReleaseStillGreets() {
        var greeted = 0
        compose.setContent { XiangeceTheme {
            CompanionHomeHeader(CompanionAppearance(motionEnabled = false), "早上好", "9月10日",
                "秋季学期", "第 1 周", .2f, {}, { greeted++ }, {}, {})
        } }
        val portrait = compose.onNodeWithTag("home-portrait")
        val before = portrait.captureToImage().asAndroidBitmap()
        portrait.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        val pressed = portrait.captureToImage().asAndroidBitmap()
        assertTrue("Portrait must not draw a pressed overlay", before.sameAs(pressed))
        portrait.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, greeted) }
    }
    @Test fun colorFeedbackStaysInsideTheVisibleCircle() {
        compose.setContent { XiangeceTheme {
            Column { CoursePaletteChoices(emptyList(), 0L, {}, {}) }
        } }
        val node = compose.onNodeWithContentDescription("清新 颜色 1")
        val before = node.captureToImage().asAndroidBitmap()
        node.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        val pressed = node.captureToImage().asAndroidBitmap()
        val cx = before.width / 2f; val cy = before.height / 2f
        val radius = before.width / 3f + 2f
        var changed = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            val differs = before.getPixel(x, y) != pressed.getPixel(x, y)
            if (differs) changed++
            if ((x-cx)*(x-cx) + (y-cy)*(y-cy) > radius*radius)
                assertFalse("Feedback escaped circle at $x,$y", differs)
        }
        assertTrue("Circle should still show feedback", changed > 0)
        node.performTouchInput { up() }
    }
}

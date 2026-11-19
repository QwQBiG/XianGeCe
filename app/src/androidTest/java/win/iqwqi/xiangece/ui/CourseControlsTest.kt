package win.iqwqi.xiangece.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import win.iqwqi.xiangece.ui.screens.TimetableActionsSheet
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

class CourseControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun viewSelectionReflectsCurrentModeAndOnlyChangesWhenNecessary() {
        var week by mutableStateOf(false)
        var changes = 0
        var dismissed = 0
        compose.setContent { XiangeceTheme {
            TimetableActionsSheet("测试课表", emptyList(), 3, 6, {}, {}, {}, week,
                { week = it; changes++ }, {}, {}, {}, {}, { dismissed++ })
        } }
        compose.onNodeWithText("按天").performScrollTo().assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(0, changes) }
        compose.onNodeWithText("按周").performClick().assertIsSelected()
        compose.onNodeWithText("按天").assertIsNotSelected().performClick().assertIsSelected()
        compose.runOnIdle { assertFalse(week); assertEquals(2, changes); assertEquals(0, dismissed) }
        for (label in listOf("课表设置", "背景设置", "已添课程", "常见问题")) {
            compose.onNodeWithTag("course-action-$label").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun pressedActionOnlyPaintsItsCircleAndStillInvokesAction() {
        var backgrounds = 0
        compose.setContent { XiangeceTheme {
            TimetableActionsSheet("测试课表", emptyList(), 3, 6, {}, {}, {}, true,
                {}, {}, { backgrounds++ }, {}, {}, {})
        } }
        val action = compose.onNodeWithTag("course-action-背景设置").performScrollTo()
        val before = action.captureToImage().asAndroidBitmap()
        val radius = with(compose.density) { 24.dp.toPx() }
        val cx = before.width / 2f
        action.performTouchInput { down(Offset(cx, radius)) }
        compose.mainClock.advanceTimeBy(300)
        val pressed = action.captureToImage().asAndroidBitmap()
        var changed = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            val differs = before.getPixel(x, y) != pressed.getPixel(x, y)
            if (differs) changed++
            if ((x-cx)*(x-cx) + (y-radius)*(y-radius) > (radius+2)*(radius+2)) {
                assertFalse("Pressed feedback escaped the circle at $x,$y", differs)
            }
        }
        assertTrue("The icon should show visible press feedback", changed > 0)
        action.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, backgrounds) }
    }
}

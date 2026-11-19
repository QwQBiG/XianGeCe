package win.iqwqi.xiangece.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import win.iqwqi.xiangece.ui.screens.*
import win.iqwqi.xiangece.ui.theme.XiangeceTheme
import win.iqwqi.xiangece.data.local.*

class CalendarUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun swipeMonthsSelectLeapDayAndReturnToToday() {
        var date by mutableStateOf(LocalDate.of(2028, 1, 31))
        compose.setContent { XiangeceTheme {
            CalendarMonthCard(date, LocalDate.of(2028, 1, 31), emptySet(), emptySet(), { date = it })
        } }
        compose.onNodeWithTag("calendar-months").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("calendar-month").assertTextEquals("2028年 2月")
        compose.runOnIdle { assertEquals(LocalDate.of(2028, 2, 29), date) }
        compose.onNodeWithContentDescription("2028-02-12").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2028, 2, 12), date) }
        compose.onNodeWithTag("calendar-months").performTouchInput { swipeRight() }
        compose.onNodeWithText("今天").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2028, 1, 31), date) }
    }

    @Test fun calendarLargeTextOpensTaskEditorWithSelectedDateAndSavesSameCallback() {
        var created: List<String>? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                XiangeceTheme {
                    CalendarScreen(AppUiState(), emptyList(), {}, { _, _ -> true },
                        { title, time, note -> created = listOf(title, time, note) },
                        { _, _, _, _ -> }, {}, {}, { _, _, _, _, _ -> }, LocalDate.of(2026, 9, 10))
                }
            }
        }
        compose.onNodeWithContentDescription("2026-09-10").performClick()
        compose.onNodeWithText("待办").performClick()
        compose.onNodeWithText("2026-09-10 18:00").assertExists()
        compose.onNodeWithText("标题").performTextInput("共享待办")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(listOf("共享待办", "2026-09-10 18:00", ""), created) }
    }

    @Test fun noteCancelDoesNotSaveAndFailedSaveKeepsDraft() {
        var opened by mutableStateOf(true)
        var writes = 0
        var succeed = false
        compose.setContent { XiangeceTheme {
            CalendarEditors(LocalDate.of(2026, 9, 10), "原文", opened, false, null, null, null,
                { opened = false }, {}, {}, {}, { _, _ -> writes++; succeed }, { _, _, _ -> },
                { _, _, _, _ -> }, {}, { _, _, _, _, _ -> })
        } }
        compose.onNodeWithText("写下这一天").performTextReplacement("编辑草稿")
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(0, writes); opened = true }
        compose.onNodeWithText("原文").assertExists()
        compose.onNodeWithText("写下这一天").performTextReplacement("再次编辑")
        compose.onNodeWithText("保存备注").performClick()
        compose.onNodeWithText("再次编辑").assertExists()
        compose.runOnIdle { assertEquals(1, writes); assertTrue(opened); succeed = true }
        compose.onNodeWithText("保存备注").performClick()
        compose.runOnIdle { assertFalse(opened); assertEquals(2, writes) }
    }

    @Test fun timetableDeleteRequiresConfirmationAndCancelDoesNothing() {
        val deleted = mutableListOf<Long>()
        compose.setContent { XiangeceTheme {
            TimetableDeleteChoices(listOf(TimetableEntity(id = 12, name = "待删除")), { deleted += it })
        } }
        compose.onNodeWithContentDescription("删除课表：待删除").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }
        compose.onNodeWithContentDescription("删除课表：待删除").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.runOnIdle { assertEquals(listOf(12L), deleted) }
    }
}

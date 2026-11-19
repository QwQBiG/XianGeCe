package win.iqwqi.xiangece.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import win.iqwqi.xiangece.data.local.TaskEntity
import win.iqwqi.xiangece.domain.model.TaskStatus
import win.iqwqi.xiangece.ui.screens.CalendarScreen
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

class CalendarPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedDayShowsFullWidthTitlesAndRefreshesSharedTasks() {
        val date = LocalDate.of(2026, 9, 10)
        val due = date.atTime(16, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val longTitle = "完成本周读书笔记并整理小组讨论需要提交的材料"
        var tasks by mutableStateOf(listOf(
            TaskEntity(id = 1, title = longTitle, dueAtEpochMillis = due),
            TaskEntity(id = 2, title = "写作", dueAtEpochMillis = due + 1000),
            TaskEntity(id = 3, title = "复习", dueAtEpochMillis = due + 2000)))
        compose.setContent { XiangeceTheme { Box(Modifier.width(360.dp)) {
            CalendarScreen(AppUiState(tasks = tasks), emptyList(), {}, { _, _ -> true },
                { _, _, _ -> }, { _, _, _, _ -> },
                { task -> tasks = tasks.map { if (it.id == task.id) it.copy(status = TaskStatus.DONE) else it } },
                {}, { _, _, _, _, _ -> }, date)
        } } }
        val day = compose.onNodeWithContentDescription("2026-09-10")
        day.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3项安排"))
        day.assert(hasText(longTitle))
        day.assert(hasText("写作"))
        day.assert(hasText("复习"))
        day.performClick()
        val list = compose.onNodeWithTag("calendar-agenda")
        list.performScrollToNode(hasTestTag("calendar-title-1"))
        val title = compose.onNodeWithTag("calendar-title-1", useUnmergedTree = true)
        title.assertTextEquals(longTitle).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        assertTrue(layouts.single().lineCount >= 2)
        list.performScrollToNode(hasTestTag("calendar-toggle-1"))
        compose.onNodeWithTag("calendar-toggle-1").performClick()
        compose.runOnIdle { tasks = tasks.map { if (it.id == 1L) it.copy(title = "修改后的完整读书笔记标题") else it } }
        list.performScrollToNode(hasTestTag("calendar-title-1"))
        compose.onNodeWithTag("calendar-title-1", useUnmergedTree = true).assertTextEquals("修改后的完整读书笔记标题")
        compose.runOnIdle { tasks = tasks.filter { it.id != 2L && it.id != 3L } }
        pressBack()
        compose.waitForIdle()
        day.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1项安排，1项已完成"))
        compose.onNodeWithContentDescription("2026-09-11").performClick()
        list.performScrollToNode(hasText("这一天还没有安排，留些时间给自己。"))
        compose.onNodeWithTag("calendar-title-1", useUnmergedTree = true).assertDoesNotExist()
        pressBack()
        compose.waitForIdle()
        day.performClick()
        compose.runOnIdle { tasks = emptyList() }
        pressBack()
        compose.waitForIdle()
        day.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0项安排"))
    }
}

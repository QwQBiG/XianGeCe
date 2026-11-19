package win.iqwqi.xiangece.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import win.iqwqi.xiangece.domain.model.TaskStatus
import win.iqwqi.xiangece.data.local.*
import win.iqwqi.xiangece.data.settings.*
import win.iqwqi.xiangece.ui.screens.CalendarScreen
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

class CalendarVisualTest {
    @get:Rule val compose = createComposeRule()
    private data class Style(val width: Int = 320, val font: Float = 1f, val dark: Boolean = false)
    @Test fun calendarFitsSmallScreensLargeTypeAndBothAppearances() {
        var style by mutableStateOf(Style())
        val date = LocalDate.of(2026, 9, 10)
        val samples = listOf(3 to "提交作业", 8 to "阅读", 10 to "完成本周读书笔记并整理小组讨论材料", 10 to "小组讨论",
            10 to "复习单词", 10 to "整理笔记", 15 to "实验报告", 21 to "图书馆", 27 to "整理下周计划")
            .mapIndexed { index, (day, title) -> TaskEntity(id = index + 1L, title = title,
                dueAtEpochMillis = date.withDayOfMonth(day).atTime(16, index).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                status = if (index == 4) TaskStatus.DONE else TaskStatus.TODO) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, style.font)) {
                XiangeceTheme(darkTheme = style.dark) {
                    Surface(Modifier.width(style.width.dp).height(750.dp), color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground) {
                    Box(Modifier.fillMaxSize().testTag("calendar-visual")) {
                        CalendarScreen(AppUiState(tasks = samples), listOf(CalendarNoteEntity(date.toEpochDay(), "去图书馆坐坐，整理这周的笔记。")),
                            {}, { _, _ -> true }, { _, _, _ -> }, { _, _, _, _ -> }, {}, {}, { _, _, _, _, _ -> }, date)
                    }
                    }
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = File(context.getExternalFilesDir(null), "calendar-visual").apply { mkdirs() }
        for (width in listOf(320, 360, 412)) for (font in listOf(1f, 1.3f, 1.5f)) for (dark in listOf(false, true)) {
            compose.runOnIdle { style = Style(width, font, dark) }
            compose.onNodeWithText("日历日程").assertIsDisplayed()
            compose.onNodeWithContentDescription("2026-09-10").assertIsDisplayed().assertIsSelected()
            compose.onNode(hasContentDescription("2026-09-30") and
                hasAnyAncestor(hasTestTag("calendar-grid-2026-09"))).assertIsDisplayed()
            compose.onNodeWithTag("calendar-preview-2026-09-10-0", useUnmergedTree = true).assertIsDisplayed()
            val widthDp = compose.onNodeWithTag("calendar-visual").fetchSemanticsNode().size.width /
                context.resources.displayMetrics.density
            check(kotlin.math.abs(widthDp - width) < 1f) { "Expected ${width}dp, rendered ${widthDp}dp" }
            val bitmap = compose.onNodeWithTag("calendar-visual").captureToImage().asAndroidBitmap()
            File(output, "${width}-${font}-${dark}.png").outputStream().use {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            compose.onNodeWithContentDescription("2026-09-10").performClick()
            compose.onNodeWithTag("calendar-agenda").performScrollToNode(hasTestTag("calendar-title-3"))
            val title = compose.onNodeWithTag("calendar-title-3", useUnmergedTree = true)
            title.assertTextEquals("完成本周读书笔记并整理小组讨论材料").assertIsDisplayed()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            title.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            check(!layouts.single().hasVisualOverflow)
            val agenda = compose.onNodeWithTag("calendar-agenda").captureToImage().asAndroidBitmap()
            File(output, "agenda-${width}-${font}-${dark}.png").outputStream().use {
                check(agenda.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            compose.onNodeWithTag("calendar-agenda").performScrollToNode(hasText("编辑备注"))
            compose.onNodeWithText("编辑备注").assertIsDisplayed()
            pressBack()
            compose.waitForIdle()
        }
    }
}

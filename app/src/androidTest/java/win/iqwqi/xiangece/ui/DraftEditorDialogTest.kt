package win.iqwqi.xiangece.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import win.iqwqi.xiangece.domain.model.DraftType
import win.iqwqi.xiangece.domain.model.ParsedDraft
import win.iqwqi.xiangece.ui.screens.DraftEditorDialog
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

@RunWith(AndroidJUnit4::class)
class DraftEditorDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ambiguityMustBeAcknowledgedBeforeConfirming() {
        var editor by mutableStateOf(
            DraftEditorState(
                inboxId = 1,
                sourceText = "周五晚上8点交实验报告",
                draft = ParsedDraft(
                    type = DraftType.TASK,
                    title = "交实验报告",
                    dateTimeEpochMillis = 1_789_000_000_000,
                    ambiguities = listOf("周五未说明本周或下周"),
                ),
            ),
        )

        composeRule.setContent {
            XiangeceTheme {
                DraftEditorDialog(
                    editor = editor,
                    aiEnabled = false,
                    isWorking = false,
                    semesterWeekCount = 20,
                    onDismiss = {},
                    onSourceTextChange = { editor = editor.copy(sourceText = it) },
                    onReparse = {},
                    onTitleChange = { editor = editor.copy(title = it) },
                    onDateTimeChange = { editor = editor.copy(dateTimeText = it) },
                    onCourseNameChange = { editor = editor.copy(courseName = it) },
                    onLocationChange = { editor = editor.copy(location = it) },
                    onTeachingWeekChange = { editor = editor.copy(teachingWeekText = it) },
                    onDayOfWeekChange = { editor = editor.copy(dayOfWeekText = it) },
                    onStartPeriodChange = { editor = editor.copy(startPeriodText = it) },
                    onEndPeriodChange = { editor = editor.copy(endPeriodText = it) },
                    onAmbiguitiesAcknowledged = {
                        editor = editor.copy(ambiguitiesAcknowledged = it)
                    },
                    onTypeChange = { editor = editor.copy(draft = editor.draft.copy(type = it)) },
                    onEnhance = {},
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithText("确认写入").assertIsNotEnabled()
        composeRule.onNodeWithText("我已逐项核对并确认").performClick()
        composeRule.onNodeWithText("确认写入").assertIsEnabled()
    }
}

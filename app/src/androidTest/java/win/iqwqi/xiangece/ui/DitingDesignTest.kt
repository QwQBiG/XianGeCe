package win.iqwqi.xiangece.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import win.iqwqi.xiangece.feature.diting.data.DitingSessionEntity
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackState
import win.iqwqi.xiangece.feature.diting.ui.DitingScreen
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

/** Synthetic view state. Does not claim microphone, model or persistence coverage. */
class DitingDesignTest {
    @get:Rule val compose = createComposeRule()
    @Test fun historyRecordingSetupCancelFailureAndActiveControlsRemainReachable() {
        val saved = DitingSessionEntity(id = 1, title = "线性代数 · 向量空间", status = "completed", durationMillis = 900_000)
        var selected by mutableStateOf<DitingSessionEntity?>(null)
        var starts = 0
        var deletes = 0
        compose.setContent { XiangeceTheme {
            Box(Modifier.width(320.dp).fillMaxHeight()) {
                DitingScreen(
                    sessions = listOf(saved), selectedSession = selected, segments = emptyList(), markers = emptyList(),
                    onStart = { title, _, _, _, transcription, annotation ->
                        assertEquals("几何与证明", title)
                        assertTrue(transcription); assertFalse(annotation)
                        starts++
                        if (starts > 1) selected = saved.copy(id = 2, title = title, status = "draft")
                    },
                    onPause = { selected = selected?.copy(status = "paused") },
                    onResume = { selected = selected?.copy(status = "recording") },
                    onStop = { selected = selected?.copy(status = "completed") },
                    onRetryOfflineTranscription = {}, onReanalyzeSession = {},
                    onMarkHighlight = {}, onMarkQuestion = {}, onTogglePlayback = {},
                    onSeekPlayback = { _, _ -> }, onPlayClip = { _, _, _ -> }, onPlaySegment = {},
                    playbackPositionMillis = 0, playbackDurationMillis = 0, playbackSpeed = 1f,
                    onSetPlaybackSpeed = {}, courses = emptyList(), onUpdateSession = { _, _, _ -> },
                    onUpdateSegment = { _, _ -> }, onUpdateMarker = { _, _, _ -> }, onDeleteMarker = {},
                    isPlaying = false, isRetryingOfflineTranscription = false, isReanalyzingSession = false,
                    creatingSession = false, localRecognitionAvailable = false, offlinePackState = DitingOfflinePackState(),
                    onOpenResources = {}, cloudTranscriptionConfigured = false, aiAnnotationEnabled = false,
                    aiAnnotationConfigured = false, notificationsAvailable = true,
                    onShareTranscript = { _, _, _ -> }, onShareAudio = {}, onDelete = { deletes++ },
                    onSelect = { selected = saved }, onCollectNotebook = {},
                )
            }
        } }
        compose.onNodeWithText(saved.title).assertIsDisplayed()
        compose.onNodeWithText("课堂名称").assertDoesNotExist()
        compose.onNodeWithText("查看").performClick()
        compose.onNodeWithTag("diting-list").performScrollToNode(hasText("文字与重点"))
        compose.onNodeWithText("文字与重点").assertIsDisplayed()
        compose.onNodeWithTag("diting-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("历史课堂").assertIsDisplayed()
        compose.onNodeWithContentDescription("删除").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithContentDescription("删除").performClick()
        compose.onNodeWithText("删除", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, deletes) }
        compose.onNodeWithText("新录音").performClick()
        compose.onNode(hasSetTextAction() and hasText("课堂名称")).performTextInput("几何与证明")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("新录音").performClick()
        compose.onNode(hasSetTextAction() and hasText("课堂名称")).assertTextContains("几何与证明")
        compose.onNodeWithText("开始录音").performClick()
        compose.runOnIdle { assertEquals(1, starts) }
        compose.onNode(hasSetTextAction() and hasText("课堂名称")).assertTextContains("几何与证明")
        compose.onNodeWithText("开始录音").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("正在准备录音，请稍候…").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("课堂名称").assertDoesNotExist()
        compose.runOnIdle { selected = selected?.copy(status = "recording") }
        compose.onNodeWithText("暂停").performClick()
        compose.onNodeWithText("继续").performClick()
        compose.runOnIdle { assertEquals("recording", selected?.status) }
        compose.onNodeWithText("结束并保存").performClick()
        compose.runOnIdle { assertEquals("completed", selected?.status) }
    }
}

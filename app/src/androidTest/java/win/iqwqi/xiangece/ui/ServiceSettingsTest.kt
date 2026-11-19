package win.iqwqi.xiangece.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import win.iqwqi.xiangece.ui.screens.AiSheet
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

/** Exercises UI orchestration only; no external service is contacted. */
class ServiceSettingsTest {
    @get:Rule val compose = createComposeRule()
    private fun reveal(label: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
    }

    @Test fun qwenPresetSavesCoherentTextVisionAndSpeechConfiguration() {
        var textModel = ""
        var visionModel = ""
        var speechModel = ""
        var endpoint = ""
        var saves = 0
        compose.setContent { XiangeceTheme { AiSheet(AppUiState(),
            onSaveAi = { _, _, _, text, vision, _, _, _ -> textModel = text; visionModel = vision; saves++ },
            onTestAi = { _, _, _, _, _, _, _, _ -> Result.success(Unit) },
            onSaveDitingTranscription = { model, url -> speechModel = model; endpoint = url }, onDismiss = {}) } }
        reveal("通义千问")
        compose.onNodeWithText("通义千问").performClick()
        compose.runOnIdle { assertEquals(0, saves) }
        reveal("保存")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals(1, saves)
            assertEquals("qwen-plus", textModel)
            assertEquals("qwen-vl-plus", visionModel)
            assertEquals("fun-asr-realtime", speechModel)
            assertEquals("wss://dashscope.aliyuncs.com/api-ws/v1/inference", endpoint)
        }
    }

    @Test fun testingCanBeStoppedRetriedAndCancelledByClosingSheet() {
        var visible by mutableStateOf(true)
        var calls = 0
        var cancelled = 0
        compose.setContent { XiangeceTheme { if (visible) AiSheet(AppUiState(),
            onSaveAi = { _, _, _, _, _, _, _, _ -> },
            onTestAi = { _, _, _, _, _, _, _, _ ->
                calls++
                if (calls == 2) Result.success(Unit)
                else try { awaitCancellation() } finally { cancelled++ }
            }, onSaveDitingTranscription = { _, _ -> }, onDismiss = { visible = false }) } }
        reveal("测试文字服务")
        compose.onNodeWithText("测试文字服务").performClick()
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("停止测试").performClick()
        compose.waitUntil(5000) { cancelled == 1 }
        compose.onNodeWithText("已停止测试，设置尚未保存。").assertExists()
        compose.onNodeWithText("测试文字服务").performClick()
        compose.waitUntil(5000) { calls == 2 }
        compose.onNode(hasText("文字服务已连通。", substring = true)).assertExists()
        compose.onNodeWithText("保存").assertIsEnabled()
        reveal("测试文字服务")
        compose.onNodeWithText("测试文字服务").performClick()
        compose.waitUntil(5000) { calls == 3 }
        compose.runOnIdle { visible = false }
        compose.waitUntil(5000) { cancelled == 2 }
    }
}

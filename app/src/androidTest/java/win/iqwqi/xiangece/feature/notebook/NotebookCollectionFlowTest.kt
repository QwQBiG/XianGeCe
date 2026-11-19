package win.iqwqi.xiangece.feature.notebook

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import win.iqwqi.xiangece.feature.notebook.ui.NotebookActivity

@RunWith(AndroidJUnit4::class)
class NotebookCollectionFlowTest {
    @get:Rule val compose = createAndroidComposeRule<NotebookActivity>()
    @Test fun collectReviewSourceAndRetrieveWithoutNetwork() {
        val title = "收集验证-${System.currentTimeMillis()}"
        compose.waitUntil(10000) { compose.onAllNodesWithText("收集资料").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("收集资料").performClick()
        compose.onNodeWithText("写入或粘贴文字").performClick()
        compose.waitUntil(10000) { compose.onAllNodes(hasSetTextAction() and hasText("笔记名称")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("笔记名称")).performTextReplacement(title)
        compose.onNode(hasSetTextAction() and hasText("原始文字")).performTextInput("为状态栏保留安全区域，正文正常滚动。保留这份合成材料供核对。")
        compose.onNodeWithText("保存原始材料为笔记").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("查看原始材料").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("查看原始材料").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("来源与回听").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("为状态栏保留安全区域，正文正常滚动。保留这份合成材料供核对。").assertExists()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithContentDescription("返回笔记库").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("问问笔记").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("问问笔记").performClick()
        compose.onNode(hasSetTextAction() and hasText("想找什么，或者想了解什么？")).performTextInput("状态栏安全区域")
        compose.onNodeWithText("本机查找").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).assertExists()
    }
}

package win.iqwqi.xiangece.feature.notebook

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import win.iqwqi.xiangece.feature.notebook.ui.NotebookActivity

@RunWith(AndroidJUnit4::class)
class NotebookFlowTest {
    @get:Rule val compose = createAndroidComposeRule<NotebookActivity>()

    @Test fun writePreviewReopenRenameTrashRestore() {
        val title = "验证笔记-${System.currentTimeMillis()}"
        compose.onNodeWithContentDescription("新建笔记").performClick()
        compose.onNode(hasSetTextAction() and hasText("名称")).performTextInput(title)
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("编辑").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput("# 课堂随记\n\n矩阵与向量空间 #数学\n\n- [ ] 回顾例题\n\n[[线性代数]]")
        compose.onNodeWithText("预览").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("已保存").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("返回笔记库").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("新建笔记").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notebook-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput(title)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(10000) {
            compose.onAllNodes(SemanticsMatcher("search has results") { node ->
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                    ?.any { Regex("^[1-9][0-9]* 篇匹配的笔记$").matches(it.text) } == true
            }).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("notebook-list").performScrollToNode(hasText(title) and !hasSetTextAction())
        compose.onNode(hasText(title) and !hasSetTextAction()).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("编辑").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("编辑").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("矩阵与向量空间", substring = true)
        compose.onNodeWithContentDescription("返回笔记库").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("notebook-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notebook-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).assertTextContains(title)
        compose.waitUntil(10000) {
            compose.onAllNodes(SemanticsMatcher("search has results") { node ->
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                    ?.any { Regex("^[1-9][0-9]* 篇匹配的笔记$").matches(it.text) } == true
            }).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("notebook-list").performScrollToNode(hasText(title) and !hasSetTextAction())
        compose.onNode(hasText(title) and !hasSetTextAction()).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("笔记菜单").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("笔记菜单").performClick()
        compose.onNodeWithText("名称与文件夹").performClick()
        compose.onNode(hasSetTextAction() and hasText("名称")).performTextReplacement("$title-改名")
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("$title-改名").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("笔记菜单").performClick()
        compose.onNodeWithText("移到回收站").performClick()
        compose.onNodeWithText("移入").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("notebook-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notebook-list").performScrollToNode(hasContentDescription("笔记库菜单"))
        compose.onNodeWithContentDescription("笔记库菜单").performClick()
        compose.onNodeWithText("回收站").performClick()
        compose.onNodeWithTag("notebook-list").performScrollToNode(hasText("$title-改名"))
        compose.onNodeWithText("$title-改名").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("编辑").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("编辑").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("矩阵与向量空间", substring = true)
    }
}

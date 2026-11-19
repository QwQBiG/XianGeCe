package win.iqwqi.xiangece.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import win.iqwqi.xiangece.MainActivity

class NavigationRestoreTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recreationKeepsSelectedMainPage() {
        val toolbox = hasText("百宝") and hasClickAction()
        compose.waitUntil(20000) { compose.onAllNodes(toolbox).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(toolbox).performClick().assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20000) { compose.onAllNodes(toolbox).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(toolbox).assertIsSelected()
        compose.onNodeWithText("笔记库").assertExists()
    }
}

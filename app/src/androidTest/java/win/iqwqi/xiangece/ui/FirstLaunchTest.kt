package win.iqwqi.xiangece.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import win.iqwqi.xiangece.MainActivity

@RunWith(AndroidJUnit4::class)
class FirstLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchShowsSemesterSetup() {
        composeRule.onNodeWithText("弦歌册").assertIsDisplayed()
        composeRule.onNodeWithText("开始使用").assertIsDisplayed()
    }
}

package win.iqwqi.xiangece.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import win.iqwqi.xiangece.data.settings.*
import win.iqwqi.xiangece.ui.companion.CompanionHomeHeader
import win.iqwqi.xiangece.ui.components.CoursePaletteChoices
import win.iqwqi.xiangece.ui.screens.AppearanceSheet
import win.iqwqi.xiangece.ui.theme.*

@RunWith(AndroidJUnit4::class)
class VisualRefinementTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appearancePaletteHasOneVerticalScrollAndGesturesReachOldColors() {
        var selected: String? = null
        compose.setContent {
            XiangeceTheme {
                AppearanceSheet(AppUiState(), { selected = it }, {}, {}, {}, {})
            }
        }
        val vertical = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        compose.onAllNodes(vertical).assertCountEquals(1)
        compose.onNodeWithText("晴空蓝").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("sky", selected) }
        repeat(3) { compose.onNode(vertical).performTouchInput { swipeUp() } }
        compose.onNodeWithText("墨黑").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("ink", selected) }
        compose.onNodeWithText("外观与角色").assertIsDisplayed()
    }

    @Test fun largeTypeHeaderKeepsAllExistingActionsClickable() {
        var wardrobe = 0; var search = 0; var create = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                XiangeceTheme {
                    CompanionHomeHeader(CompanionAppearance(motionEnabled = false), "早上好", "9月10日 星期四",
                        "2026 秋季学期", "第 1 周 · 共 20 周", 0.1f,
                        { wardrobe++ }, {}, { search++ }, { create++ })
                }
            }
        }
        compose.onNodeWithText("换装").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("搜索").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("新建事项").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, wardrobe); assertEquals(1, search); assertEquals(1, create) }
    }

    @Test fun hiddenHeaderKeepsSearchAndCreateWithoutCharacterControls() {
        compose.setContent {
            XiangeceTheme {
                CompanionHomeHeader(CompanionAppearance(display = CompanionDisplay.HIDDEN), "早上好", "9月10日",
                    "秋季学期", "第 1 周", null, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("换装").assertDoesNotExist()
        compose.onNodeWithTag("home-portrait").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索").assertIsDisplayed()
        compose.onNodeWithContentDescription("新建事项").assertIsDisplayed()
    }

    @Test fun coursePresetsKeepExistingSelectionAndCustomColorAction() {
        var color by mutableStateOf(0xFF52796F)
        var custom = false
        compose.setContent {
            XiangeceTheme {
                Column { CoursePaletteChoices(listOf(0xFF52796F), color, { color = it }, { custom = true }) }
            }
        }
        compose.onNodeWithContentDescription("经典 颜色 1").assertIsSelected()
        compose.onNodeWithContentDescription("清新 颜色 1").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(modernCourseColors.first(), color) }
        compose.onNodeWithText("自定义 RGB").performClick()
        compose.runOnIdle { assertTrue(custom) }
    }
}

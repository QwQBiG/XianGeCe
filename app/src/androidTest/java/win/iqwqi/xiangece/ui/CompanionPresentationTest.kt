package win.iqwqi.xiangece.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import win.iqwqi.xiangece.data.settings.*
import win.iqwqi.xiangece.ui.companion.CompanionHero
import win.iqwqi.xiangece.ui.companion.CompanionCue
import win.iqwqi.xiangece.ui.companion.CompanionEvent
import win.iqwqi.xiangece.ui.companion.CompanionFeedbackHost
import kotlinx.coroutines.flow.MutableSharedFlow
import win.iqwqi.xiangece.ui.companion.WardrobeSheet
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

@RunWith(AndroidJUnit4::class)
class CompanionPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingPreviewThenCancellingDoesNotApplyAppearance() {
        var applied: CompanionAppearance? = null
        var open by mutableStateOf(true)
        compose.setContent {
            XiangeceTheme {
                if (open) WardrobeSheet(CompanionAppearance(), "indigo",
                    onApply = { applied = it }, onDismiss = { open = false })
            }
        }
        compose.onNodeWithText("书院").performScrollTo().performClick()
        compose.onNodeWithText("书院日常").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertNull(applied); assertFalse(open) }
    }

    @Test fun applyingPreviewDeliversChosenOutfitOnce() {
        var applied: CompanionAppearance? = null
        var count = 0
        var open by mutableStateOf(true)
        compose.setContent {
            XiangeceTheme {
                if (open) WardrobeSheet(CompanionAppearance(), "indigo",
                    onApply = { applied = it; count++ }, onDismiss = { open = false })
            }
        }
        compose.onNodeWithText("书院").performScrollTo().performClick()
        compose.onNodeWithText("应用装扮").performClick()
        compose.runOnIdle {
            assertEquals(CompanionOutfit.ACADEMY, applied?.outfit)
            assertEquals(1, count)
            assertFalse(open)
        }
    }

    @Test fun savedFeedbackSurvivesDialogDismissalButNeverReplaysAfterPageChange() {
        val events = MutableSharedFlow<CompanionEvent>(extraBufferCapacity = 1)
        var obscured by mutableStateOf(true)
        var page by mutableStateOf(0)
        compose.setContent {
            XiangeceTheme(companion = CompanionAppearance(motionEnabled = false)) {
                CompanionFeedbackHost(events, page, obscured = obscured)
            }
        }
        compose.waitUntil(3_000) { events.subscriptionCount.value == 1 }
        compose.runOnIdle { events.tryEmit(CompanionEvent(CompanionCue.SAVED)) }
        compose.onNodeWithText("收到").assertDoesNotExist()
        compose.runOnIdle { obscured = false }
        compose.waitUntil(2_000) { compose.onAllNodesWithText("收到").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("收到").assertIsDisplayed()
        compose.runOnIdle { page = 1 }
        compose.onNodeWithText("收到").assertDoesNotExist()
        compose.runOnIdle { page = 0 }
        compose.onNodeWithText("收到").assertDoesNotExist()
    }

    @Test fun disabledFeedbackDoesNotCollectSuccessEvents() {
        val events = MutableSharedFlow<CompanionEvent>(extraBufferCapacity = 1)
        compose.setContent {
            XiangeceTheme(companion = CompanionAppearance(feedbackEnabled = false)) {
                CompanionFeedbackHost(events, page = 0)
            }
        }
        compose.runOnIdle {
            assertEquals(0, events.subscriptionCount.value)
            events.tryEmit(CompanionEvent(CompanionCue.SAVED))
        }
        compose.onNodeWithText("收到").assertDoesNotExist()
    }

    @Test fun hiddenModeOmitsHeaderAndGreetingActions() {
        compose.setContent {
            XiangeceTheme(companion = CompanionAppearance(display = CompanionDisplay.HIDDEN)) {
                CompanionHero("下午好", "秋季学期", "第 3 周", 0.2f, {})
            }
        }
        compose.onNodeWithText("下午好").assertDoesNotExist()
        compose.onNodeWithText("换装").assertDoesNotExist()
    }

    @Test fun narrowHeaderAtLargeFontKeepsWardrobeActionVisibleAndClickable() {
        var opened = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                XiangeceTheme {
                    Box(Modifier.width(288.dp)) {
                        CompanionHero("下午好", "2026 年秋季学期", "第 3 周 · 还有 17 周", 0.15f,
                            onWardrobe = { opened = true })
                    }
                }
            }
        }
        compose.onNodeWithText("换装").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun backgroundDiscardsVisibleAndNewFeedbackWithoutReplaying() {
        val events = MutableSharedFlow<CompanionEvent>(extraBufferCapacity = 1)
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this)
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        compose.setContent {
            CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                XiangeceTheme(companion = CompanionAppearance(motionEnabled = false)) {
                    CompanionFeedbackHost(events, page = 0)
                }
            }
        }
        compose.runOnIdle { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.waitUntil(3_000) { events.subscriptionCount.value == 1 }
        compose.onNodeWithText("收到").assertDoesNotExist()
        compose.runOnIdle { events.tryEmit(CompanionEvent(CompanionCue.SAVED)) }
        compose.waitUntil(2_000) { compose.onAllNodesWithText("收到").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
        compose.waitUntil(2_000) { events.subscriptionCount.value == 0 }
        compose.onNodeWithText("收到").assertDoesNotExist()
        compose.runOnIdle {
            events.tryEmit(CompanionEvent(CompanionCue.SAVED))
            owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        }
        compose.waitUntil(2_000) { events.subscriptionCount.value == 1 }
        compose.onNodeWithText("收到").assertDoesNotExist()
        compose.runOnIdle { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED }
    }

    @Test fun defaultMotionShowsFeedbackAndRecordingPauseDiscardsIt() {
        val events = MutableSharedFlow<CompanionEvent>(extraBufferCapacity = 1)
        var paused by mutableStateOf(false)
        compose.setContent {
            XiangeceTheme(companion = CompanionAppearance()) {
                CompanionFeedbackHost(events, page = 0, paused = paused)
            }
        }
        compose.waitUntil(3_000) { events.subscriptionCount.value == 1 }
        compose.runOnIdle { events.tryEmit(CompanionEvent(CompanionCue.SAVED)) }
        compose.waitUntil(2_000) { compose.onAllNodesWithText("收到").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("收到").assertIsDisplayed()
        compose.runOnIdle { paused = true }
        compose.waitUntil(2_000) { events.subscriptionCount.value == 0 }
        compose.onNodeWithText("收到").assertDoesNotExist()
        compose.runOnIdle {
            events.tryEmit(CompanionEvent(CompanionCue.SAVED))
            paused = false
        }
        compose.waitUntil(2_000) { events.subscriptionCount.value == 1 }
        compose.onNodeWithText("收到").assertDoesNotExist()
    }

    @Test fun staleSuccessEventIsDiscardedAtCollection() {
        val events = MutableSharedFlow<CompanionEvent>(extraBufferCapacity = 1)
        compose.setContent {
            XiangeceTheme(companion = CompanionAppearance(motionEnabled = false)) {
                CompanionFeedbackHost(events, page = 0)
            }
        }
        compose.waitUntil(3_000) { events.subscriptionCount.value == 1 }
        compose.runOnIdle {
            events.tryEmit(CompanionEvent(CompanionCue.SAVED, createdAtMillis = System.currentTimeMillis() - 5_000))
        }
        compose.waitForIdle()
        compose.onNodeWithText("收到").assertDoesNotExist()
    }
}

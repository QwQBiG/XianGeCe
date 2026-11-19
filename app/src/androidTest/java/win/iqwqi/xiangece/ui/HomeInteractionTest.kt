package win.iqwqi.xiangece.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import win.iqwqi.xiangece.data.settings.*
import win.iqwqi.xiangece.ui.companion.*
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

class HomeInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tapsCycleDistinctDrawingsForBothOutfitsAndCompactMode() {
        var appearance by mutableStateOf(CompanionAppearance(motionEnabled = false))
        compose.setContent { XiangeceTheme {
            CompanionHomeHeader(appearance, "早上好", "9月10日", "2026 秋季学期", "第 1 周", .2f, {}, {}, {}, {}, modifier = Modifier.width(360.dp))
        } }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = File(context.getExternalFilesDir(null), "home-interaction").apply { mkdirs() }
        for (outfit in CompanionOutfit.entries) for (display in listOf(CompanionDisplay.STANDARD, CompanionDisplay.COMPACT)) {
            compose.runOnIdle { appearance = appearance.copy(outfit = outfit, display = display) }
            var previous: android.graphics.Bitmap? = null
            for (pose in CompanionPose.entries) {
                val node = compose.onNodeWithTag("home-portrait")
                node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, pose.label))
                val bitmap = node.captureToImage().asAndroidBitmap()
                previous?.let { assertFalse("Each pose must have a different drawing", bitmap.sameAs(it)) }
                previous = bitmap
                val header = compose.onNodeWithTag("home-header").captureToImage().asAndroidBitmap()
                File(output, "${outfit}-${display}-${pose}.png").outputStream().use {
                    check(header.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
                node.performClick()
            }
            compose.onNodeWithTag("home-portrait").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, CompanionPose.IDLE.label))
        }
    }

    @Test fun backgroundingClearsPoseBeforeReturning() {
        lateinit var registry: LifecycleRegistry
        lateinit var owner: LifecycleOwner
        compose.runOnIdle {
            owner = object : LifecycleOwner { override val lifecycle get() = registry }
            registry = LifecycleRegistry(owner).apply { currentState = Lifecycle.State.RESUMED }
        }
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            XiangeceTheme {
                CompanionHomeHeader(CompanionAppearance(motionEnabled = false), "早上好", "9月10日", "秋季学期", "第 1 周", .2f,
                    {}, {}, {}, {})
            }
        } }
        val node = compose.onNodeWithTag("home-portrait")
        node.performClick()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, CompanionPose.WELCOME.label))
        compose.runOnIdle { registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { registry.currentState = Lifecycle.State.RESUMED }
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, CompanionPose.IDLE.label))
    }

    @Test fun leavingPageAndDisablingFeedbackDiscardThePose() {
        var appearance by mutableStateOf(CompanionAppearance())
        var active by mutableStateOf(true)
        compose.setContent { XiangeceTheme {
            CompanionHomeHeader(appearance, "早上好", "9月10日", "秋季学期", "第 1 周", .2f,
                {}, {}, {}, {}, active = active)
        } }
        val node = compose.onNodeWithTag("home-portrait")
        node.performClick()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, CompanionPose.WELCOME.label))
        compose.runOnIdle { active = false }
        compose.runOnIdle { active = true }
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, CompanionPose.IDLE.label))
        node.performClick()
        compose.runOnIdle { appearance = appearance.copy(feedbackEnabled = false) }
        node.performClick()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, CompanionPose.IDLE.label))
        compose.runOnIdle { appearance = appearance.copy(feedbackEnabled = true, outfit = CompanionOutfit.ACADEMY) }
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, CompanionPose.IDLE.label))
    }
}

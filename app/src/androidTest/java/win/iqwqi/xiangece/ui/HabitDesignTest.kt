package win.iqwqi.xiangece.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import win.iqwqi.xiangece.data.local.HabitTemplateEntity
import win.iqwqi.xiangece.ui.screens.HabitsScreen
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

class HabitDesignTest {
    @get:Rule val compose = createComposeRule()
    private data class Style(val width: Int = 320, val font: Float = 1f, val dark: Boolean = false)
    @Test fun wholePageScrollsAndCheckinCanBeCancelledAtLargeFont() {
        var style by mutableStateOf(Style())
        var done by mutableStateOf(emptySet<Long>())
        val habits = listOf(
            HabitTemplateEntity(1, "读一会儿书", "留下今天值得记住的一句话"),
            HabitTemplateEntity(2, "去操场走走", "不赶路，慢慢积累"),
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, style.font)) {
                XiangeceTheme(darkTheme = style.dark) {
                    Surface(Modifier.width(style.width.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground) {
                    Box(Modifier.fillMaxSize().testTag("habit-visual")) {
                        HabitsScreen(AppUiState(habits = habits, habitStats = HabitStats(12, 3, 21, 10, done)),
                            PaddingValues(0.dp), { _, _, _, _ -> }, { _, _, _, _, _ -> }, {},
                            { id -> done = if (id in done) done - id else done + id })
                    }
                    }
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = File(context.getExternalFilesDir(null), "design-refinement").apply { mkdirs() }
        for (width in listOf(320, 360, 412)) for (font in listOf(1f, 1.5f)) for (dark in listOf(false, true)) {
            compose.runOnIdle { style = Style(width, font, dark) }
            compose.onNodeWithTag("habits-list").performScrollToIndex(0)
            compose.onNodeWithText("厚积").assertIsDisplayed()
            val bitmap = compose.onNodeWithTag("habit-visual").captureToImage().asAndroidBitmap()
            File(output, "habits-${width}-${font}-${dark}.png").outputStream().use {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            compose.onNodeWithTag("habits-list").performScrollToNode(hasText("去操场走走"))
            compose.onNodeWithText("去操场走走").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(setOf(2L), done) }
            compose.onNodeWithTag("habits-list").performScrollToNode(hasText("去操场走走"))
            compose.onNodeWithText("去操场走走").performClick()
            compose.runOnIdle { assertTrue(done.isEmpty()) }
        }
        compose.onNodeWithContentDescription("新增长期事项").performClick()
        compose.onNodeWithText("取消").assertIsDisplayed().performClick()
    }
}

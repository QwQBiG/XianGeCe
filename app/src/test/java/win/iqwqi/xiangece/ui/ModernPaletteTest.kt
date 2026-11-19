package win.iqwqi.xiangece.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.ui.theme.*

class ModernPaletteTest {
    @Test fun newPresetsKeepWhiteCourseTitlesAndButtonsReadable() {
        val backgrounds = modernCourseColors.map { Color(it) } + modernThemeSeeds.map { it.lightPrimary }
        backgrounds.forEach { color ->
            val ratio = 1.05f / (color.luminance() + 0.05f)
            assertTrue("White text contrast $ratio is below 4.5 for $color", ratio >= 4.5f)
        }
    }
}

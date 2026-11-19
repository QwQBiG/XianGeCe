package win.iqwqi.xiangece.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

private fun modernSeed(id: String, name: String, primary: Long, pale: Long, night: Long, deep: Long) = ThemeSeed(
    id = id, name = name, lightPrimary = Color(primary), lightPrimaryContainer = Color(pale),
    lightSecondary = Color(0xFF5B6684), lightSecondaryContainer = Color(0xFFE8EDF8),
    darkPrimary = Color(night), darkPrimaryContainer = Color(deep),
    darkSecondary = Color(0xFFC0C9E4), darkSecondaryContainer = Color(0xFF35415C), modern = true,
)

val modernThemeSeeds = listOf(
    modernSeed("sky", "晴空蓝", 0xFF3567AA, 0xFFDDEBFF, 0xFFAECFFF, 0xFF254976),
    modernSeed("iris", "雾紫", 0xFF7161B4, 0xFFEAE3FF, 0xFFD0C2FF, 0xFF483C78),
    modernSeed("mint", "薄荷青", 0xFF28766D, 0xFFD8F1E9, 0xFFA0DACE, 0xFF20534D),
    modernSeed("rose", "珊瑚粉", 0xFFA94F70, 0xFFFBE1EB, 0xFFF2B5CE, 0xFF70344C),
)

/** Existing saved course colors keep their meaning; these are additional editor presets. */
val modernCourseColors = listOf(
    0xFF4E72B8, 0xFF8064B5, 0xFF337F74, 0xFFAB5C80,
    0xFF347B98, 0xFF9E633D, 0xFF647B50, 0xFF657094,
)

internal fun modernSurfaces(base: ColorScheme, dark: Boolean): ColorScheme = if (dark) base.copy(
    background = Color(0xFF171D2C), onBackground = Color(0xFFEEF2FC),
    surface = Color(0xFF242D40), onSurface = Color(0xFFEEF2FC),
    surfaceVariant = Color(0xFF354158), onSurfaceVariant = Color(0xFFC1CADD),
    surfaceContainerLowest = Color(0xFF121827), surfaceContainerLow = Color(0xFF1D2638),
    surfaceContainer = Color(0xFF242D40), surfaceContainerHigh = Color(0xFF2C374D),
    surfaceContainerHighest = Color(0xFF354158), outline = Color(0xFF8B98AF),
    outlineVariant = Color(0xFF46516A),
) else base.copy(
    background = Color(0xFFF4F7FC), onBackground = Color(0xFF25314B),
    surface = Color.White, onSurface = Color(0xFF25314B),
    surfaceVariant = Color(0xFFE8EEF8), onSurfaceVariant = Color(0xFF56647D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8FAFF),
    surfaceContainer = Color(0xFFEEF3FB), surfaceContainerHigh = Color(0xFFE8EEF8),
    surfaceContainerHighest = Color(0xFFE0E8F4), outline = Color(0xFF7D8BA4),
    outlineVariant = Color(0xFFD4DEEC),
)

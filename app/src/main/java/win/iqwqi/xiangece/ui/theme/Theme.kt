package win.iqwqi.xiangece.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ============ 基础色定义 ============
val Paper = Color(0xFFF7F2E8)
val PaperRaised = Color(0xFFFFFBF3)
val Ink = Color(0xFF20231F)
val NightPaper = Color(0xFF171A18)
val NightRaised = Color(0xFF202522)

// 松绿主题（默认）
val Pine = Color(0xFF24473D)
val PineSoft = Color(0xFF6B8577)
val PineLight = Color(0xFFD5E5DD)

// 朱砂主题
val Cinnabar = Color(0xFFB4493E)
val CinnabarLight = Color(0xFFF5DDD9)

// 赭石主题
val Ochre = Color(0xFFA4834E)
val OchreLight = Color(0xFFF3E2B9)

// 青蓝主题
val Indigo = Color(0xFF3D5A80)
val IndigoLight = Color(0xFFD4DEEA)

// 紫罗兰主题
val Violet = Color(0xFF6B4E8E)
val VioletLight = Color(0xFFE0D4EC)

// 墨黑主题
val InkBlack = Color(0xFF1A1A2E)
val InkBlackLight = Color(0xFFD4D4DC)

// ============ 主题色方案 ============
data class ThemeSeed(
    val id: String,
    val name: String,
    val lightPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightSecondary: Color,
    val lightSecondaryContainer: Color,
    val darkPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkSecondary: Color,
    val darkSecondaryContainer: Color,
)

val themeSeeds = listOf(
    ThemeSeed(
        id = "pine",
        name = "松绿",
        lightPrimary = Pine,
        lightPrimaryContainer = PineLight,
        lightSecondary = Ochre,
        lightSecondaryContainer = OchreLight,
        darkPrimary = Color(0xFFA9CCBC),
        darkPrimaryContainer = Pine,
        darkSecondary = Color(0xFFD8BD7E),
        darkSecondaryContainer = Color(0xFF5B4A26),
    ),
    ThemeSeed(
        id = "cinnabar",
        name = "朱砂",
        lightPrimary = Cinnabar,
        lightPrimaryContainer = CinnabarLight,
        lightSecondary = Ochre,
        lightSecondaryContainer = OchreLight,
        darkPrimary = Color(0xFFFFB4AA),
        darkPrimaryContainer = Cinnabar,
        darkSecondary = Color(0xFFD8BD7E),
        darkSecondaryContainer = Color(0xFF5B4A26),
    ),
    ThemeSeed(
        id = "indigo",
        name = "青蓝",
        lightPrimary = Indigo,
        lightPrimaryContainer = IndigoLight,
        lightSecondary = Ochre,
        lightSecondaryContainer = OchreLight,
        darkPrimary = Color(0xFFB4C7DE),
        darkPrimaryContainer = Indigo,
        darkSecondary = Color(0xFFD8BD7E),
        darkSecondaryContainer = Color(0xFF5B4A26),
    ),
    ThemeSeed(
        id = "violet",
        name = "紫罗兰",
        lightPrimary = Violet,
        lightPrimaryContainer = VioletLight,
        lightSecondary = PineSoft,
        lightSecondaryContainer = PineLight,
        darkPrimary = Color(0xFFC9B8DE),
        darkPrimaryContainer = Violet,
        darkSecondary = Color(0xFFA9CCBC),
        darkSecondaryContainer = Color(0xFF24473D),
    ),
    ThemeSeed(
        id = "ochre",
        name = "赭石",
        lightPrimary = Ochre,
        lightPrimaryContainer = OchreLight,
        lightSecondary = Pine,
        lightSecondaryContainer = PineLight,
        darkPrimary = Color(0xFFD8BD7E),
        darkPrimaryContainer = Ochre,
        darkSecondary = Color(0xFFA9CCBC),
        darkSecondaryContainer = Color(0xFF24473D),
    ),
    ThemeSeed(
        id = "ink",
        name = "墨黑",
        lightPrimary = InkBlack,
        lightPrimaryContainer = InkBlackLight,
        lightSecondary = Ochre,
        lightSecondaryContainer = OchreLight,
        darkPrimary = Color(0xFFC0C0C8),
        darkPrimaryContainer = InkBlack,
        darkSecondary = Color(0xFFD8BD7E),
        darkSecondaryContainer = Color(0xFF5B4A26),
    ),
)

fun getThemeSeed(id: String): ThemeSeed = themeSeeds.firstOrNull { it.id == id } ?: themeSeeds[0]

private fun buildLightColors(seed: ThemeSeed) = lightColorScheme(
    primary = seed.lightPrimary,
    onPrimary = Color.White,
    primaryContainer = seed.lightPrimaryContainer,
    onPrimaryContainer = Color(0xFF102C24),
    secondary = seed.lightSecondary,
    onSecondary = Color.White,
    secondaryContainer = seed.lightSecondaryContainer,
    onSecondaryContainer = Color(0xFF332A13),
    error = Cinnabar,
    background = Paper,
    onBackground = Ink,
    surface = PaperRaised,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECE6DB),
    onSurfaceVariant = Color(0xFF5E615B),
    outline = Color(0xFF858980),
)

private fun buildDarkColors(seed: ThemeSeed) = darkColorScheme(
    primary = seed.darkPrimary,
    onPrimary = Color.White,
    primaryContainer = seed.darkPrimaryContainer,
    onPrimaryContainer = seed.lightPrimaryContainer,
    secondary = seed.darkSecondary,
    onSecondary = Color.White,
    secondaryContainer = seed.darkSecondaryContainer,
    onSecondaryContainer = seed.lightSecondaryContainer,
    error = Color(0xFFFFB4AA),
    background = NightPaper,
    onBackground = Color(0xFFE6E9E4),
    surface = NightRaised,
    onSurface = Color(0xFFE6E9E4),
    surfaceVariant = Color(0xFF333A36),
    onSurfaceVariant = Color(0xFFC3C9C3),
    outline = Color(0xFF8C938D),
)

@Composable
fun XiangeceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeSeedId: String = "pine",
    content: @Composable () -> Unit,
) {
    val seed = getThemeSeed(themeSeedId)
    MaterialTheme(
        colorScheme = if (darkTheme) buildDarkColors(seed) else buildLightColors(seed),
        content = content,
    )
}

package win.iqwqi.xiangece.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import win.iqwqi.xiangece.data.settings.CompanionAppearance

val LocalCompanionAppearance = staticCompositionLocalOf { CompanionAppearance() }
val LocalCompanionDark = staticCompositionLocalOf { false }

internal fun companionColors(dark: Boolean, academy: Boolean): ColorScheme {
    if (dark) return darkColorScheme(
        primary = Color(0xFFC5B8FF), onPrimary = Color(0xFF25203D),
        primaryContainer = Color(0xFF443C68), onPrimaryContainer = Color(0xFFEAE3FF),
        secondary = Color(0xFFD1C0E0), onSecondary = Color(0xFF302742),
        secondaryContainer = Color(0xFF39364F), onSecondaryContainer = Color(0xFFEAE1F4),
        tertiary = Color(0xFFABD9D0), onTertiary = Color(0xFF173C38),
        background = Color(0xFF191B27), onBackground = Color(0xFFF1F0F6),
        surface = Color(0xFF252836), onSurface = Color(0xFFF1F0F6),
        surfaceVariant = Color(0xFF323643), onSurfaceVariant = Color(0xFFBFC1CF),
        surfaceContainerLowest = Color(0xFF141620),
        surfaceContainerLow = Color(0xFF20232F),
        surfaceContainer = Color(0xFF252836),
        surfaceContainerHigh = Color(0xFF303440),
        surfaceContainerHighest = Color(0xFF3B3E4B),
        outline = Color(0xFF9294AB), outlineVariant = Color(0xFF464960),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = Color(0xFFEAE8F5), inverseOnSurface = Color(0xFF28283D),
        inversePrimary = Color(0xFF6B57AA), surfaceTint = Color(0xFFC5B8FF),
    )
    val ink = if (academy) Color(0xFF302D42) else Color(0xFF272A4B)
    val primary = if (academy) Color(0xFF756192) else Color(0xFF7868C4)
    return lightColorScheme(
        primary = primary, onPrimary = Color.White,
        primaryContainer = Color(0xFFEAE4FA), onPrimaryContainer = Color(0xFF433663),
        secondary = Color(0xFF66658B), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE9E7F4), onSecondaryContainer = Color(0xFF363450),
        tertiary = Color(0xFF426C64), onTertiary = Color.White,
        background = if (academy) Color(0xFFF7F3EE) else Color(0xFFF5F6FC),
        onBackground = ink,
        surface = if (academy) Color(0xFFFFFDF8) else Color.White,
        onSurface = ink,
        surfaceVariant = if (academy) Color(0xFFECE6ED) else Color(0xFFECEBF5),
        onSurfaceVariant = if (academy) Color(0xFF6C6678) else Color(0xFF666B80),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = if (academy) Color(0xFFF9F5F0) else Color(0xFFF5F3FC),
        surfaceContainer = if (academy) Color(0xFFF2ECED) else Color(0xFFF0EDF8),
        surfaceContainerHigh = if (academy) Color(0xFFEFEAE4) else Color(0xFFEBEDF5),
        surfaceContainerHighest = if (academy) Color(0xFFE7E1DC) else Color(0xFFE2E5EF),
        outline = Color(0xFF878194), outlineVariant = Color(0xFFDDD7E8),
        error = Color(0xFFB3261E), onError = Color.White,
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        inverseSurface = Color(0xFF302E43), inverseOnSurface = Color(0xFFF5F0FF),
        inversePrimary = Color(0xFFCDBEFF), surfaceTint = primary,
    )
}

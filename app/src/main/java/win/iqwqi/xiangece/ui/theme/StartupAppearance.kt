package win.iqwqi.xiangece.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import win.iqwqi.xiangece.R
import win.iqwqi.xiangece.data.settings.AppSettings

/** A small startup cache; DataStore remains the source of truth for settings. */
object StartupAppearance {
    private const val PREFS = "startup_appearance"
    private const val MODE = "mode"
    private const val SYSTEM = 0
    private const val LIGHT = 1
    private const val DARK = 2

    fun cachedTheme(context: Context): Int = theme(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(MODE, LIGHT),
    )

    fun persist(activity: Activity, settings: AppSettings) {
        val mode = when {
            settings.followSystemTheme -> SYSTEM
            settings.darkMode -> DARK
            else -> LIGHT
        }
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(MODE, -1) != mode) prefs.edit().putInt(MODE, mode).apply()
        if (Build.VERSION.SDK_INT >= 31) {
            // The OS persists the stable theme NAME for future cold launches. This
            // changes only the splash, so Compose's "system" mode stays system-driven.
            activity.splashScreen.setSplashScreenTheme(theme(mode))
        }
    }

    private fun theme(mode: Int): Int = when (mode) {
        SYSTEM -> R.style.Theme_Xiangece_StartingSystem
        DARK -> R.style.Theme_Xiangece_StartingDark
        else -> R.style.Theme_Xiangece_StartingLight
    }
}

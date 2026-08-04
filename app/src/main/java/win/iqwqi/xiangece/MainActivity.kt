package win.iqwqi.xiangece

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import win.iqwqi.xiangece.ui.MainViewModel
import win.iqwqi.xiangece.ui.XiangeceRoot
import win.iqwqi.xiangece.ui.theme.XiangeceTheme
import win.iqwqi.xiangece.widget.XiangeceWidgetProvider

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The app draws behind system bars; the actual bottom surface color is
        // applied from the active theme below so legacy gesture/navigation
        // areas do not become a transparent or gray strip.
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val useDarkTheme = if (state.settings.followSystemTheme) isSystemInDarkTheme() else state.settings.darkMode
            XiangeceTheme(
                darkTheme = useDarkTheme,
                themeSeedId = state.settings.themeSeed,
            ) {
                val navigationSurface = MaterialTheme.colorScheme.surface.toArgb()
                SideEffect {
                    window.navigationBarColor = navigationSurface
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightNavigationBars = !useDarkTheme
                }
                XiangeceRoot(state = state, viewModel = viewModel)
            }
            LaunchedEffect(Unit) {
                viewModel.handleIntent(intent)
                setIntent(Intent())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIntent(intent)
        setIntent(Intent())
    }

    override fun onResume() {
        super.onResume()
        XiangeceWidgetProvider.refreshAll(this)
    }
}

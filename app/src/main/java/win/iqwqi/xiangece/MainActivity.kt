package win.iqwqi.xiangece

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            XiangeceTheme(
                darkTheme = state.settings.darkMode,
                themeSeedId = state.settings.themeSeed,
            ) {
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

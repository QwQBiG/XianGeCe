package win.iqwqi.xiangece.ui.theme

import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import win.iqwqi.xiangece.data.settings.AppSettings

/** Keep the preceding window visible until settings are ready, without blocking input dispatch. */
fun ComponentActivity.setContentAfterAppearance(
    settings: Flow<AppSettings>,
    content: @Composable (AppSettings) -> Unit,
) {
    val root = findViewById<android.view.View>(android.R.id.content)
    var ready = false
    root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            if (!ready) return false
            root.viewTreeObserver.removeOnPreDrawListener(this)
            return true
        }
    })
    lifecycleScope.launch {
        val initial = settings.first()
        setContent { content(initial) }
        ready = true
        root.postInvalidateOnAnimation()
    }
}

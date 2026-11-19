package win.iqwqi.xiangece.ui.companion

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import win.iqwqi.xiangece.ui.theme.LocalCompanionAppearance

/** A burst of check-ins produces one celebration, never a playback queue. */
class CompanionFeedbackGate {
    private var lastHappyAt: Long? = null
    fun accept(cue: CompanionCue, now: Long): Boolean {
        if (cue != CompanionCue.HAPPY) return true
        if (lastHappyAt?.let { now - it < 30_000 } == true) return false
        lastHappyAt = now
        return true
    }
}

@Composable
fun CompanionFeedbackHost(
    events: Flow<CompanionEvent>, page: Int, modifier: Modifier = Modifier,
    paused: Boolean = false, obscured: Boolean = false,
) {
    val appearance = LocalCompanionAppearance.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestObscured by rememberUpdatedState(obscured)
    val gate = remember { CompanionFeedbackGate() }
    var visible by remember { mutableStateOf<CompanionCue?>(null) }
    var lastCue by remember { mutableStateOf(CompanionCue.HELLO) }
    val allowed = appearance.showsFeedback && !paused && page != 2
    LaunchedEffect(page, allowed, lifecycle) {
        visible = null
        if (!allowed) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                events.filter { event ->
                    (event.page == null || event.page == page) &&
                        System.currentTimeMillis() - event.createdAtMillis in 0..1_500
                }.collectLatest { event ->
                    // A successful write may finish just before its dialog leaves composition.
                    // Keep only this latest event briefly; page/lifecycle changes cancel it.
                    val remaining = 1_500 - (System.currentTimeMillis() - event.createdAtMillis)
                    val ready = withTimeoutOrNull(remaining.coerceAtLeast(1)) {
                        snapshotFlow { latestObscured }.first { !it }
                        true
                    } ?: false
                    if (!ready || !gate.accept(event.cue, System.currentTimeMillis())) return@collectLatest
                    lastCue = event.cue
                    visible = event.cue
                    try { delay(1_800) } finally { visible = null }
                }
            } finally { visible = null }
        }
    }
    AnimatedVisibility(visible != null && allowed && !obscured, modifier,
        enter = if (appearance.motionEnabled) fadeIn(tween(140)) + slideInVertically { it / 5 } else EnterTransition.None,
        exit = if (appearance.motionEnabled) fadeOut(tween(120)) else ExitTransition.None) {
        val cue = visible ?: lastCue
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp) {
            Row(Modifier.padding(start = 8.dp, end = 18.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CompanionSticker(cue, Modifier.size(68.dp))
                Text(cue.label, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun CompanionWorking(isWorking: Boolean, modifier: Modifier = Modifier) {
    val appearance = LocalCompanionAppearance.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(isWorking, appearance.showsFeedback) {
        show = false
        if (isWorking && appearance.showsFeedback) { delay(800); show = true }
    }
    if (show) CompanionSticker(CompanionCue.THINKING, modifier.size(72.dp))
}

package win.iqwqi.xiangece.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import win.iqwqi.xiangece.ui.theme.LocalCompanionAppearance

/** Short, interruptible transitions. Compose also applies the system duration scale. */
@Composable
fun <T> AppScene(
    target: T, key: (T) -> String, depth: (T) -> Int,
    modifier: Modifier = Modifier, content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    val duration = if (LocalCompanionAppearance.current.motionEnabled) 240 else 0
    AnimatedContent(targetState = target, modifier = modifier, contentKey = key, label = "page-content",
        transitionSpec = {
            val direction = if (depth(targetState) >= depth(initialState)) 1 else -1
            (fadeIn(tween(duration)) + slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { direction * it / 12 })
                .togetherWith(fadeOut(tween(duration / 2)) + slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -direction * it / 16 })
                .using(SizeTransform(clip = false))
        }, content = content)
}

package win.iqwqi.xiangece.ui.companion

import androidx.compose.foundation.Image
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import win.iqwqi.xiangece.R
import win.iqwqi.xiangece.data.settings.CompanionOutfit
import win.iqwqi.xiangece.ui.theme.LocalCompanionAppearance

enum class CompanionCue(val label: String) {
    HELLO("来啦"), SAVED("收到"), HAPPY("好耶"), THINKING("我想想")
}

data class CompanionEvent(
    val cue: CompanionCue,
    val page: Int? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Composable
fun CompanionPortrait(
    outfit: CompanionOutfit, modifier: Modifier = Modifier, avatar: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit, alignment: Alignment = Alignment.Center,
    pose: CompanionPose? = null, motionEnabled: Boolean = false,
) {
    val resource = if (pose != null) companionPoseResource(outfit, pose, avatar) else when {
        avatar && outfit == CompanionOutfit.CAMPUS -> R.drawable.companion_avatar_campus
        avatar -> R.drawable.companion_avatar_academy
        outfit == CompanionOutfit.CAMPUS -> R.drawable.companion_campus
        else -> R.drawable.companion_academy
    }
    Crossfade(resource, modifier = modifier, animationSpec = tween(if (motionEnabled) 180 else 0), label = "portrait-expression") { image ->
        Image(painterResource(image), contentDescription = null, modifier = Modifier.fillMaxSize(),
            contentScale = contentScale, alignment = alignment)
    }
}

@Composable
fun CompanionSticker(
    cue: CompanionCue,
    modifier: Modifier = Modifier,
    outfit: CompanionOutfit = LocalCompanionAppearance.current.outfit,
) {
    val resource = if (outfit == CompanionOutfit.CAMPUS) {
        when (cue) {
            CompanionCue.HELLO -> R.drawable.companion_campus_hello
            CompanionCue.SAVED -> R.drawable.companion_campus_saved
            CompanionCue.HAPPY -> R.drawable.companion_campus_happy
            CompanionCue.THINKING -> R.drawable.companion_campus_thinking
        }
    } else {
        when (cue) {
            CompanionCue.HELLO -> R.drawable.companion_academy_hello
            CompanionCue.SAVED -> R.drawable.companion_academy_saved
            CompanionCue.HAPPY -> R.drawable.companion_academy_happy
            CompanionCue.THINKING -> R.drawable.companion_academy_thinking
        }
    }
    Image(painterResource(resource), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}

internal fun companionSceneResource(scene: CompanionOutfit, dark: Boolean): Int = when {
    scene == CompanionOutfit.CAMPUS && !dark -> R.drawable.companion_scene_campus_day
    scene == CompanionOutfit.CAMPUS -> R.drawable.companion_scene_campus_night
    !dark -> R.drawable.companion_scene_academy_day
    else -> R.drawable.companion_scene_academy_night
}

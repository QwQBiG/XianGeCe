package win.iqwqi.xiangece.ui.companion

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import win.iqwqi.xiangece.data.settings.CompanionAppearance

enum class CompanionPose(val label: String) {
    IDLE("静静陪伴"), WELCOME("向你招手"), HAPPY("开心加油"), CARING("温柔鼓励");

    fun next(): CompanionPose = entries[(ordinal + 1) % entries.size]
}

/** Local presentation state: taps never write to tasks or trigger completion events. */
@Stable
internal class CompanionInteraction {
    var pose by mutableStateOf(CompanionPose.IDLE)
        private set

    fun tap() { pose = pose.next() }
    fun reset() { pose = CompanionPose.IDLE }
}

@Composable
internal fun rememberCompanionInteraction(appearance: CompanionAppearance, active: Boolean): CompanionInteraction {
    val interaction = remember(appearance.outfit, appearance.display) { CompanionInteraction() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, interaction) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) interaction.reset()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active, appearance.showsFeedback) {
        if (!active || !appearance.showsFeedback) interaction.reset()
    }
    return interaction
}

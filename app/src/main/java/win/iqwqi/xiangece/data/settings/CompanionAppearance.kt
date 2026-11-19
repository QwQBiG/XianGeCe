package win.iqwqi.xiangece.data.settings

import kotlinx.serialization.Serializable

@Serializable
enum class CompanionOutfit(val label: String) {
    CAMPUS("校园"), ACADEMY("书院")
}

@Serializable
enum class CompanionDisplay(val label: String) {
    STANDARD("标准"), COMPACT("紧凑"), HIDDEN("隐藏")
}

@Serializable
enum class CompanionPalette { OUTFIT, CUSTOM }

/** Presentation preferences only; course and reminder settings remain independent. */
@Serializable
data class CompanionAppearance(
    val outfit: CompanionOutfit = CompanionOutfit.CAMPUS,
    val display: CompanionDisplay = CompanionDisplay.STANDARD,
    val scene: CompanionOutfit = CompanionOutfit.CAMPUS,
    val sceneFollowsOutfit: Boolean = true,
    val palette: CompanionPalette = CompanionPalette.OUTFIT,
    val feedbackEnabled: Boolean = true,
    val motionEnabled: Boolean = true,
) {
    val resolvedScene: CompanionOutfit get() = if (sceneFollowsOutfit) outfit else scene
    val showsFeedback: Boolean get() = display != CompanionDisplay.HIDDEN && feedbackEnabled

    fun withSceneLink(linked: Boolean): CompanionAppearance = copy(
        scene = resolvedScene,
        sceneFollowsOutfit = linked,
    )
}

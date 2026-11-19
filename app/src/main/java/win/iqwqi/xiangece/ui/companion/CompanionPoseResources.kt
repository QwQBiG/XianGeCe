package win.iqwqi.xiangece.ui.companion

import win.iqwqi.xiangece.R
import win.iqwqi.xiangece.data.settings.CompanionOutfit

internal fun companionPoseResource(outfit: CompanionOutfit, pose: CompanionPose, avatar: Boolean): Int =
    if (outfit == CompanionOutfit.CAMPUS) {
        if (avatar) when (pose) {
            CompanionPose.IDLE -> R.drawable.companion_avatar_campus_idle
            CompanionPose.WELCOME -> R.drawable.companion_avatar_campus_welcome
            CompanionPose.HAPPY -> R.drawable.companion_avatar_campus_happy
            CompanionPose.CARING -> R.drawable.companion_avatar_campus_caring
        } else when (pose) {
            CompanionPose.IDLE -> R.drawable.companion_home_campus_idle
            CompanionPose.WELCOME -> R.drawable.companion_home_campus_welcome
            CompanionPose.HAPPY -> R.drawable.companion_home_campus_happy
            CompanionPose.CARING -> R.drawable.companion_home_campus_caring
        }
    } else {
        if (avatar) when (pose) {
            CompanionPose.IDLE -> R.drawable.companion_avatar_academy_idle
            CompanionPose.WELCOME -> R.drawable.companion_avatar_academy_welcome
            CompanionPose.HAPPY -> R.drawable.companion_avatar_academy_happy
            CompanionPose.CARING -> R.drawable.companion_avatar_academy_caring
        } else when (pose) {
            CompanionPose.IDLE -> R.drawable.companion_home_academy_idle
            CompanionPose.WELCOME -> R.drawable.companion_home_academy_welcome
            CompanionPose.HAPPY -> R.drawable.companion_home_academy_happy
            CompanionPose.CARING -> R.drawable.companion_home_academy_caring
        }
    }

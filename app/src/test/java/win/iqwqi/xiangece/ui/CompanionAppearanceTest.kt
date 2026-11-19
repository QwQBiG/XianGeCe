package win.iqwqi.xiangece.ui

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import win.iqwqi.xiangece.data.settings.*
import win.iqwqi.xiangece.ui.companion.CompanionCue
import win.iqwqi.xiangece.ui.companion.CompanionFeedbackGate

class CompanionAppearanceTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun olderBackupKeepsTimetableAndThemePreferences() {
        val old = """{"themeSeed":"ochre","timetableRowHeightDp":118,"timetableColumnWidthDp":76,"timetableWallpaperPath":"saved-wallpaper.webp","followSystemTheme":true}"""
        val settings = json.decodeFromString<AppSettings>(old)
        assertEquals("ochre", settings.themeSeed)
        assertEquals(118, settings.timetableRowHeightDp)
        assertEquals(76, settings.timetableColumnWidthDp)
        assertEquals("saved-wallpaper.webp", settings.timetableWallpaperPath)
        assertTrue(settings.followSystemTheme)
        assertEquals(CompanionOutfit.CAMPUS, settings.companion.outfit)
        assertEquals(CompanionPalette.OUTFIT, settings.companion.palette)
    }

    @Test fun backupRoundTripIncludesIndependentOutfitAndScene() {
        val settings = AppSettings(themeSeed = "indigo", darkMode = true, companion = CompanionAppearance(
            outfit = CompanionOutfit.ACADEMY, scene = CompanionOutfit.CAMPUS,
            sceneFollowsOutfit = false, palette = CompanionPalette.CUSTOM,
            display = CompanionDisplay.COMPACT, feedbackEnabled = false, motionEnabled = false,
        ))
        assertEquals(settings, json.decodeFromString<AppSettings>(json.encodeToString(settings)))
    }

    @Test fun unlinkingPreservesVisibleSceneAcrossSubsequentOutfitChanges() {
        val linked = CompanionAppearance(outfit = CompanionOutfit.ACADEMY)
        val unlinked = linked.withSceneLink(false).copy(outfit = CompanionOutfit.CAMPUS)
        assertEquals(CompanionOutfit.ACADEMY, unlinked.resolvedScene)
        assertEquals(CompanionOutfit.CAMPUS, unlinked.withSceneLink(true).resolvedScene)
    }

    @Test fun hidingCharacterSuppressesFeedbackWithoutErasingPreferences() {
        val hidden = CompanionAppearance().copy(display = CompanionDisplay.HIDDEN)
        assertFalse(hidden.showsFeedback)
        assertTrue(hidden.copy(display = CompanionDisplay.STANDARD).showsFeedback)
    }

    @Test fun completionBurstDoesNotQueueCelebrationsOrSuppressSaveAcknowledgements() {
        val gate = CompanionFeedbackGate()
        assertTrue(gate.accept(CompanionCue.HAPPY, 1_000))
        assertFalse(gate.accept(CompanionCue.HAPPY, 1_100))
        assertFalse(gate.accept(CompanionCue.HAPPY, 30_999))
        assertTrue(gate.accept(CompanionCue.SAVED, 2_000))
        assertTrue(gate.accept(CompanionCue.HAPPY, 31_000))
    }
}

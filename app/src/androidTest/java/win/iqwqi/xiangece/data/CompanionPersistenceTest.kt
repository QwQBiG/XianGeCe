package win.iqwqi.xiangece.data

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import win.iqwqi.xiangece.core.backup.BackupManager
import win.iqwqi.xiangece.data.local.*
import win.iqwqi.xiangece.data.settings.*
import win.iqwqi.xiangece.feature.diting.audio.DitingAudioStore

/** Exercises production persistence against owned files and an in-memory database. */
@RunWith(AndroidJUnit4::class)
class CompanionPersistenceTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var job: CompletableJob
    private lateinit var preferences: DataStore<Preferences>
    private lateinit var settings: AppSettingsStore
    private lateinit var database: XiangeceDatabase
    private lateinit var backup: BackupManager
    private lateinit var notebook: win.iqwqi.xiangece.feature.notebook.data.NotebookRepository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    private val appearance = CompanionAppearance(outfit = CompanionOutfit.ACADEMY,
        display = CompanionDisplay.COMPACT, scene = CompanionOutfit.CAMPUS,
        sceneFollowsOutfit = false, palette = CompanionPalette.CUSTOM,
        feedbackEnabled = false, motionEnabled = false)

    @Before fun prepareIsolatedStorage() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        root = File(application.cacheDir, "companion-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(application) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        openSettings()
        database = Room.inMemoryDatabaseBuilder(context, XiangeceDatabase::class.java).build()
        notebook = win.iqwqi.xiangece.feature.notebook.data.NotebookRepository(context)
        backup = BackupManager(context, database.campusDao(), database.ditingDao(),
            DitingAudioStore(context), database, settings, json, notebook)
    }

    private fun openSettings() {
        job = SupervisorJob()
        preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
            File(root, "appearance.preferences_pb")
        }
        settings = AppSettingsStore(preferences)
    }

    @After fun closeIsolatedStorage() = runBlocking {
        job.cancelAndJoin()
        database.close()
        val parent = ApplicationProvider.getApplicationContext<Context>().cacheDir.canonicalFile
        check(root.canonicalFile.parentFile == parent && root.name.startsWith("companion-test-"))
        check(root.deleteRecursively())
    }

    private suspend fun populateSettings() {
        settings.updateGeneral(onboardingComplete = true, darkMode = true,
            followSystemTheme = true, courseReminderMinutes = 17)
        settings.updateThemeSeed("ochre")
        settings.updateTimetableLayout(72, 118, 24)
        settings.updateTimetableWallpaper("owned-wallpaper.webp")
        settings.updateTimetableBackgroundOptions(0.61f, false)
        settings.updateCompanion(appearance)
        settings.settings.first()
    }

    @Test fun wardrobeWritesOnlyItsKeyAndSurvivesClosingStorage() = runBlocking {
        populateSettings()
        val before = preferences.data.first().asMap()
        val chosen = appearance.copy(outfit = CompanionOutfit.CAMPUS, display = CompanionDisplay.HIDDEN)
        settings.updateCompanion(chosen)
        val after = preferences.data.first().asMap()
        assertEquals(before.filterKeys { it.name != "companion_appearance" },
            after.filterKeys { it.name != "companion_appearance" })
        val expected = settings.settings.first()
        job.cancelAndJoin()
        openSettings()
        assertEquals(expected, settings.settings.first())
        assertEquals(chosen, settings.settings.first().companion)
    }

    @Test fun malformedAppearanceFallsBackWithoutResettingTimetablePreferences() = runBlocking {
        populateSettings()
        preferences.edit { it[stringPreferencesKey("companion_appearance")] = "{" }
        val loaded = settings.settings.first()
        assertEquals(CompanionAppearance(), loaded.companion)
        assertEquals("ochre", loaded.themeSeed)
        assertEquals(72, loaded.timetableColumnWidthDp)
        assertEquals("owned-wallpaper.webp", loaded.timetableWallpaperPath)
    }

    private suspend fun exportFixture(): File {
        populateSettings()
        val dao = database.campusDao()
        dao.upsertTimetable(TimetableEntity(id = 1, name = "验证课表"))
        dao.upsertCourse(CourseEntity(id = 10, name = "课程长名称保留", teacher = "张老师"))
        dao.upsertMeeting(CourseMeetingEntity(id = 20, courseId = 10, dayOfWeek = 3,
            startPeriod = 2, endPeriod = 4, startWeek = 1, endWeek = 18, location = "教学楼 A101"))
        dao.upsertTasks(listOf(TaskEntity(id = 30, title = "验证任务", courseId = 10)))
        return File(root, "current.xiangece").also { backup.exportTo(Uri.fromFile(it)) }
    }

    private fun readPayload(file: File): JsonObject = ZipFile(file).use { zip ->
        json.parseToJsonElement(zip.getInputStream(zip.getEntry("backup.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }).jsonObject
    }

    private fun writePayload(name: String, payload: JsonObject): File = File(root, name).also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(payload.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    @Test fun completeBackupRestoresAppearanceAndCourseRecords() = runBlocking {
        val file = exportFixture()
        val expectedSettings = settings.settings.first()
        val dao = database.campusDao()
        val courses = dao.allCourses()
        val meetings = dao.allMeetings()
        val tasks = dao.allTasks()
        assertEquals("ACADEMY", readPayload(file)["settings"]!!.jsonObject["companion"]!!
            .jsonObject["outfit"]!!.jsonPrimitive.content)
        settings.updateCompanion(CompanionAppearance())
        settings.updateThemeSeed("pine")
        settings.updateTimetableLayout(46, 74, 16)
        dao.upsertCourse(courses.single().copy(name = "临时修改"))
        backup.restoreFrom(Uri.fromFile(file))
        assertEquals(expectedSettings, settings.settings.first())
        assertEquals(courses, dao.allCourses())
        assertEquals(meetings, dao.allMeetings())
        assertEquals(tasks, dao.allTasks())
    }

    @Test fun olderBackupWithoutAppearanceUsesDefaultsAndRetainsOtherSettings() = runBlocking {
        val payload = readPayload(exportFixture())
        val originalSettings = settings.settings.first()
        val oldSettings = JsonObject(payload["settings"]!!.jsonObject - "companion")
        val oldFile = writePayload("legacy.xiangece", JsonObject(payload + ("settings" to oldSettings)))
        settings.updateCompanion(appearance.copy(display = CompanionDisplay.HIDDEN))
        backup.restoreFrom(Uri.fromFile(oldFile))
        assertEquals(originalSettings.copy(companion = CompanionAppearance()), settings.settings.first())
        assertEquals("课程长名称保留", database.campusDao().allCourses().single().name)
    }

    @Test fun calendarNotesRoundTripAndLegacyBackupHasNoNotes() = runBlocking {
        val dao = database.campusDao()
        val note = CalendarNoteEntity(20706, "日历手记\n保留中文与换行")
        dao.upsertCalendarNotes(listOf(note))
        val file = exportFixture()
        assertEquals(1, readPayload(file)["calendarNotes"]!!.jsonArray.size)
        dao.clearCalendarNotes()
        backup.restoreFrom(Uri.fromFile(file))
        assertEquals(listOf(note), dao.allCalendarNotes())
        val legacy = writePayload("no-calendar.xiangece", JsonObject(readPayload(file) - "calendarNotes"))
        backup.restoreFrom(Uri.fromFile(legacy))
        assertTrue(dao.allCalendarNotes().isEmpty())
        assertEquals("验证任务", dao.allTasks().single().title)
    }

    @Test fun rejectedBackupDoesNotWriteSettingsOrCourses() = runBlocking {
        val payload = readPayload(exportFixture())
        val before = settings.settings.first()
        val courses = database.campusDao().allCourses()
        val invalid = writePayload("invalid.xiangece",
            JsonObject(payload + ("applicationId" to JsonPrimitive("different.application"))))
        try {
            backup.restoreFrom(Uri.fromFile(invalid))
            fail("Invalid backup was accepted")
        } catch (expected: IllegalArgumentException) {
            assertEquals("备份不属于弦歌册", expected.message)
        }
        assertEquals(before, settings.settings.first())
        assertEquals(courses, database.campusDao().allCourses())
    }
    @Test fun notebookBackupMergesWithoutOverwritingNewerNotes() = runBlocking {
        val original = notebook.store.create("备份笔记", "学习", "原来的内容 #数学")
        val file = exportFixture()
        ZipFile(file).use { assertNotNull(it.getEntry("notebook.zip")) }
        notebook.store.save(original.record.id, "备份之后的新内容", original.revision)
        assertNull(backup.restoreFrom(Uri.fromFile(file)))
        val restored = notebook.store.list()
        assertEquals(2, restored.size)
        assertEquals("备份之后的新内容", notebook.store.read(original.record.id).text)
        assertTrue(restored.any { it.text == "原来的内容 #数学" && it.record.folder.startsWith("导入-") })
    }
}

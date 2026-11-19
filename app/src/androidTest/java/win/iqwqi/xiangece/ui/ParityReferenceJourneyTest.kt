package win.iqwqi.xiangece.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import win.iqwqi.xiangece.MainActivity
import win.iqwqi.xiangece.core.backup.BackupPayload
import win.iqwqi.xiangece.data.local.*
import win.iqwqi.xiangece.data.settings.*
import win.iqwqi.xiangece.feature.notebook.data.NotebookArchive
import win.iqwqi.xiangece.feature.notebook.data.NotebookFileStore

/** Real original app + local Room/Markdown. Every fixture is explicitly labelled validation data. */
@RunWith(AndroidJUnit4::class)
class ParityReferenceJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun captureOriginalPagesAndKeepMarkdownOriginalBytes() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(semesterName).fetchSemanticsNodes().isNotEmpty() }
        val homeHash = capture("01-today-campus")
        compose.onNodeWithText("换装").performClick()
        waitForWardrobe()
        capture("02-wardrobe-campus", differentFrom = homeHash)
        compose.onNodeWithText("书院").performScrollTo().performClick()
        compose.onNodeWithText("应用装扮").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("角色衣橱").fetchSemanticsNodes().isEmpty() }
        capture("03-today-academy")
        compose.onNode(hasText("课程") and hasClickAction()).performClick()
        compose.onNodeWithText(courseName).assertIsDisplayed()
        capture("04-weekly-timetable")
        val bottom = WindowInsetsCompat.toWindowInsetsCompat(compose.activity.window.decorView.rootWindowInsets)
            .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val stripOffset = with(compose.density) { 5.dp.toPx() }
        compose.onRoot().performTouchInput { click(Offset(width/2f, height-bottom-stripOffset)) }
        compose.onNode(hasText("百宝") and hasClickAction()).performClick()
        compose.onNodeWithText("日历日程").performClick()
        compose.onNodeWithText("日历日程").assertIsDisplayed()
        capture("05-calendar-month")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("笔记库").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("notebook-list").fetchSemanticsNodes().isNotEmpty() }
        capture("06-notebook-library")
        compose.onNodeWithTag("notebook-list").performScrollToNode(hasText(noteTitle) and !hasSetTextAction())
        compose.onNode(hasText(noteTitle) and !hasSetTextAction()).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("预览").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(isSelected() and hasText("编辑")).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasSetTextAction() and hasText(noteBody)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction() and hasText(noteBody)).assertIsDisplayed().assertTextEquals(noteBody)
        capture("07-notebook-editor")
        compose.onNodeWithText("预览").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("已保存").fetchSemanticsNodes().isNotEmpty() }
        waitForRealMarkdownPreview()
        capture("08-markdown-preview")
        val context=ApplicationProvider.getApplicationContext<Context>()
        val current=NotebookFileStore(File(context.filesDir,"notebook")).read(noteId).text
        assertEquals(noteHash,sha256(current.toByteArray(Charsets.UTF_8)))
        File(output,"markdown-sha256-after.txt").writeText(sha256(current.toByteArray(Charsets.UTF_8))+"\n")
        compose.onNodeWithContentDescription("返回笔记库").performClick()
        pressBack()
        sourceFiles.forEach { (file, hash) -> assertEquals("Original reference changed: ${file.name}", hash, sha256(file.readBytes())) }
    }

    private fun waitForWardrobe() {
        compose.onNodeWithText("角色衣橱").assertIsDisplayed()
        compose.onNodeWithTag("wardrobe-scroll").assertIsDisplayed()
        compose.onNodeWithText("应用装扮").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(800)
        var previous = compose.onNodeWithTag("full-screen-sheet").fetchSemanticsNode().boundsInRoot
        var stableSince = SystemClock.uptimeMillis()
        compose.waitUntil(10_000) {
            val bounds = compose.onNodeWithTag("full-screen-sheet").fetchSemanticsNode().boundsInRoot
            if (bounds != previous) { previous = bounds; stableSince = SystemClock.uptimeMillis() }
            bounds.width > 0 && bounds.height > 0 && SystemClock.uptimeMillis() - stableSince >= 300
        }
        compose.onNodeWithText("角色衣橱").assertIsDisplayed()
        compose.onNodeWithText("应用装扮").assertIsDisplayed()
    }

    private fun waitForRealMarkdownPreview() {
        compose.waitUntil(15_000) { visibleWindowWebView() != null }
        val web = checkNotNull(visibleWindowWebView())
        compose.runOnIdle { assertFalse("Reference preview keeps production JavaScript policy", web.settings.javaScriptEnabled) }
        var actualText = ""
        try {
            compose.waitUntil(15_000) {
                actualText = webAccessibilityText()
                actualText.contains("中文粗体") && actualText.contains("表格预览")
            }
        } finally { File(output, "08-webview-readiness.txt").writeText(actualText) }
        val ready = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            web.postVisualStateCallback(SystemClock.uptimeMillis(), object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { ready.countDown() }
            })
        }
        assertTrue("Rendered Markdown visual state was not ready", ready.await(5, TimeUnit.SECONDS))
    }

    /** NotebookEditor lives in a dialog, outside the Activity decorView hierarchy. */
    private fun visibleWindowWebView(): WebView? {
        var result: WebView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val roots = WindowInspector.getGlobalWindowViews().filter { it.isShown && it.isAttachedToWindow }
            // Prefer the current focused dialog, then inspect every other visible app window.
            val ordered = roots.filter { it.hasWindowFocus() }.asReversed() + roots.filterNot { it.hasWindowFocus() }.asReversed()
            result = ordered.firstNotNullOfOrNull(::findWebView)
        }
        return result
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view.takeIf { it.isShown && it.isAttachedToWindow && it.width > 0 && it.height > 0 }
        is ViewGroup -> if (view.isShown && view.isAttachedToWindow)
            (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) } else null
        else -> null
    }

    private fun webAccessibilityText(): String = buildString {
        fun visit(node: AccessibilityNodeInfo, insideWeb: Boolean) {
            try {
                val isWeb = insideWeb || node.className?.toString() == "android.webkit.WebView"
                if (isWeb) node.text?.let { append(it); append('\n') }
                for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it, isWeb) }
            } finally { node.recycle() }
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.let { visit(it, false) }
    }

    /** Compose idleness alone does not wait for the dialog window's rendered buffer. */
    private fun awaitCommittedWindowFrames() {
        check(Build.VERSION.SDK_INT >= 29) { "Reference capture requires frame-commit callbacks (API 29+)" }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(2) {
            val committed = CountDownLatch(1)
            instrumentation.runOnMainSync {
                val root = checkNotNull(WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it.hasWindowFocus() }) { "No focused reference window" }
                check(root.isHardwareAccelerated) { "Reference window cannot report hardware frame commits" }
                root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                root.postInvalidateOnAnimation()
            }
            assertTrue("Reference window did not commit its rendered frame", committed.await(5, TimeUnit.SECONDS))
        }
    }

    private fun capture(name: String, differentFrom: String? = null): String {
        compose.waitForIdle()
        awaitCommittedWindowFrames()
        val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val bytes = try { ByteArrayOutputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)); it.toByteArray() } }
            finally { bitmap.recycle() }
        val hash = sha256(bytes)
        check(differentFrom == null || hash != differentFrom) { "$name is the unchanged prior screen; refusing invalid reference evidence" }
        File(output,"$name.png").writeBytes(bytes)
        return hash
    }

    companion object {
        private lateinit var output:File
        private lateinit var semesterName:String
        private lateinit var courseName:String
        private lateinit var noteTitle:String
        private lateinit var noteId:String
        private lateinit var noteHash:String
        private lateinit var noteBody:String
        private var sourceFiles:Map<File,String> = emptyMap()
        private fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}

        @JvmStatic @BeforeClass fun prepareRealLocalValidationData()=runBlocking {
            val context=ApplicationProvider.getApplicationContext<Context>()
            val run=System.currentTimeMillis()
            val sourceRun = InstrumentationRegistry.getArguments().getString("xiangece.reference.sourceRun")
            if (sourceRun != null) {
                require(sourceRun.matches(Regex("[0-9]{13}"))) { "Invalid reference source run" }
                reuseOriginalReference(context, sourceRun, run)
                return@runBlocking
            }
            output=File(context.getExternalFilesDir(null),"parity-reference/run-$run").apply{check(mkdirs())}
            semesterName="验证数据·秋季学期";courseName="验证数据·数学分析";noteTitle="验证数据·Markdown-${run.toString().takeLast(4)}"
            val today=LocalDate.now();val monday=today.minusDays((today.dayOfWeek.value-1).toLong())
            val settings=AppSettingsStore(context)
            settings.updateGeneral(onboardingComplete=true,darkMode=false,followSystemTheme=false,notificationPermissionAsked=true)
            settings.updateCompanion(CompanionAppearance(motionEnabled=false))
            val periods=(1..16).map{PeriodTemplateEntity(it,480+(it-1)*55,525+(it-1)*55)}
            val semester=SemesterEntity(run,semesterName,monday.toEpochDay(),20)
            val timetable=TimetableEntity(run,"验证数据·主课表",run,true)
            val courses=listOf(CourseEntity(run,run,courseName,"验证数据·教师","A101",0xFF52796F),CourseEntity(run+1,run,"验证数据·程序设计","验证数据·教师","B202",0xFF4C6A92))
            val meetings=listOf(CourseMeetingEntity(run,run,run,1,1,2,1,20,location="A101"),CourseMeetingEntity(run+1,run,run+1,4,3,4,1,20,location="B202"))
            val tasks=listOf(TaskEntity(run,"验证数据·提交报告",run,today.plusDays(1).atTime(18,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),note="仅用于跨平台对照"),TaskEntity(run+1,"验证数据·无日期待办"))
            val events=listOf(CampusEventEntity(run,"验证数据·读书讨论",today.plusDays(2).atTime(14,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),location="图书馆"))
            val notes=listOf(CalendarNoteEntity(today.toEpochDay(),"验证数据·当天手记"))
            val database=Room.databaseBuilder(context,XiangeceDatabase::class.java,"xiangece.db").build()
            try {
                val dao=database.campusDao()
                database.withTransaction {
                    dao.clearCurrentSemester();dao.upsertSemester(semester)
                    dao.clearCurrentTimetable();dao.upsertTimetable(timetable)
                    dao.upsertPeriods(periods);dao.upsertCourses(courses);dao.upsertMeetings(meetings)
                    dao.upsertTasks(tasks);dao.upsertEvents(events);dao.upsertCalendarNotes(notes)
                }
            } finally { database.close() }
            val store=NotebookFileStore(File(context.filesDir,"notebook"))
            val body="# 验证数据·Markdown\n\n[跳到章节](#section)\n\n**中文粗体**与普通文字。\n\n| 项目 | 说明 |\n| --- | --- |\n| 验证数据 | 表格预览 |\n\n```kotlin\nval title = \"弦歌册\"\n```\n\n"+
                (1..18).joinToString("\n\n"){"验证数据·目录前的第 $it 段。"}+"\n\n<a id=\"section\"></a>\n## 章节\n\n验证数据·锚点目标。\n"
            val document=store.create(noteTitle,"验证数据",body);noteId=document.record.id;noteHash=sha256(body.toByteArray(Charsets.UTF_8))
            noteBody=body
            File(output,"markdown-sha256-before.txt").writeText(noteHash+"\n")
            File(output,"fixture.json").writeText("""{"label":"验证数据","run":$run,"date":"$today","zone":"${ZoneId.systemDefault()}","semester":"$semesterName","note":"$noteTitle","noteId":"$noteId","productionCodeChanged":false}""")
            val payload=BackupPayload(settings=settings.settings.first().copy(aiEnabled=false,encryptedApiKey=""),semesters=listOf(semester),timetables=listOf(timetable),periods=periods,courses=courses,meetings=meetings,tasks=tasks,events=events,calendarNotes=notes,inbox=emptyList(),ocrSnapshots=emptyList(),grades=emptyList(),gradeRules=emptyList(),reminders=emptyList())
            File(output,"验证数据-Android参考.xiangece").outputStream().use{raw->ZipOutputStream(raw).use{zip->
                zip.putNextEntry(ZipEntry("backup.json"));zip.write(Json{encodeDefaults=true}.encodeToString(payload).toByteArray(Charsets.UTF_8));zip.closeEntry()
                val notebook=File(context.cacheDir,"parity-notebook-$run.zip")
                notebook.outputStream().use{NotebookArchive.export(store,it)}
                zip.putNextEntry(ZipEntry("notebook.zip"));notebook.inputStream().use{it.copyTo(zip)};zip.closeEntry()
            }}
        }

        private suspend fun reuseOriginalReference(context: Context, sourceRun: String, run: Long) {
            val source = File(context.getExternalFilesDir(null), "parity-reference/run-$sourceRun")
            val fixture = Json.parseToJsonElement(File(source, "fixture.json").readText()).jsonObject
            fun field(key: String) = checkNotNull(fixture[key]?.jsonPrimitive?.contentOrNull) { "Original fixture lacks $key" }
            check(field("run") == sourceRun) { "Original fixture run mismatch" }
            sourceFiles = checkNotNull(source.listFiles()).filter(File::isFile).associateWith { sha256(it.readBytes()) }
            val archive = File(source, "验证数据-Android参考.xiangece")
            val payload = ZipFile(archive).use { zip ->
                val entry = checkNotNull(zip.getEntry("backup.json")) { "Original backup.json missing" }
                Json { ignoreUnknownKeys = true }.decodeFromString<BackupPayload>(zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
            check(context.getDatabasePath("xiangece.db").isFile) { "Original Room database missing; no replacement was created" }
            val database = Room.databaseBuilder(context, XiangeceDatabase::class.java, "xiangece.db").build()
            try {
                val dao = database.campusDao()
                assertEquals("Original current semester differs; not restoring over local data", payload.semesters.single { it.isCurrent }, dao.currentSemester())
                assertEquals("Original current timetable differs", payload.timetables.single { it.isCurrent }, dao.currentTimetable())
                assertEquals(payload.periods.sortedBy { it.periodIndex }, dao.allPeriods().sortedBy { it.periodIndex })
                assertEquals(payload.courses.sortedBy { it.id }, dao.allCourses().sortedBy { it.id })
                assertEquals(payload.meetings.sortedBy { it.id }, dao.allMeetings().sortedBy { it.id })
                assertEquals(payload.tasks.sortedBy { it.id }, dao.allTasks().sortedBy { it.id })
                assertEquals(payload.events.sortedBy { it.id }, dao.allEvents().sortedBy { it.id })
                assertEquals(payload.calendarNotes.sortedBy { it.dateEpochDay }, dao.allCalendarNotes().sortedBy { it.dateEpochDay })
            } finally { database.close() }
            noteId = field("noteId"); noteTitle = field("note"); semesterName = field("semester")
            courseName = payload.courses.first().name
            check(File(context.filesDir, "notebook/state/index.json").isFile) { "Original notebook index missing; no replacement was created" }
            val store = NotebookFileStore(File(context.filesDir, "notebook"))
            val document = store.read(noteId)
            noteHash = File(source, "markdown-sha256-before.txt").readText().trim()
            assertEquals(noteTitle, document.record.title)
            assertEquals("验证数据", document.record.folder)
            assertEquals("Original local Markdown differs; no replacement was created", noteHash, sha256(document.text.toByteArray(Charsets.UTF_8)))
            check(store.draft(noteId) == null) { "Original note has an unsaved draft; refusing to alter it" }
            noteBody = document.text
            val settings = AppSettingsStore(context)
            settings.updateGeneral(onboardingComplete = true, darkMode = false, followSystemTheme = false, notificationPermissionAsked = true)
            settings.updateCompanion(payload.settings.companion)
            output = File(context.getExternalFilesDir(null), "parity-reference-recapture/source-$sourceRun/run-$run").apply { check(mkdirs()) }
            File(output, "reuse-boundary.txt").writeText("sourceRun=$sourceRun\nsourceDate=${field("date")}\nactualDate=${LocalDate.now()}\nactualZone=${ZoneId.systemDefault()}\nRoom and original Markdown matched before reuse. No Room restore, source rewrite, backup export, product change, or system-clock change. Dates are not claimed to be the same visual state.\n")
            File(output, "source-sha256.txt").writeText(sourceFiles.entries.joinToString("\n") { "${it.value}  ${it.key.name}" } + "\n")
        }
    }
}

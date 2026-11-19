package win.iqwqi.xiangece.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import win.iqwqi.xiangece.data.local.*
import win.iqwqi.xiangece.domain.model.TaskStatus
import win.iqwqi.xiangece.domain.parser.CampusTextParser

@RunWith(AndroidJUnit4::class)
class CalendarDataTest {
    private lateinit var db: XiangeceDatabase
    private lateinit var repository: CampusRepository
    private val dao get() = db.campusDao()
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), XiangeceDatabase::class.java).build()
        repository = CampusRepository(dao, db, CampusTextParser(), Json { ignoreUnknownKeys = true })
    }
    @After fun close() { db.close() }

    @Test fun notesCanBeEditedRemovedAndRejectInvalidDates() = runBlocking {
        repository.saveCalendarNote(20706, "今天的想法")
        repository.saveCalendarNote(20707, "明天")
        repository.saveCalendarNote(20706, "新的想法")
        assertEquals("新的想法", repository.calendarNotes.first().first().text)
        repository.saveCalendarNote(20706, " \n ")
        assertEquals(listOf(20707L), dao.allCalendarNotes().map { it.dateEpochDay })
        assertTrue(runCatching { repository.saveCalendarNote(Long.MAX_VALUE, "invalid") }.isFailure)
        assertTrue(runCatching { repository.saveCalendarNote(20707, "x".repeat(10_001)) }.isFailure)
        assertEquals("明天", dao.allCalendarNotes().single().text)
    }

    @Test fun taskObserversShareEditsCompletionDeleteAndRestore() = runBlocking {
        val id = repository.createTask("同步待办", 123456789L, "备注")
        val task = repository.tasks.first().single()
        assertEquals(id, task.id)
        repository.updateTask(task, "已改标题", 987654321L, "改备注")
        val edited = repository.tasks.first().single()
        assertEquals("已改标题", edited.title)
        assertEquals(987654321L, edited.dueAtEpochMillis)
        repository.toggleTask(edited)
        val done = repository.tasks.first().single()
        assertEquals(TaskStatus.DONE, done.status)
        repository.deleteTask(id)
        assertTrue(repository.tasks.first().isEmpty())
        repository.restoreTask(done)
        assertEquals(done, repository.tasks.first().single())
    }

    @Test fun deletingCurrentTimetableRemovesItsCoursesButKeepsTasksAndSource() = runBlocking {
        val first = repository.createTimetable("保留课表")
        val keep = dao.upsertCourse(CourseEntity(timetableId = first, name = "保留课程"))
        val target = repository.createTimetable("待删除课表")
        val course = dao.upsertCourse(CourseEntity(timetableId = target, name = "待删除课程"))
        val meeting = dao.upsertMeeting(CourseMeetingEntity(timetableId = target, courseId = course,
            dayOfWeek = 1, startPeriod = 1, endPeriod = 2, startWeek = 1, endWeek = 20))
        val inbox = dao.upsertInbox(InboxItemEntity(timetableId = target, sourceType = "text", originalText = "保留原文"))
        val task = dao.upsertTask(TaskEntity(title = "保留待办", courseId = course, sourceInboxId = inbox))
        val deletedTask = TaskEntity(id = 800, title = "等待撤销", courseId = course)
        db.ditingDao().upsertSessions(listOf(win.iqwqi.xiangece.feature.diting.data.DitingSessionEntity(
            id = 50, title = "保留录音", meetingId = meeting)))
        val ids = repository.deleteTimetable(target)
        assertEquals(listOf(meeting), ids)
        assertEquals(first, dao.currentTimetable()!!.id)
        assertNull(dao.courseById(course))
        assertNull(dao.meetingById(meeting))
        assertNotNull(dao.courseById(keep))
        assertNull(dao.taskById(task)!!.courseId)
        assertEquals(inbox, dao.taskById(task)!!.sourceInboxId)
        assertEquals("保留原文", dao.allInbox().single().originalText)
        assertEquals(first, dao.allInbox().single().timetableId)
        assertNull(db.ditingDao().allSessions().single().meetingId)
        assertEquals("保留录音", db.ditingDao().allSessions().single().title)
        repository.restoreTask(deletedTask)
        assertNull(dao.taskById(800)!!.courseId)
        assertEquals("等待撤销", dao.taskById(800)!!.title)
    }

    @Test fun deletingInactiveAndLastBookKeepsExactlyOneCurrentBook() = runBlocking {
        val inactive = repository.createTimetable("旧课表")
        val active = repository.createTimetable("当前课表")
        repository.deleteTimetable(inactive)
        assertEquals(active, dao.currentTimetable()!!.id)
        repository.deleteTimetable(active)
        val replacement = dao.allTimetables().single()
        assertTrue(replacement.isCurrent)
        assertTrue(replacement.id != active)
        assertEquals("默认课表", replacement.name)
        repository.deleteTimetable(-99)
        assertEquals(replacement, dao.allTimetables().single())
    }
}

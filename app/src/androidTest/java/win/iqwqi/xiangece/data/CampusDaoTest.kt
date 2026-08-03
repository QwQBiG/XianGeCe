package win.iqwqi.xiangece.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import win.iqwqi.xiangece.data.local.CourseEntity
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.XiangeceDatabase
import win.iqwqi.xiangece.data.local.ReminderEntity

@RunWith(AndroidJUnit4::class)
class CampusDaoTest {
    private lateinit var database: XiangeceDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            XiangeceDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun courseAndMeetingAreObservedFromSingleSource() = runTest {
        val courseId = database.campusDao().upsertCourse(CourseEntity(name = "数据结构"))
        database.campusDao().upsertMeeting(
            CourseMeetingEntity(
                courseId = courseId,
                dayOfWeek = 3,
                startPeriod = 3,
                endPeriod = 4,
                startWeek = 1,
                endWeek = 18,
            ),
        )

        assertEquals("数据结构", database.campusDao().observeCourses().first().single().name)
        assertEquals(courseId, database.campusDao().observeMeetings().first().single().courseId)
    }

    @Test
    fun remindersCanBeRemovedByTargetWithoutTouchingOthers() = runTest {
        database.campusDao().upsertReminder(
            ReminderEntity(
                targetType = "task",
                targetId = 1,
                triggerAtEpochMillis = System.currentTimeMillis() + 60_000,
                title = "任务一",
                body = "",
                channel = "task_reminders",
            ),
        )
        database.campusDao().upsertReminder(
            ReminderEntity(
                targetType = "task",
                targetId = 2,
                triggerAtEpochMillis = System.currentTimeMillis() + 60_000,
                title = "任务二",
                body = "",
                channel = "task_reminders",
            ),
        )

        database.campusDao().deleteRemindersForTarget("task", 1)

        assertEquals(0, database.campusDao().remindersForTarget("task", 1).size)
        assertEquals(1, database.campusDao().remindersForTarget("task", 2).size)
    }

    @Test
    fun reminderEnabledStateIsObserved() = runTest {
        val dao = database.campusDao()
        val id = dao.upsertReminder(
            ReminderEntity(
                targetType = "event",
                targetId = 8,
                triggerAtEpochMillis = System.currentTimeMillis() + 60_000,
                title = "讲座",
                body = "即将开始",
                channel = "event_reminders",
            ),
        )

        val initial = dao.observeReminders().first().single()
        dao.upsertReminder(initial.copy(enabled = false))

        assertEquals(id, initial.id)
        assertEquals(false, dao.observeReminders().first().single().enabled)
    }
}

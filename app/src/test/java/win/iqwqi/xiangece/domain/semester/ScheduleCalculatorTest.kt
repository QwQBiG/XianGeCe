package win.iqwqi.xiangece.domain.semester

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.PeriodTemplateEntity
import win.iqwqi.xiangece.data.local.SemesterEntity
import win.iqwqi.xiangece.domain.model.WeekParity

class ScheduleCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val semester = SemesterEntity(
        id = 1,
        name = "测试学期",
        startDateEpochDay = LocalDate.of(2026, 9, 7).toEpochDay(),
        weekCount = 4,
    )
    private val periods = listOf(PeriodTemplateEntity(1, 8 * 60, 8 * 60 + 45))

    @Test
    fun findsNextOccurrenceAndHonorsOddWeeks() {
        val meeting = CourseMeetingEntity(
            id = 3,
            courseId = 2,
            dayOfWeek = 1,
            startPeriod = 1,
            endPeriod = 2,
            startWeek = 1,
            endWeek = 4,
            weekParity = WeekParity.ODD,
        )
        val now = LocalDateTime.of(2026, 9, 7, 9, 0).atZone(zone).toInstant().toEpochMilli()

        val result = ScheduleCalculator.nextCourse(listOf(meeting), semester, periods, now, zone)

        assertEquals(3, result?.teachingWeek)
        assertEquals(LocalDate.of(2026, 9, 21), result?.startsAtEpochMillis?.toDate(zone))
    }

    @Test
    fun returnsNullAfterSemester() {
        val meeting = CourseMeetingEntity(
            courseId = 2,
            dayOfWeek = 1,
            startPeriod = 1,
            endPeriod = 2,
            startWeek = 1,
            endWeek = 4,
        )
        val now = LocalDateTime.of(2027, 1, 1, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertNull(ScheduleCalculator.nextCourse(listOf(meeting), semester, periods, now, zone))
    }

    @Test
    fun detectsOverdueOnlyWhenDueTimeExists() {
        assertTrue(ScheduleCalculator.isOverdue(99, 100))
        assertEquals(false, ScheduleCalculator.isOverdue(null, 100))
    }
}

private fun Long.toDate(zoneId: ZoneId): LocalDate =
    java.time.Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

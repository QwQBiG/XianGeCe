package win.iqwqi.xiangece.domain.semester

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.SemesterEntity
import win.iqwqi.xiangece.domain.model.WeekParity

class ReminderTimeCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun offsetTriggersAreDeduplicatedSortedAndFutureOnly() {
        val now = 1_000_000_000L
        val target = now + 30 * 3_600_000L

        val result = ReminderTimeCalculator.offsetTriggers(
            targetEpochMillis = target,
            hoursBefore = listOf(2, 24, 2, 48),
            nowEpochMillis = now,
        )

        assertEquals(listOf(24, 2), result.map { it.first })
        assertTrue(result.all { it.second > now })
    }

    @Test
    fun courseTriggersRespectOddWeeksAndReminderOffset() {
        val semester = SemesterEntity(
            name = "测试学期",
            startDateEpochDay = LocalDate.of(2026, 9, 9).toEpochDay(),
            weekCount = 4,
        )
        val meeting = CourseMeetingEntity(
            courseId = 1,
            dayOfWeek = 3,
            startPeriod = 1,
            endPeriod = 2,
            startWeek = 1,
            endWeek = 4,
            weekParity = WeekParity.ODD,
        )
        val now = LocalDateTime.of(2026, 9, 1, 0, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val result = ReminderTimeCalculator.courseTriggers(
            meeting = meeting,
            semester = semester,
            startMinutes = 8 * 60,
            minutesBefore = 20,
            nowEpochMillis = now,
            zoneId = zone,
        )

        assertEquals(2, result.size)
        assertEquals(
            LocalDateTime.of(2026, 9, 9, 7, 40).atZone(zone).toInstant().toEpochMilli(),
            result.first(),
        )
        assertEquals(
            LocalDateTime.of(2026, 9, 23, 7, 40).atZone(zone).toInstant().toEpochMilli(),
            result.last(),
        )
    }

    @Test
    fun invalidCourseTimeProducesNoAlarm() {
        val result = ReminderTimeCalculator.courseTriggers(
            meeting = CourseMeetingEntity(
                courseId = 1,
                dayOfWeek = 8,
                startPeriod = 1,
                endPeriod = 1,
                startWeek = 1,
                endWeek = 2,
            ),
            semester = SemesterEntity(
                name = "测试学期",
                startDateEpochDay = LocalDate.of(2026, 9, 7).toEpochDay(),
                weekCount = 20,
            ),
            startMinutes = 8 * 60,
            minutesBefore = 20,
            nowEpochMillis = 0,
            zoneId = zone,
        )

        assertTrue(result.isEmpty())
    }
}

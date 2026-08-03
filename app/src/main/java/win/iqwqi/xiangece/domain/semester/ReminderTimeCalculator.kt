package win.iqwqi.xiangece.domain.semester

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.SemesterEntity
import win.iqwqi.xiangece.domain.model.WeekParity

object ReminderTimeCalculator {
    fun offsetTriggers(
        targetEpochMillis: Long,
        hoursBefore: List<Int>,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): List<Pair<Int, Long>> = hoursBefore
        .map { it.coerceAtLeast(0) }
        .distinct()
        .map { hours -> hours to targetEpochMillis - hours * 3_600_000L }
        .filter { (_, trigger) -> trigger > nowEpochMillis }
        .sortedBy { (_, trigger) -> trigger }

    fun courseTriggers(
        meeting: CourseMeetingEntity,
        semester: SemesterEntity,
        startMinutes: Int,
        minutesBefore: Int,
        nowEpochMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<Long> {
        if (meeting.dayOfWeek !in 1..7 || startMinutes !in 0 until 24 * 60) return emptyList()
        val semesterMonday = LocalDate.ofEpochDay(semester.startDateEpochDay)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val time = LocalTime.of(startMinutes / 60, startMinutes % 60)
        return (meeting.startWeek.coerceAtLeast(1)..meeting.endWeek.coerceAtMost(semester.weekCount))
            .filter { week -> meeting.activeIn(week) }
            .map { week ->
                semesterMonday
                    .plusWeeks((week - 1).toLong())
                    .plusDays((meeting.dayOfWeek - 1).toLong())
                    .atTime(time)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli() - minutesBefore.coerceAtLeast(0) * 60_000L
            }
            .filter { it > nowEpochMillis }
    }

    private fun CourseMeetingEntity.activeIn(week: Int): Boolean =
        week in startWeek..endWeek && when (weekParity) {
            WeekParity.ALL -> true
            WeekParity.ODD -> week % 2 == 1
            WeekParity.EVEN -> week % 2 == 0
        }
}

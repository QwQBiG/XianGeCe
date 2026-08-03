package win.iqwqi.xiangece.domain.semester

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.PeriodTemplateEntity
import win.iqwqi.xiangece.data.local.SemesterEntity
import win.iqwqi.xiangece.domain.model.WeekParity

data class CourseOccurrence(
    val meeting: CourseMeetingEntity,
    val teachingWeek: Int,
    val startsAtEpochMillis: Long,
)

object ScheduleCalculator {
    fun nextCourse(
        meetings: List<CourseMeetingEntity>,
        semester: SemesterEntity?,
        periods: List<PeriodTemplateEntity>,
        nowEpochMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): CourseOccurrence? {
        semester ?: return null
        val semesterMonday = LocalDate.ofEpochDay(semester.startDateEpochDay)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return meetings.asSequence()
            .flatMap { meeting ->
                val period = periods.firstOrNull { it.periodIndex == meeting.startPeriod }
                    ?: return@flatMap emptySequence()
                (meeting.startWeek..meeting.endWeek.coerceAtMost(semester.weekCount))
                    .asSequence()
                    .filter { week -> meeting.activeIn(week) }
                    .map { week ->
                        val date = semesterMonday
                            .plusWeeks((week - 1).toLong())
                            .plusDays((meeting.dayOfWeek - 1).toLong())
                        val time = LocalTime.of(period.startMinutes / 60, period.startMinutes % 60)
                        CourseOccurrence(
                            meeting = meeting,
                            teachingWeek = week,
                            startsAtEpochMillis = date.atTime(time)
                                .atZone(zoneId)
                                .toInstant()
                                .toEpochMilli(),
                        )
                    }
            }
            .filter { it.startsAtEpochMillis >= nowEpochMillis }
            .minByOrNull { it.startsAtEpochMillis }
    }

    fun isOverdue(dueAtEpochMillis: Long?, nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        dueAtEpochMillis != null && dueAtEpochMillis < nowEpochMillis

    private fun CourseMeetingEntity.activeIn(week: Int): Boolean =
        week in startWeek..endWeek && when (weekParity) {
            WeekParity.ALL -> true
            WeekParity.ODD -> week % 2 == 1
            WeekParity.EVEN -> week % 2 == 0
        }
}

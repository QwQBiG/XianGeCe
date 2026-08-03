package win.iqwqi.xiangece.domain.semester

import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.domain.model.WeekParity

object CourseConflictDetector {
    fun conflicts(
        candidate: CourseMeetingEntity,
        existing: List<CourseMeetingEntity>,
    ): List<CourseMeetingEntity> = existing.filter { other ->
        other.id != candidate.id && overlaps(candidate, other)
    }

    fun overlaps(first: CourseMeetingEntity, second: CourseMeetingEntity): Boolean {
        if (first.dayOfWeek != second.dayOfWeek) return false
        if (first.endPeriod < second.startPeriod || second.endPeriod < first.startPeriod) return false
        val firstWeek = maxOf(first.startWeek, second.startWeek)
        val lastWeek = minOf(first.endWeek, second.endWeek)
        if (firstWeek > lastWeek) return false
        return (firstWeek..lastWeek).any { week ->
            first.weekParity.includes(week) && second.weekParity.includes(week)
        }
    }

    fun activeInWeek(meeting: CourseMeetingEntity, week: Int): Boolean =
        week in meeting.startWeek..meeting.endWeek && meeting.weekParity.includes(week)

    private fun WeekParity.includes(week: Int): Boolean = when (this) {
        WeekParity.ALL -> true
        WeekParity.ODD -> week % 2 == 1
        WeekParity.EVEN -> week % 2 == 0
    }
}

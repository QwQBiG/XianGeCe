package win.iqwqi.xiangece.widget

import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.PeriodTemplateEntity

internal data class WidgetCourseSlot(val meeting: CourseMeetingEntity, val startMinutes: Int, val endMinutes: Int)

internal fun nextWidgetCourse(
    meetings: List<CourseMeetingEntity>, periods: List<PeriodTemplateEntity>, nowMinutes: Int,
): WidgetCourseSlot? {
    val byIndex = periods.associateBy { it.periodIndex }
    return meetings.mapNotNull { meeting ->
        val start = byIndex[meeting.startPeriod]?.startMinutes ?: return@mapNotNull null
        val end = byIndex[meeting.endPeriod]?.endMinutes ?: return@mapNotNull null
        if (end <= start || end <= nowMinutes) null else WidgetCourseSlot(meeting, start, end)
    }.minByOrNull { it.startMinutes }
}

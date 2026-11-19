package win.iqwqi.xiangece.widget

import org.junit.Assert.*
import org.junit.Test
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.PeriodTemplateEntity

class WidgetCourseSlotTest {
    private val periods = listOf(PeriodTemplateEntity(1, 480, 525), PeriodTemplateEntity(2, 535, 580),
        PeriodTemplateEntity(3, 590, 635))
    private val double = CourseMeetingEntity(courseId = 1, dayOfWeek = 1, startPeriod = 1, endPeriod = 2,
        startWeek = 1, endWeek = 20)

    @Test fun ongoingCrossPeriodCourseRemainsVisibleDuringBreakAndSecondPeriod() {
        for (minute in listOf(479, 525, 540, 579)) {
            val result = nextWidgetCourse(listOf(double), periods, minute)!!
            assertEquals(480, result.startMinutes)
            assertEquals(580, result.endMinutes)
        }
    }

    @Test fun finishedCourseSwitchesToNextAndMissingEndDoesNotInventTime() {
        val upcoming = double.copy(courseId = 2, startPeriod = 3, endPeriod = 3)
        assertEquals(2L, nextWidgetCourse(listOf(double, upcoming), periods, 580)!!.meeting.courseId)
        assertNull(nextWidgetCourse(listOf(double, upcoming), periods, 635))
        assertNull(nextWidgetCourse(listOf(double.copy(endPeriod = 8)), periods, 480))
    }
}

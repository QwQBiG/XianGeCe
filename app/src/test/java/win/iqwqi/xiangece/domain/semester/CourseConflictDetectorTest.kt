package win.iqwqi.xiangece.domain.semester

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.domain.model.WeekParity

class CourseConflictDetectorTest {
    @Test
    fun detectsPeriodAndWeekOverlap() {
        val first = meeting(day = 2, start = 3, end = 4, startWeek = 1, endWeek = 16)
        val second = meeting(day = 2, start = 4, end = 5, startWeek = 10, endWeek = 18)
        assertTrue(CourseConflictDetector.overlaps(first, second))
    }

    @Test
    fun oddAndEvenMeetingsDoNotConflict() {
        val odd = meeting(parity = WeekParity.ODD)
        val even = meeting(parity = WeekParity.EVEN)
        assertFalse(CourseConflictDetector.overlaps(odd, even))
    }

    @Test
    fun sameParityConflictsOnlyInsideSharedWeekRange() {
        val earlyOdd = meeting(startWeek = 1, endWeek = 7, parity = WeekParity.ODD)
        val lateOdd = meeting(startWeek = 8, endWeek = 16, parity = WeekParity.ODD)
        assertFalse(CourseConflictDetector.overlaps(earlyOdd, lateOdd))

        val sharedOdd = meeting(startWeek = 7, endWeek = 9, parity = WeekParity.ODD)
        assertTrue(CourseConflictDetector.overlaps(earlyOdd, sharedOdd))
    }

    private fun meeting(
        day: Int = 1,
        start: Int = 1,
        end: Int = 2,
        startWeek: Int = 1,
        endWeek: Int = 16,
        parity: WeekParity = WeekParity.ALL,
    ) = CourseMeetingEntity(
        courseId = 1,
        dayOfWeek = day,
        startPeriod = start,
        endPeriod = end,
        startWeek = startWeek,
        endWeek = endWeek,
        weekParity = parity,
    )
}

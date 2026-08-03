package win.iqwqi.xiangece.core.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.data.local.CourseEntity
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.domain.model.WeekParity

class TimetableCodeCodecTest {
    @Test
    fun roundTripPreservesCourseSlots() {
        val code = TimetableCodeCodec.encode(
            courses = listOf(CourseEntity(id = 7, name = "Data Structures", teacher = "Lin")),
            meetings = listOf(
                CourseMeetingEntity(
                    id = 9,
                    courseId = 7,
                    dayOfWeek = 3,
                    startPeriod = 3,
                    endPeriod = 4,
                    startWeek = 2,
                    endWeek = 16,
                    weekParity = WeekParity.ODD,
                    location = "A201",
                ),
            ),
        )

        val rows = TimetableCodeCodec.decode(code)

        assertTrue(code.startsWith("XGC1-"))
        assertEquals(1, rows.size)
        assertEquals("Data Structures", rows.single().name)
        assertEquals(3, rows.single().dayOfWeek)
        assertEquals(WeekParity.ODD, rows.single().parity)
    }
}

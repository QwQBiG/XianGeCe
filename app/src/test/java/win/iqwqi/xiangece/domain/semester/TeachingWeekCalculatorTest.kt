package win.iqwqi.xiangece.domain.semester

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TeachingWeekCalculatorTest {
    @Test
    fun calculatesAndClampsTeachingWeek() {
        val start = LocalDate.of(2026, 9, 7)
        assertEquals(1, TeachingWeekCalculator.weekOf(start, start, 20))
        assertEquals(3, TeachingWeekCalculator.weekOf(start, start.plusWeeks(2), 20))
        assertEquals(1, TeachingWeekCalculator.weekOf(start, start.minusDays(2), 20))
        assertEquals(20, TeachingWeekCalculator.weekOf(start, start.plusWeeks(30), 20))
        assertEquals(0f, TeachingWeekCalculator.progress(start, start.minusDays(1), 20), 0f)
        assertEquals(1f, TeachingWeekCalculator.progress(start, start.plusWeeks(20), 20), 0f)
        assertEquals(false, TeachingWeekCalculator.contains(start, start.minusDays(1), 20))
        assertEquals(true, TeachingWeekCalculator.contains(start, start.plusWeeks(2), 20))
    }
}

package win.iqwqi.xiangece.ui

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import win.iqwqi.xiangece.ui.screens.*

class CalendarDatesTest {
    @Test fun leapFebruaryContains29AndMondayLeadingDays() {
        val days = calendarDays(YearMonth.of(2028, 2))
        assertEquals(42, days.size)
        assertEquals(LocalDate.of(2028, 1, 31), days.first())
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
        assertEquals(29, days.count { it.monthValue == 2 })
        assertEquals(42, days.distinct().size)
    }
    @Test fun nonLeapFebruaryAndYearBoundaryAreRealDates() {
        assertEquals(28, calendarDays(YearMonth.of(2027, 2)).count { it.monthValue == 2 })
        assertEquals(LocalDate.of(2025, 12, 29), calendarDays(YearMonth.of(2026, 1)).first())
        val december = calendarMonthIndex(YearMonth.of(2026, 12))
        assertEquals(YearMonth.of(2027, 1), calendarMonthAt(december + 1))
        assertEquals(YearMonth.of(2026, 11), calendarMonthAt(december - 1))
    }
    @Test fun timestampUsesLocalDayInsteadOfUtcOrCreatedDate() {
        val millis = Instant.parse("2026-09-10T17:30:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026, 9, 11), calendarDate(millis, ZoneId.of("Asia/Shanghai")))
        assertEquals(LocalDate.of(2026, 9, 10), calendarDate(millis, ZoneId.of("America/New_York")))
    }
    @Test fun completeYearRangeCanRoundTrip() {
        listOf(YearMonth.of(1, 1), YearMonth.of(9999, 12), YearMonth.of(2026, 9)).forEach {
            assertEquals(it, calendarMonthAt(calendarMonthIndex(it)))
        }
    }
}

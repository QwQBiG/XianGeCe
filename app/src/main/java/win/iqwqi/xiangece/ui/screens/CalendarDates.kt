package win.iqwqi.xiangece.ui.screens

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal fun calendarDays(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    return List(42) { start.plusDays(it.toLong()) }
}

internal fun calendarMonthIndex(month: YearMonth): Int = (month.year - 1) * 12 + month.monthValue - 1
internal fun calendarMonthAt(index: Int): YearMonth = YearMonth.of(index / 12 + 1, index % 12 + 1)
internal fun calendarDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

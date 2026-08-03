package win.iqwqi.xiangece.domain.semester

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object TeachingWeekCalculator {
    fun weekOf(startDate: LocalDate, date: LocalDate, weekCount: Int): Int {
        require(weekCount > 0)
        val calculated = ChronoUnit.WEEKS.between(mondayOf(startDate), date).toInt() + 1
        return calculated.coerceIn(1, weekCount)
    }

    fun progress(startDate: LocalDate, date: LocalDate, weekCount: Int): Float =
        when {
            date.isBefore(mondayOf(startDate)) -> 0f
            !date.isBefore(mondayOf(startDate).plusWeeks(weekCount.toLong())) -> 1f
            else -> {
                val elapsedDays = ChronoUnit.DAYS.between(mondayOf(startDate), date).toFloat()
                (elapsedDays / (weekCount * 7f)).coerceIn(0f, 1f)
            }
        }

    fun contains(startDate: LocalDate, date: LocalDate, weekCount: Int): Boolean =
        !date.isBefore(mondayOf(startDate)) &&
            date.isBefore(mondayOf(startDate).plusWeeks(weekCount.toLong()))

    private fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

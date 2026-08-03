package win.iqwqi.xiangece.core.importing

import org.junit.Assert.assertEquals
import org.junit.Test
import win.iqwqi.xiangece.domain.parser.TimetableTextParser

class TabularTimetableImporterTest {
    private val importer = TabularTimetableImporter(TimetableTextParser())

    @Test
    fun parsesWeekdayColumnsAndPeriodRows() {
        val rows = importer.parseGrid(
            rows = listOf(
                listOf("Period", "Mon", "Tue", "Wed", "Thu", "Fri"),
                listOf("1-2", "Math\nA101\n1-16", "", "English\nB202", "", ""),
            ),
            defaultEndWeek = 20,
        )

        assertEquals(2, rows.size)
        assertEquals("Math", rows[0].name)
        assertEquals(1, rows[0].dayOfWeek)
        assertEquals(1, rows[0].startPeriod)
        assertEquals(2, rows[0].endPeriod)
        assertEquals("English", rows[1].name)
        assertEquals(3, rows[1].dayOfWeek)
    }
}

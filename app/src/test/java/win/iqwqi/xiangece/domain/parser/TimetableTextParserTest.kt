package win.iqwqi.xiangece.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import win.iqwqi.xiangece.domain.model.WeekParity

class TimetableTextParserTest {
    @Test
    fun parsesEditableRowsFromOcrLines() {
        val rows = TimetableTextParser().parse(
            """
            高等数学 周一 第1-2节 第1-16周 综A302
            数据结构 星期三 第3-4节 第2-18周 单周 教A201
            """.trimIndent(),
            defaultEndWeek = 20,
        )

        assertEquals(2, rows.size)
        assertEquals(1, rows[0].dayOfWeek)
        assertEquals(2, rows[0].endPeriod)
        assertEquals(WeekParity.ODD, rows[1].parity)
    }
}

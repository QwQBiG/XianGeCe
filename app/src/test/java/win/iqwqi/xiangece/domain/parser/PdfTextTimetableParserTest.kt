package win.iqwqi.xiangece.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.core.ocr.OcrRegion
import win.iqwqi.xiangece.domain.model.WeekParity

class PdfTextTimetableParserTest {
    private val parser = PdfTextTimetableParser()

    @Test
    fun rebuildsSelectablePdfRowsByDayAndExplicitPeriod() {
        val result = parser.parse(
            listOf(
                region("星期一", 133, 63, 165, 75),
                region("星期二", 237, 63, 269, 75),
                region("星期三", 341, 63, 373, 75),
                region("星期四", 445, 63, 477, 75),
                region("星期五", 548, 63, 580, 75),
                region("计算机组成原理☆", 104, 88, 188, 100),
                region("(1-2节)1-17周/场地:明理楼0343/教师:杨建/考核方式:考试", 104, 101, 250, 115),
                region("大学英语3☆", 445, 155, 505, 168),
                region("(3-4节)1-13周(单)/场地:明理楼0138/教师:吕燕/考核方式:考试", 445, 170, 600, 185),
            ),
            defaultEndWeek = 20,
        )

        assertEquals(2, result.size)
        assertTrue(result.any {
            it.name == "计算机组成原理" && it.dayOfWeek == 1 &&
                it.startPeriod == 1 && it.endPeriod == 2 &&
                it.startWeek == 1 && it.endWeek == 17 &&
                it.location == "明理楼0343" && it.teacher == "杨建"
        })
        assertTrue(result.any {
            it.name == "大学英语3" && it.dayOfWeek == 4 &&
                it.startPeriod == 3 && it.endPeriod == 4 && it.parity == WeekParity.ODD
        })
    }

    private fun region(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrRegion(text, left, top, right, bottom)
}

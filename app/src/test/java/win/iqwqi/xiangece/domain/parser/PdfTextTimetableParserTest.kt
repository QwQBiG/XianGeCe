package win.iqwqi.xiangece.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.core.ocr.OcrRegion
import win.iqwqi.xiangece.domain.model.WeekParity

class PdfTextTimetableParserTest {
    private val parser = PdfTextTimetableParser()

    @Test
    fun tableLayoutUsesCoordinatesAndReadsUnlabelledMetadata() {
        val regions = mutableListOf<OcrRegion>()
        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, day ->
            regions += region("星期$day", 135 + index * 104, 60, 170 + index * 104, 72)
        }
        regions += listOf(
            region("计算机组成原理☆", 104, 88, 190, 99),
            region("(1-2节)1-17周", 104, 101, 186, 112),
            region("明理楼0343", 104, 114, 170, 125),
            region("杨建", 104, 127, 136, 138),
            region("考试", 104, 140, 136, 151),
            region("大学英语3☆", 447, 155, 510, 166),
            region("(3-4节)1-13周(单)", 447, 168, 550, 179),
            region("明理楼0138", 447, 181, 514, 192),
            region("吕燕", 447, 194, 480, 205),
            region("线性代数1☆", 447, 215, 516, 226),
            region("(3-4节)2-16周(双)", 447, 228, 552, 239),
            region("明理楼0335", 447, 241, 514, 252),
            region("田蒙", 447, 254, 480, 265),
        )

        val result = parser.parse(regions, defaultEndWeek = 20, layout = PdfTextTimetableParser.Layout.TABLE)

        assertEquals(3, result.size)
        assertTrue(result.any {
            it.name == "计算机组成原理" && it.dayOfWeek == 1 &&
                it.startPeriod == 1 && it.endPeriod == 2 &&
                it.location == "明理楼0343" && it.teacher == "杨建" &&
                it.startWeek == 1 && it.endWeek == 17 && it.parity == WeekParity.ALL
        })
        assertTrue(result.any {
            it.name == "大学英语3" && it.dayOfWeek == 4 &&
                it.startPeriod == 3 && it.endPeriod == 4 &&
                it.startWeek == 1 && it.endWeek == 13 && it.parity == WeekParity.ODD
        })
        assertTrue(result.any {
            it.name == "线性代数1" && it.dayOfWeek == 4 &&
                it.startPeriod == 3 && it.endPeriod == 4 &&
                it.startWeek == 2 && it.endWeek == 16 && it.parity == WeekParity.EVEN
        })
    }

    @Test
    fun listLayoutReusesAStandalonePeriodForOddAndEvenRows() {
        val regions = listOf(
            region("星期三", 24, 235, 66, 247),
            region("星期四", 24, 314, 66, 326),
            region("3-4", 92, 239, 118, 251),
            region("大学英语3☆", 154, 239, 230, 251),
            region("周数:1-13周/场地:明理楼0138/教师:吕燕/考核方式:考试", 356, 239, 780, 251),
            region("大学英语3☆", 154, 307, 230, 319),
            region("周数:1-13周(单)/场地:明理楼0138/教师:吕燕/考核方式:考试", 356, 307, 790, 319),
            region("3-4", 92, 320, 118, 332),
            region("线性代数1☆", 154, 333, 230, 345),
            region("周数:2-16周(双)/场地:明理楼0335/教师:田蒙/考核方式:考试", 356, 333, 790, 345),
        )

        val result = parser.parse(regions, defaultEndWeek = 20, layout = PdfTextTimetableParser.Layout.LIST)

        assertEquals(3, result.size)
        assertTrue(result.any {
            it.name == "大学英语3" && it.dayOfWeek == 3 &&
                it.startPeriod == 3 && it.endPeriod == 4 && it.parity == WeekParity.ALL
        })
        assertTrue(result.any {
            it.name == "大学英语3" && it.dayOfWeek == 4 &&
                it.startPeriod == 3 && it.endPeriod == 4 && it.parity == WeekParity.ODD
        })
        assertTrue(result.any {
            it.name == "线性代数1" && it.dayOfWeek == 4 &&
                it.startPeriod == 3 && it.endPeriod == 4 && it.parity == WeekParity.EVEN &&
                it.location == "明理楼0335" && it.teacher == "田蒙"
        })
    }

    private fun region(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrRegion(text, left, top, right, bottom)
}

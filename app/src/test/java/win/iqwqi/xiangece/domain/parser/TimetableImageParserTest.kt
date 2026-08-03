package win.iqwqi.xiangece.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.core.ocr.OcrPage
import win.iqwqi.xiangece.core.ocr.OcrRegion

class TimetableImageParserTest {
    private val parser = TimetableImageParser(TimetableTextParser())

    @Test
    fun mapsCourseBlocksFromWeekdayAndPeriodCoordinates() {
        val page = OcrPage(
            text = "周一 周二 周三\n1\n2\n3\n高等数学\n综A302\n数据结构\n教B201",
            width = 800,
            height = 1_200,
            lines = listOf(
                region("周一", 120, 80, 190, 120),
                region("周二", 250, 80, 320, 120),
                region("周三", 380, 80, 450, 120),
                region("1", 25, 150, 50, 180),
                region("2", 25, 230, 50, 260),
                region("3", 25, 310, 50, 340),
                region("高等数学", 115, 145, 195, 175),
                region("综A302", 115, 180, 190, 210),
                region("数据结构", 375, 225, 455, 255),
                region("教B201", 375, 260, 450, 290),
            ),
            blocks = listOf(
                region("周一 周二 周三", 110, 75, 455, 125),
                region("高等数学\n综A302", 110, 140, 200, 215),
                region("数据结构\n教B201", 370, 220, 460, 295),
            ),
        )

        val result = parser.parse(page, defaultEndWeek = 18, maxPeriods = 12)

        assertTrue(result.any { it.name == "高等数学" && it.dayOfWeek == 1 && it.startPeriod == 1 })
        assertTrue(result.any { it.name == "数据结构" && it.dayOfWeek == 3 && it.startPeriod == 2 })
        assertEquals("综A302", result.first { it.name == "高等数学" }.location)
    }

    @Test
    fun fallsBackToTextWhenCoordinatesAreUnavailable() {
        val page = OcrPage(
            text = "课程：大学英语 周四 第5-6节 第1-16周 教A201",
            width = 1_000,
            height = 2_000,
            lines = emptyList(),
            blocks = emptyList(),
        )

        val result = parser.parse(page, defaultEndWeek = 18, maxPeriods = 12)

        assertTrue(result.any { it.dayOfWeek == 4 && it.startPeriod == 5 && it.endPeriod == 6 })
    }

    @Test
    fun groupsSeparateOcrLinesIntoOnePdfCourseCell() {
        val page = OcrPage(
            text = "",
            width = 840,
            height = 595,
            lines = listOf(
                region("星期一", 120, 60, 175, 74),
                region("星期二", 225, 60, 280, 74),
                region("星期三", 330, 60, 385, 74),
                region("1", 70, 78, 82, 90),
                region("2", 70, 118, 82, 130),
                region("计算机组成原理", 103, 82, 185, 94),
                region("(1-2节)1-17周", 103, 95, 188, 107),
                region("场地:明理楼0343", 103, 108, 190, 120),
                region("教师:杨建", 103, 121, 160, 133),
            ),
            blocks = emptyList(),
        )

        val result = parser.parse(page, defaultEndWeek = 20, maxPeriods = 16)

        val course = result.single()
        assertEquals("计算机组成原理", course.name)
        assertEquals(1, course.dayOfWeek)
        assertEquals(1, course.startPeriod)
        assertEquals(2, course.endPeriod)
        assertEquals(17, course.endWeek)
        assertEquals("杨建", course.teacher)
    }

    private fun region(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrRegion(text, left, top, right, bottom)
}

package win.iqwqi.xiangece.domain.parser

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.domain.model.DraftType
import win.iqwqi.xiangece.domain.model.WeekParity

class CampusTextParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 9, 7, 10, 0).atZone(zone).toInstant().toEpochMilli()
    private val parser = CampusTextParser()

    @Test
    fun parsesAbsoluteDeadline() {
        val result = parser.parse(
            "高等数学作业请于2026年9月10日 18:30前提交，地点：综A302",
            ParserContext(now, zoneId = zone),
        )

        assertEquals(DraftType.TASK, result.type)
        assertEquals("综A302", result.location)
        assertEquals(
            LocalDateTime.of(2026, 9, 10, 18, 30).atZone(zone).toInstant().toEpochMilli(),
            result.dateTimeEpochMillis,
        )
    }

    @Test
    fun parsesTeachingWeekParityAndPeriods() {
        val semesterStart = LocalDate.of(2026, 9, 7)
        val result = parser.parse(
            "课程：数据结构 第3周周三 第3-4节 单周 教室：教A201",
            ParserContext(now, semesterStart.toEpochDay(), zone),
        )

        assertEquals(DraftType.COURSE_MEETING, result.type)
        assertEquals(3, result.teachingWeek)
        assertEquals(3, result.dayOfWeek)
        assertEquals(3, result.startPeriod)
        assertEquals(4, result.endPeriod)
        assertEquals(WeekParity.ODD, result.weekParity)
        val parsed = java.time.Instant.ofEpochMilli(result.dateTimeEpochMillis!!).atZone(zone)
        assertEquals(LocalDate.of(2026, 9, 23), parsed.toLocalDate())
        assertTrue(result.ambiguities.none { "日期没有写年份" in it })
    }

    @Test
    fun ambiguousWeekdayRequiresConfirmation() {
        val result = parser.parse("周五晚上8点交实验报告", ParserContext(now, zoneId = zone))
        assertTrue(result.ambiguities.any { "本周或下周" in it })
    }

    @Test
    fun eveningTwelveMovesToNextDay() {
        val result = parser.parse("明晚12点截止", ParserContext(now, zoneId = zone))
        val parsed = java.time.Instant.ofEpochMilli(result.dateTimeEpochMillis!!).atZone(zone)
        assertEquals(LocalDate.of(2026, 9, 9), parsed.toLocalDate())
        assertEquals(0, parsed.hour)
    }

    @Test
    fun thisWeekUsesCurrentCalendarWeekEvenWhenDayHasPassed() {
        val friday = LocalDateTime.of(2026, 9, 11, 10, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val result = parser.parse("本周三下午3点开会", ParserContext(friday, zoneId = zone))
        val parsed = java.time.Instant.ofEpochMilli(result.dateTimeEpochMillis!!).atZone(zone)

        assertEquals(LocalDate.of(2026, 9, 9), parsed.toLocalDate())
        assertEquals(15, parsed.hour)
        assertTrue(result.ambiguities.none { "本周或下周" in it })
    }

    @Test
    fun nextWeekUsesFollowingCalendarWeek() {
        val result = parser.parse("下周一上午9点答辩", ParserContext(now, zoneId = zone))
        val parsed = java.time.Instant.ofEpochMilli(result.dateTimeEpochMillis!!).atZone(zone)

        assertEquals(LocalDate.of(2026, 9, 14), parsed.toLocalDate())
        assertEquals(9, parsed.hour)
    }

    @Test
    fun multipleDatesAreNeverSilent() {
        val result = parser.parse(
            "活动时间为2026年9月10日或2026年9月11日 18:00",
            ParserContext(now, zoneId = zone),
        )

        assertTrue(result.ambiguities.any { "多个日期" in it })
    }

    @Test
    fun invalidPeriodAndMissingDeadlineDateRequireConfirmation() {
        val periodResult = parser.parse("课程：物理 周二第8-3节", ParserContext(now, zoneId = zone))
        val deadlineResult = parser.parse("请尽快提交作业，截止前完成", ParserContext(now, zoneId = zone))

        assertTrue(periodResult.ambiguities.any { "节次范围无效" in it })
        assertTrue(deadlineResult.ambiguities.any { "没有明确日期" in it })
    }
}

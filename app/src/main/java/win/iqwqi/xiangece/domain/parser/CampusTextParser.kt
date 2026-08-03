package win.iqwqi.xiangece.domain.parser

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import win.iqwqi.xiangece.domain.model.DraftType
import win.iqwqi.xiangece.domain.model.ParsedDraft
import win.iqwqi.xiangece.domain.model.WeekParity

data class ParserContext(
    val nowEpochMillis: Long = System.currentTimeMillis(),
    val semesterStartEpochDay: Long? = null,
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val semesterWeekCount: Int? = null,
)

@Singleton
class CampusTextParser @Inject constructor() {
    private val absoluteDate = Regex("""(?<!第)(?:(20\d{2})[年/.-])?(\d{1,2})[月/.-](\d{1,2})日?(?!\s*节)""")
    private val clockTime = Regex("""(?<!\d)([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)(?!\d)""")
    private val chineseTime = Regex("""(?<!\d)([0-2]?\d)\s*[点时](半|[0-5]?\d分?)?""")
    private val teachingWeek = Regex("""第\s*(\d{1,2})\s*周""")
    private val weekday = Regex("""(?:(下|本|这)\s*周|星期|周)([一二三四五六日天])""")
    private val period = Regex("""第?\s*(\d{1,2})\s*(?:[-—~至到]\s*(\d{1,2}))?\s*节""")
    private val coursePattern = Regex("""(?:课程|科目|课名)\s*[:：]\s*([^\n，。,；;]+)""")
    private val locationPattern = Regex("""(?:地点|教室|位置)\s*[:：]\s*([^\n，。,；;]+)""")
    private val compactLocation = Regex("""(?<!\w)(?:教|综|实|科|文|理|图)[A-Za-z]?\d{2,4}(?!\w)""")

    fun parse(text: String, context: ParserContext = ParserContext()): ParsedDraft {
        val normalized = text
            .replace('：', ':')
            .replace("\r\n", "\n")
            .trim()
        if (normalized.isBlank()) {
            return ParsedDraft(
                type = DraftType.NOTE,
                title = "未识别内容",
                ambiguities = listOf("没有可解析的文字"),
            )
        }

        val now = Instant.ofEpochMilli(context.nowEpochMillis).atZone(context.zoneId)
        val ambiguities = mutableListOf<String>()
        val weekMatches = teachingWeek.findAll(normalized).toList()
        val weekMatch = weekMatches.firstOrNull()
        val parsedTeachingWeek = weekMatch?.groupValues?.get(1)?.toIntOrNull()
        if (weekMatches.map { it.groupValues[1] }.distinct().size > 1) {
            ambiguities += "文字中出现了多个教学周，请确认"
        }
        if (
            parsedTeachingWeek != null &&
            (parsedTeachingWeek < 1 || context.semesterWeekCount?.let { parsedTeachingWeek > it } == true)
        ) {
            ambiguities += "教学周超出当前学期范围"
        }
        val weekdayMatches = weekday.findAll(normalized).toList()
        val weekdayMatch = weekdayMatches.firstOrNull()
        val parsedDayOfWeek = weekdayMatch?.groupValues?.get(2)?.let(::weekdayNumber)
        if (weekdayMatches.map { it.groupValues[2] }.distinct().size > 1) {
            ambiguities += "文字中出现了多个星期，请确认"
        }

        val absoluteMatches = absoluteDate.findAll(normalized).toList()
        val absoluteDates = absoluteMatches.mapNotNull { parseAbsoluteDate(it, now.toLocalDate()) }.distinct()
        if (absoluteMatches.isNotEmpty() && absoluteDates.isEmpty()) {
            ambiguities += "识别到的日期无效，请手动填写"
        }
        if (absoluteDates.size > 1) {
            ambiguities += "文字中出现了多个日期，当前采用第一个"
        }
        if (absoluteMatches.firstOrNull()?.groupValues?.get(1).isNullOrBlank() && absoluteDates.isNotEmpty()) {
            ambiguities += "日期没有写年份，当前按 ${now.year} 年处理"
        }
        var date = absoluteDates.firstOrNull() ?: parseRelativeDate(normalized, now.toLocalDate())

        if (date == null && parsedTeachingWeek != null && parsedDayOfWeek != null) {
            date = context.semesterStartEpochDay?.let { start ->
                LocalDate.ofEpochDay(start)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .plusWeeks((parsedTeachingWeek - 1).toLong())
                    .plusDays((parsedDayOfWeek - 1).toLong())
            }
            if (date == null) ambiguities += "识别到教学周，但尚未设置学期开始日期"
        }

        if (date == null && parsedDayOfWeek != null) {
            val modifier = weekdayMatch?.groupValues?.get(1)
            date = when (modifier) {
                "本", "这" -> weekdayInRelativeWeek(now.toLocalDate(), parsedDayOfWeek, 0)
                "下" -> weekdayInRelativeWeek(now.toLocalDate(), parsedDayOfWeek, 1)
                else -> {
                    ambiguities += "“周${weekdayChinese(parsedDayOfWeek)}”未说明本周或下周，请确认日期"
                    nextWeekday(now.toLocalDate(), parsedDayOfWeek)
                }
            }
        }

        val parsedTimes = parseTimes(normalized)
        if (parsedTimes.size > 1) {
            ambiguities += "文字中出现了多个时间，当前采用第一个"
        }
        var time = parsedTimes.firstOrNull()
        if (
            normalized.contains("今晚12点") ||
            normalized.contains("明晚12点") ||
            normalized.contains("晚上12点")
        ) {
            ambiguities += "“晚上12点”可能表示次日00:00，请确认"
            if (date != null) date = date.plusDays(1)
            time = LocalTime.MIDNIGHT
        }
        if (time != null && date == null) ambiguities += "识别到时间，但没有明确日期"
        if (date != null && time == null && containsDeadline(normalized)) {
            ambiguities += "识别到截止日期，但没有明确时刻"
        }
        if (date == null && containsDeadline(normalized)) {
            ambiguities += "识别到截止语句，但没有明确日期"
        }

        val dateTime = date?.atTime(time ?: LocalTime.of(if (containsDeadline(normalized)) 23 else 9, if (containsDeadline(normalized)) 59 else 0))
        val periodMatch = period.find(normalized)
        val startPeriod = periodMatch?.groupValues?.get(1)?.toIntOrNull()
        val endPeriod = periodMatch?.groupValues?.get(2)?.toIntOrNull() ?: startPeriod
        if (startPeriod != null && (startPeriod !in 1..24 || endPeriod == null || endPeriod !in startPeriod..24)) {
            ambiguities += "课程节次范围无效"
        }
        val parity = when {
            "单周" in normalized || "单数周" in normalized -> WeekParity.ODD
            "双周" in normalized || "双数周" in normalized -> WeekParity.EVEN
            else -> WeekParity.ALL
        }
        val location = locationPattern.find(normalized)?.groupValues?.get(1)?.trim()
            ?: compactLocation.find(normalized)?.value
        val course = coursePattern.find(normalized)?.groupValues?.get(1)?.trim()
            ?: inferCourse(normalized)
        val title = inferTitle(normalized)
        val type = when {
            listOf("考试", "讲座", "活动", "会议", "答辩", "竞赛").any(normalized::contains) -> DraftType.EVENT
            startPeriod != null && parsedDayOfWeek != null && course != null -> DraftType.COURSE_MEETING
            dateTime != null || containsDeadline(normalized) || listOf("作业", "提交", "完成", "打卡").any(normalized::contains) -> DraftType.TASK
            else -> DraftType.NOTE
        }
        if (type == DraftType.EVENT && dateTime == null) {
            ambiguities += "事件没有明确日期与时间"
        }

        var confidence = 0.28f
        if (title.isNotBlank()) confidence += 0.15f
        if (date != null) confidence += 0.22f
        if (time != null) confidence += 0.12f
        if (location != null) confidence += 0.08f
        if (course != null) confidence += 0.08f
        if (parsedTeachingWeek != null || startPeriod != null) confidence += 0.07f
        confidence -= min(ambiguities.size * 0.08f, 0.24f)

        return ParsedDraft(
            type = type,
            title = title,
            description = normalized,
            dateTimeEpochMillis = dateTime?.atZone(context.zoneId)?.toInstant()?.toEpochMilli(),
            courseName = course,
            location = location,
            teachingWeek = parsedTeachingWeek,
            dayOfWeek = parsedDayOfWeek,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weekParity = parity,
            confidence = confidence.coerceIn(0f, 1f),
            ambiguities = ambiguities.distinct(),
        )
    }

    private fun parseAbsoluteDate(match: MatchResult, fallback: LocalDate): LocalDate? {
        val year = match.groupValues[1].toIntOrNull() ?: fallback.year
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun parseRelativeDate(text: String, today: LocalDate): LocalDate? = when {
        "大后天" in text -> today.plusDays(3)
        "后天" in text -> today.plusDays(2)
        "明天" in text || "明晚" in text -> today.plusDays(1)
        "今天" in text || "今日" in text || "今晚" in text -> today
        else -> null
    }

    private fun parseTimes(text: String): List<LocalTime> {
        val matches = mutableListOf<Pair<Int, LocalTime>>()
        clockTime.findAll(text).forEach {
            matches += it.range.first to LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        chineseTime.findAll(text).forEach {
            var hour = it.groupValues[1].toInt()
            val suffix = it.groupValues[2]
            val minute = when {
                suffix == "半" -> 30
                suffix.isBlank() -> 0
                else -> suffix.filter(Char::isDigit).toIntOrNull() ?: 0
            }
            val prefix = text.take(it.range.first).takeLast(6)
            if (hour < 12 && listOf("下午", "晚上", "今晚", "中午").any(prefix::contains)) hour += 12
            if (hour == 24) hour = 0
            runCatching { LocalTime.of(hour, minute) }.getOrNull()?.let { time ->
                matches += it.range.first to time
            }
        }
        return matches.sortedBy { it.first }.map { it.second }.distinct()
    }

    private fun inferTitle(text: String): String {
        val firstUsefulLine = text.lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotBlank() && it.length >= 2 }
            .orEmpty()
        val cleaned = firstUsefulLine
            .replace(Regex("""^(通知|提醒|各位同学|同学们)[：:，,\s]*"""), "")
            .trim()
        return when {
            cleaned.isBlank() -> "待确认事项"
            cleaned.length <= 34 -> cleaned
            else -> cleaned.take(32) + "…"
        }
    }

    private fun inferCourse(text: String): String? {
        val knownSuffix = Regex("""([\p{IsHan}A-Za-z0-9·]{2,18}(?:课程|实验|实训|数学|英语|物理|化学|程序设计|数据结构))""")
        return knownSuffix.find(text)?.groupValues?.get(1)
    }

    private fun containsDeadline(text: String): Boolean =
        listOf("截止", "之前", "前提交", "ddl", "DDL").any(text::contains)

    private fun weekdayNumber(value: String): Int = when (value) {
        "一" -> 1
        "二" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        else -> 7
    }

    private fun weekdayChinese(value: Int): String =
        listOf("一", "二", "三", "四", "五", "六", "日")[value.coerceIn(1, 7) - 1]

    private fun nextWeekday(today: LocalDate, target: Int): LocalDate {
        val delta = (target - today.dayOfWeek.value + 7) % 7
        return today.plusDays(delta.toLong())
    }

    private fun weekdayInRelativeWeek(today: LocalDate, target: Int, weekOffset: Long): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(weekOffset)
            .plusDays((target - 1).toLong())
}

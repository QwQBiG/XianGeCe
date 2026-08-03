package win.iqwqi.xiangece.domain.parser

import javax.inject.Inject
import win.iqwqi.xiangece.domain.model.WeekParity

data class TimetableCandidate(
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startWeek: Int = 1,
    val endWeek: Int,
    val parity: WeekParity = WeekParity.ALL,
)

class TimetableTextParser @Inject constructor() {
    fun parse(text: String, defaultEndWeek: Int): List<TimetableCandidate> =
        text.lineSequence()
            .map(String::trim)
            .filter { it.length >= 3 }
            .mapNotNull { parseLine(it, defaultEndWeek) }
            .distinctBy { listOf(it.name, it.dayOfWeek, it.startPeriod, it.startWeek) }
            .toList()

    private fun parseLine(line: String, defaultEndWeek: Int): TimetableCandidate? {
        val dayMatch = dayRegex.find(line) ?: return null
        val day = dayMap[dayMatch.groupValues[1]] ?: return null
        val periodMatch = periodRangeRegex.find(line) ?: singlePeriodRegex.find(line) ?: return null
        val startPeriod = periodMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val endPeriod = periodMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: startPeriod
        val weekMatch = weekRangeRegex.find(line)
        val startWeek = weekMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val endWeek = weekMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: defaultEndWeek
        val parity = when {
            "单周" in line -> WeekParity.ODD
            "双周" in line -> WeekParity.EVEN
            else -> WeekParity.ALL
        }
        val location = locationRegex.find(line)?.value.orEmpty()
        val cleaned = line
            .replace(dayMatch.value, " ")
            .replace(periodMatch.value, " ")
            .let { weekMatch?.let { match -> it.replace(match.value, " ") } ?: it }
            .replace("单周", " ")
            .replace("双周", " ")
            .replace(location, " ")
            .replace(Regex("""[|｜,，;；()（）\[\]]+"""), " ")
            .trim()
            .replace(Regex("""\s{2,}"""), " ")
        val name = cleaned.split(' ').firstOrNull { it.length >= 2 } ?: cleaned
        if (name.isBlank()) return null
        return TimetableCandidate(
            name = name,
            location = location,
            dayOfWeek = day,
            startPeriod = startPeriod,
            endPeriod = endPeriod.coerceAtLeast(startPeriod),
            startWeek = startWeek,
            endWeek = endWeek.coerceAtLeast(startWeek),
            parity = parity,
        )
    }

    private companion object {
        val dayRegex = Regex("""(?:周|星期)([一二三四五六日天])""")
        val periodRangeRegex = Regex("""第?\s*(\d{1,2})\s*[-~至—–]\s*(\d{1,2})\s*节""")
        val singlePeriodRegex = Regex("""第?\s*(\d{1,2})\s*节""")
        val weekRangeRegex = Regex("""第?\s*(\d{1,2})\s*[-~至—–]\s*(\d{1,2})\s*周""")
        val locationRegex = Regex("""(?:[A-Za-z\u4e00-\u9fa5]{0,5}(?:楼|教|综|实|文|理|体)[A-Za-z]?\s*\d{2,4}|[A-Za-z]\d{2,4})""")
        val dayMap = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
    }
}

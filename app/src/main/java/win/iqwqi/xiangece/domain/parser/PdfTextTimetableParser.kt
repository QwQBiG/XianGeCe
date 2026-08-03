package win.iqwqi.xiangece.domain.parser

import javax.inject.Inject
import win.iqwqi.xiangece.core.ocr.OcrRegion
import win.iqwqi.xiangece.domain.model.WeekParity

/**
 * Rebuilds selectable-text PDFs by their real page coordinates.  This is
 * deliberately independent from image OCR: a PDF course entry is accepted
 * only when it contains an explicit period range.
 */
class PdfTextTimetableParser @Inject constructor() {
    fun parse(regions: List<OcrRegion>, defaultEndWeek: Int): List<TimetableCandidate> {
        val days = regions.mapNotNull { region ->
            dayRegex.find(region.text)?.groupValues?.getOrNull(1)?.let(dayMap::get)?.let { it to region }
        }.distinctBy { it.first }.sortedBy { it.first }
        if (days.size < 3) return emptyList()

        val source = regions.filter { it.text.isNotBlank() }.sortedWith(compareBy<OcrRegion> { it.top }.thenBy { it.left })
        val periodAnchors = source.filter { periodRegex.containsMatchIn(it.text) }
        return periodAnchors.mapNotNull { details ->
            val period = periodRegex.find(details.text) ?: return@mapNotNull null
            // Detail rows can extend into the next column because of teacher and
            // location text; their left edge remains aligned with the course cell.
            val day = days.minByOrNull { (_, header) -> kotlin.math.abs(header.centerX - details.left) }?.first
                ?: return@mapNotNull null
            val startPeriod = period.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val endPeriod = period.groupValues[2].toIntOrNull() ?: startPeriod
            val week = weekRegex.find(details.text)
            val startWeek = week?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val endWeek = week?.groupValues?.getOrNull(2)?.toIntOrNull() ?: defaultEndWeek
            val parity = when {
                "单" in details.text && "周" in details.text -> WeekParity.ODD
                "双" in details.text && "周" in details.text -> WeekParity.EVEN
                else -> WeekParity.ALL
            }
            val nextAnchorTop = periodAnchors
                .asSequence()
                .filter { it.top > details.top && kotlin.math.abs(it.left - details.left) <= COLUMN_TOLERANCE }
                .map { it.top }
                .minOrNull()
                ?: Int.MAX_VALUE
            // A school PDF frequently wraps a course name, the building and the
            // room number onto separate physical lines. Reassemble the whole
            // cell between this period anchor and the next period anchor.
            val cellLines = source.filter { candidate ->
                kotlin.math.abs(candidate.left - details.left) <= COLUMN_TOLERANCE &&
                    candidate.top >= details.top - CELL_NAME_LOOKBACK &&
                    candidate.top < nextAnchorTop
            }.sortedBy { it.top }
            val cellText = cellLines.joinToString("\n") { it.text }
            val nameLines = cellLines.takeWhile { it.top <= details.top }
            // Do not promote period/week metadata to a course title. PDFBox may
            // split a wrapped title into separate text runs, so retain only
            // title-quality runs immediately above the period marker.
            val inlineName = nameLines
                .map { it.text.substringBefore(period.value).cleanCourseName() }
                .filter { it.isCourseName() }
                .joinToString(" ")
                .cleanCourseName()
            val name = inlineName.ifBlank {
                cellLines.asReversed().asSequence()
                    .filter { it.top < details.top }
                    .map { it.text.cleanCourseName() }
                    .firstOrNull { it.isCourseName() }.orEmpty()
            }
            if (!name.isCourseName()) return@mapNotNull null
            TimetableCandidate(
                name = name.take(48),
                teacher = extractTeacher(cellLines, cellText, name),
                location = extractLocation(cellLines, cellText),
                dayOfWeek = day,
                startPeriod = startPeriod,
                endPeriod = endPeriod.coerceAtLeast(startPeriod),
                startWeek = startWeek.coerceAtLeast(1),
                endWeek = endWeek.coerceAtLeast(startWeek).coerceAtMost(defaultEndWeek),
                parity = parity,
            )
        }.distinctBy { candidate ->
            listOf(candidate.name, candidate.dayOfWeek, candidate.startPeriod, candidate.endPeriod, candidate.startWeek, candidate.endWeek, candidate.parity)
        }
    }

    private fun extractLocation(lines: List<OcrRegion>, cellText: String): String {
        val labelled = locationRegex.find(cellText)?.groupValues?.getOrNull(1)?.compactField().orEmpty()
        if (labelled.isNotBlank()) return labelled
        return lines.asSequence()
            .map { it.text.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length <= 32 }
            .firstOrNull { locationLineRegex.containsMatchIn(it) }
            ?.compactField()
            .orEmpty()
    }

    private fun extractTeacher(lines: List<OcrRegion>, cellText: String, courseName: String): String {
        val labelled = teacherRegex.find(cellText)?.groupValues?.getOrNull(1)?.compactField().orEmpty()
        if (labelled.isNotBlank()) return labelled
        return lines.asSequence()
            .map { it.text.replace(Regex("\\s+"), "").trim() }
            .filter { it != courseName && it.length in 2..8 }
            .firstOrNull { teacherLineRegex.matches(it) }
            .orEmpty()
    }

    private fun String.cleanCourseName(): String =
        replace(Regex("[☆★▲※]"), "")
            .replace(Regex("(场地|教师|考核方式)[:：].*"), "")
            .replace(Regex("[\\s/|]+"), " ")
            .trim(' ', ':', '：', '(', '（')

    private fun String.compactField(): String =
        replace(Regex("\\s+"), "").trim().take(32)

    private fun String.isCourseName(): Boolean =
        length >= 2 &&
            !periodRegex.containsMatchIn(this) &&
            !weekRegex.containsMatchIn(this) &&
            !contains("场地") && !contains("教师") && !contains("考核") &&
            !matches(Regex("[\\d:：.\\- ]+"))

    private companion object {
        const val COLUMN_TOLERANCE = 48
        const val CELL_NAME_LOOKBACK = 72
        val dayRegex = Regex("(?:星期|周)([一二三四五六日天])")
        val periodRegex = Regex("\\(?\\s*(\\d{1,2})\\s*[-~至]\\s*(\\d{1,2})\\s*节")
        val weekRegex = Regex("(\\d{1,2})(?:\\s*[-~至]\\s*(\\d{1,2}))?\\s*周")
        val locationRegex = Regex("场地[:：]([\\s\\S]*?)(?=/教师[:：]|/考核|$)")
        val teacherRegex = Regex("教师[:：]([\\s\\S]*?)(?=/考核|$)")
        val locationLineRegex = Regex("(?:教室|实验室|楼|馆|区|校区).{0,10}\\d{2,4}|(?:[A-Za-z一二三四五六七八九十]+楼)\\s*\\d{2,4}")
        val teacherLineRegex = Regex("[\\u4e00-\\u9fa5]{2,8}")
        val dayMap = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
    }
}

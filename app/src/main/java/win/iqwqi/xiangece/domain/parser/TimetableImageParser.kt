package win.iqwqi.xiangece.domain.parser

import javax.inject.Inject
import kotlin.math.abs
import win.iqwqi.xiangece.core.ocr.OcrPage
import win.iqwqi.xiangece.core.ocr.OcrRegion
import win.iqwqi.xiangece.domain.model.WeekParity

class TimetableImageParser @Inject constructor(
    private val textParser: TimetableTextParser,
) {
    fun parse(
        page: OcrPage,
        defaultEndWeek: Int,
        maxPeriods: Int,
    ): List<TimetableCandidate> {
        val spatial = parseSpatial(page, defaultEndWeek, maxPeriods.coerceIn(1, 24))
        // Do not combine loose OCR text with positioned cells: that created
        // duplicate courses with unrelated weekday/period values.
        val parsed = if (spatial.isNotEmpty()) spatial else textParser.parse(page.text, defaultEndWeek)
        return parsed
            .filter { candidate ->
                candidate.dayOfWeek in 1..7 &&
                    candidate.startPeriod in 1..24 &&
                    candidate.endPeriod in candidate.startPeriod..24
            }
            .distinctBy {
                listOf(
                    it.name.normalizeName(),
                    it.dayOfWeek.toString(),
                    it.startPeriod.toString(),
                    it.startWeek.toString(),
                    it.endWeek.toString(),
                    it.parity.toString(),
                )
            }
    }

    private fun parseSpatial(
        page: OcrPage,
        defaultEndWeek: Int,
        maxPeriods: Int,
    ): List<TimetableCandidate> {
        val dayAnchors = page.lines.mapNotNull { line ->
            extractHeaderDay(line.text)?.let { day -> day to line }
        }.distinctBy { it.first }.sortedBy { it.first }
        if (dayAnchors.size < 3) return emptyList()

        val anchorSpacing = dayAnchors.zipWithNext { first, second ->
            abs(second.second.centerX - first.second.centerX)
        }.filter { it > 0 }.sorted().let { values ->
            values.getOrNull(values.size / 2)
        } ?: (page.width / 8)
        val headerBottom = dayAnchors.maxOf { it.second.bottom }
        val firstDayX = dayAnchors.minOf { it.second.centerX }
        val periodAnchors = page.lines.mapNotNull { line ->
            val match = periodAxisRegex.matchEntire(line.text.replace(" ", "")) ?: return@mapNotNull null
            val period = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            if (
                period !in 1..maxPeriods ||
                line.top <= headerBottom ||
                (line.centerX >= firstDayX - anchorSpacing / 3 && "节" !in line.text)
            ) {
                return@mapNotNull null
            }
            period to line.centerY
        }.distinctBy { it.first }.sortedBy { it.first }

        val syntheticBlocks = synthesizeCourseBlocks(
            lines = page.lines,
            dayAnchors = dayAnchors,
            headerBottom = headerBottom,
            anchorSpacing = anchorSpacing,
        )
        val parsedBlocks = (if (syntheticBlocks.isNotEmpty()) syntheticBlocks else page.blocks)
            .distinctBy { listOf(it.text, it.left, it.top) }
            .mapNotNull { block ->
            if (block.top <= headerBottom || block.text.isBlank()) return@mapNotNull null
            if (weekdayRegex.findAll(block.text).count() >= 2) return@mapNotNull null
            if (block.right - block.left > anchorSpacing * 1.35f) return@mapNotNull null
            val (day, dayRegion) = dayAnchors.minByOrNull {
                abs(it.second.centerX - block.centerX)
            } ?: return@mapNotNull null
            if (abs(dayRegion.centerX - block.centerX) > anchorSpacing * 0.72f) return@mapNotNull null
            createCandidate(
                block = block,
                day = day,
                periodAnchors = periodAnchors,
                defaultEndWeek = defaultEndWeek,
            )
            }
        if (parsedBlocks.isNotEmpty()) return parsedBlocks
        // Some screenshots are returned by ML Kit as single text lines rather
        // than course-cell blocks. Recreate small cells from weekday columns
        // and the nearest period row before giving up.
        return synthesizeGridCells(page.lines, dayAnchors, periodAnchors, headerBottom, anchorSpacing)
            .mapNotNull { (day, block) ->
                createCandidate(block, day, periodAnchors, defaultEndWeek)
            }
    }

    private fun synthesizeGridCells(
        lines: List<OcrRegion>,
        dayAnchors: List<Pair<Int, OcrRegion>>,
        periodAnchors: List<Pair<Int, Int>>,
        headerBottom: Int,
        anchorSpacing: Int,
    ): List<Pair<Int, OcrRegion>> {
        if (periodAnchors.isEmpty()) return emptyList()
        return lines
            .filter { line ->
                line.top > headerBottom &&
                    !periodAxisRegex.matches(line.text.replace(" ", "")) &&
                    extractHeaderDay(line.text) == null
            }
            .groupBy { line ->
                val (day, header) = dayAnchors.minByOrNull { abs(it.second.centerX - line.centerX) } ?: return@groupBy 0 to 0
                if (abs(header.centerX - line.centerX) > anchorSpacing * 0.72f) return@groupBy 0 to 0
                val period = periodAnchors.minByOrNull { abs(it.second - line.centerY) }?.first ?: 0
                day to period
            }
            .filterKeys { (day, period) -> day in 1..7 && period > 0 }
            .map { (key, grouped) ->
                key.first to OcrRegion(
                    text = grouped.sortedBy { it.top }.joinToString("\n") { it.text },
                    left = grouped.minOf { it.left },
                    top = grouped.minOf { it.top },
                    right = grouped.maxOf { it.right },
                    bottom = grouped.maxOf { it.bottom },
                )
            }
    }

    /**
     * OCR engines often return one line per physical line in a timetable cell
     * instead of one block per course. A course's "(1-2节)" line is a stable
     * anchor: join nearby lines in the same weekday column into a synthetic
     * course block before interpreting its fields.
     */
    private fun synthesizeCourseBlocks(
        lines: List<OcrRegion>,
        dayAnchors: List<Pair<Int, OcrRegion>>,
        headerBottom: Int,
        anchorSpacing: Int,
    ): List<OcrRegion> {
        return lines
            .filter { it.top > headerBottom && periodRegex.containsMatchIn(it.text) }
            .mapNotNull { periodLine ->
                val (_, anchor) = dayAnchors.minByOrNull {
                    abs(it.second.centerX - periodLine.centerX)
                } ?: return@mapNotNull null
                if (abs(anchor.centerX - periodLine.centerX) > anchorSpacing * 0.72f) {
                    return@mapNotNull null
                }
                val neighboring = lines.filter { line ->
                    line.top > headerBottom &&
                        abs(line.centerX - anchor.centerX) < anchorSpacing * 0.45f &&
                        line.top in (periodLine.top - CELL_CONTEXT_ABOVE)..(periodLine.bottom + CELL_CONTEXT_BELOW)
                }
                if (neighboring.isEmpty()) return@mapNotNull null
                OcrRegion(
                    text = neighboring.sortedBy { it.top }.joinToString("\n") { it.text },
                    left = neighboring.minOf { it.left },
                    top = neighboring.minOf { it.top },
                    right = neighboring.maxOf { it.right },
                    bottom = neighboring.maxOf { it.bottom },
                )
            }
    }

    private fun createCandidate(
        block: OcrRegion,
        day: Int,
        periodAnchors: List<Pair<Int, Int>>,
        defaultEndWeek: Int,
    ): TimetableCandidate? {
        val explicitPeriod = periodRegex.find(block.text)
        val periodsInside = periodAnchors
            .filter { (_, centerY) -> centerY in block.top..block.bottom }
            .map { it.first }
        val closestPeriod = periodAnchors.minByOrNull { (_, centerY) ->
            abs(centerY - block.centerY)
        }?.first
        val startPeriod = explicitPeriod?.groupValues?.get(1)?.toIntOrNull()
            ?: periodsInside.minOrNull()
            ?: closestPeriod
            ?: return null
        val endPeriod = explicitPeriod?.groupValues?.get(2)?.toIntOrNull()
            ?: periodsInside.maxOrNull()
            ?: startPeriod
        val weekMatch = weekRegex.find(block.text)
        val startWeek = weekMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val endWeek = weekMatch?.groupValues?.get(2)?.toIntOrNull() ?: defaultEndWeek
        val parity = when {
            "单周" in block.text -> WeekParity.ODD
            "双周" in block.text -> WeekParity.EVEN
            else -> WeekParity.ALL
        }
        val location = locationRegex.find(block.text)?.value.orEmpty()
        val teacher = teacherRegex.find(block.text)?.groupValues?.get(1)?.trim().orEmpty()
        val name = inferName(block.text, location, teacher) ?: return null
        return TimetableCandidate(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = day,
            startPeriod = startPeriod,
            endPeriod = endPeriod.coerceAtLeast(startPeriod),
            startWeek = startWeek.coerceAtLeast(1),
            endWeek = endWeek.coerceAtLeast(startWeek).coerceAtMost(defaultEndWeek),
            parity = parity,
        )
    }

    private fun inferName(text: String, location: String, teacher: String): String? =
        text.lineSequence()
            .flatMap { it.split(Regex("""[\s|｜,，;；]+""")).asSequence() }
            .map(String::trim)
            .filter { it.length >= 2 }
            .firstOrNull { token ->
                token != location &&
                    token != teacher &&
                    !periodRegex.containsMatchIn(token) &&
                    !weekRegex.containsMatchIn(token) &&
                    !weekdayRegex.containsMatchIn(token) &&
                    !teacherPrefixRegex.containsMatchIn(token) &&
                    !timeRegex.matches(token)
            }
            ?.take(32)

    private fun extractHeaderDay(text: String): Int? {
        val normalized = text.trim().replace(" ", "")
        val explicit = weekdayRegex.find(normalized)?.groupValues?.get(1)?.let(dayMap::get)
        return explicit
            ?: numberedWeekdayRegex.find(normalized)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..7 }
            ?: normalized.takeIf { it.length == 1 }?.let(dayMap::get)
    }

    private fun String.normalizeName(): String =
        lowercase().replace(Regex("""[\s·•_-]"""), "")

    private companion object {
        val weekdayRegex = Regex("""(?:星期|周)([一二三四五六日天])""")
        val numberedWeekdayRegex = Regex("""(?:星期|周)([1-7])""")
        val periodAxisRegex = Regex("""(?:第)?(\d{1,2})(?:节)?""")
        val periodRegex = Regex("""(?:第\s*)?(\d{1,2})(?:\s*[-~至—–]\s*(\d{1,2}))?\s*节""")
        val weekRegex = Regex("""第?\s*(\d{1,2})(?:\s*[-~至—–]\s*(\d{1,2}))?\s*周""")
        val locationRegex = Regex("""(?:[A-Za-z\u4e00-\u9fa5]{0,5}(?:楼|教|综|实|文|理|体)[A-Za-z]?\s*\d{2,4}|[A-Za-z]\d{2,4})""")
        val teacherRegex = Regex("""(?:教师|老师|任课)[:：]?\s*([\p{IsHan}A-Za-z·]{2,12})""")
        val teacherPrefixRegex = Regex("""(?:教师|老师|任课)[:：]?""")
        val timeRegex = Regex("""\d{1,2}[:：]\d{2}""")
        val dayMap = mapOf(
            "一" to 1,
            "二" to 2,
            "三" to 3,
            "四" to 4,
            "五" to 5,
            "六" to 6,
            "日" to 7,
            "天" to 7,
        )
        const val CELL_CONTEXT_ABOVE = 72
        const val CELL_CONTEXT_BELOW = 86
    }
}

package win.iqwqi.xiangece.domain.parser

import javax.inject.Inject
import kotlin.math.abs
import win.iqwqi.xiangece.core.ocr.OcrRegion
import win.iqwqi.xiangece.domain.model.WeekParity

/**
 * Parses the two selectable-text PDF layouts exported by the teaching system.
 *
 * TABLE is a weekly grid. It is parsed column-by-column using the real PDF X coordinates.
 * LIST is a record table. It is parsed row-by-row using the real PDF Y coordinates.
 * The two paths intentionally do not share heuristic fallbacks: mixing them was the main source
 * of plausible-looking but incorrect courses.
 */
class PdfTextTimetableParser @Inject constructor() {
    enum class Layout { AUTO, TABLE, LIST }

    private data class VisualRow(
        val items: List<OcrRegion>,
        val centerY: Int,
        val text: String,
    )

    private data class DayMarker(
        val day: Int,
        val centerY: Int,
    )

    fun parse(
        regions: List<OcrRegion>,
        defaultEndWeek: Int,
        layout: Layout = Layout.AUTO,
    ): List<TimetableCandidate> {
        if (regions.isEmpty()) return emptyList()
        return when (layout) {
            Layout.TABLE -> parseTable(regions, defaultEndWeek)
            Layout.LIST -> parseList(regions, defaultEndWeek)
            Layout.AUTO -> parseTable(regions, defaultEndWeek)
                .ifEmpty { parseList(regions, defaultEndWeek) }
        }
    }

    /** Weekly-grid PDF: recover each weekday independently, never by global reading order. */
    private fun parseTable(
        regions: List<OcrRegion>,
        defaultEndWeek: Int,
    ): List<TimetableCandidate> {
        val source = regions.filter { it.text.isNotBlank() }
        val headers = source.mapNotNull { region ->
            parseDayHeader(region.text)?.let { day -> day to region }
        }.distinctBy { it.first }.sortedBy { it.first }

        // A real weekly grid has several weekday headers on one horizontal band. The list PDF has
        // the same labels vertically, so this guard also makes AUTO layout selection deterministic.
        if (headers.size < 3) return emptyList()
        val headerY = headers.map { it.second.centerY }
        if (headerY.maxOrNull()!! - headerY.minOrNull()!! > TABLE_HEADER_Y_TOLERANCE) {
            return emptyList()
        }

        val centers = headers.map { it.second.centerX }
        val spacing = centers.zipWithNext { left, right -> right - left }
            .filter { it > 0 }
            .sorted()
            .let { values -> values.getOrElse(values.size / 2) { DEFAULT_DAY_COLUMN_WIDTH } }
        val headerBottom = headers.maxOf { it.second.bottom }

        fun belongsToColumn(region: OcrRegion, index: Int): Boolean {
            val left = if (index == 0) {
                centers[index] - spacing / 2
            } else {
                (centers[index - 1] + centers[index]) / 2
            }
            val right = if (index == centers.lastIndex) {
                centers[index] + spacing / 2
            } else {
                (centers[index] + centers[index + 1]) / 2
            }
            return region.centerX in left until right && region.bottom > headerBottom
        }

        return headers.flatMapIndexed { columnIndex, (day, _) ->
            val rows = buildRows(source.filter { belongsToColumn(it, columnIndex) })
            val anchors = rows.indices.filter { index ->
                explicitPeriodRegex.containsMatchIn(rows[index].text) &&
                    weekRegex.containsMatchIn(rows[index].text)
            }

            anchors.mapNotNull { anchorIndex ->
                val anchor = rows[anchorIndex]
                val period = explicitPeriodRegex.find(anchor.text) ?: return@mapNotNull null
                val startPeriod = period.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val endPeriod = period.groupValues.getOrNull(2)?.toIntOrNull() ?: startPeriod
                val inlineName = anchor.text.substring(0, period.range.first).cleanCourseName()
                val name = inlineName.takeIf { it.isCourseName() }
                    ?: findTableCourseName(rows, anchorIndex)
                    ?: return@mapNotNull null

                // Grid PDF cells do not label the location and teacher. Their stable order is:
                // schedule -> location -> teacher -> assessment. Read only the short block directly
                // below this schedule anchor; never merge a later course's schedule/parity into it.
                val recordRows = collectTableRecordRows(rows, anchorIndex)
                val recordText = recordRows.joinToString(" ") { it.text }
                val location = recordRows.asSequence()
                    .drop(1)
                    .map { it.text.compactField() }
                    .firstOrNull { it.looksLikeLocation() }
                    .orEmpty()
                val locationRowIndex = recordRows.indexOfFirst {
                    it.text.compactField().looksLikeLocation()
                }
                val teacher = if (locationRowIndex >= 0) {
                    recordRows.asSequence()
                        .drop(locationRowIndex + 1)
                        .map { it.text.compactField() }
                        .firstOrNull { it.looksLikeTeacher() }
                        .orEmpty()
                } else {
                    ""
                }
                createCandidate(
                    name = name,
                    day = day,
                    startPeriod = startPeriod,
                    endPeriod = endPeriod,
                    scheduleText = anchor.text,
                    recordText = recordText,
                    defaultEndWeek = defaultEndWeek,
                    locationOverride = location,
                    teacherOverride = teacher,
                )
            }
        }.distinctCandidates()
    }

    /**
     * Teaching-system list PDF. Every course is one visual record row with three logical fields:
     * period, course name, and `周数/地点/教师`. Weekday cells are vertically merged, so a course
     * belongs to the nearest weekday marker by Y, not the nearest text in the PDF stream.
     */
    private fun parseList(
        regions: List<OcrRegion>,
        defaultEndWeek: Int,
    ): List<TimetableCandidate> {
        val source = regions.filter { it.text.isNotBlank() }
        val markers = source.mapNotNull { region ->
            parseDayHeader(region.text)?.let { day -> DayMarker(day, region.centerY) }
        }.distinctBy { it.day }.sortedBy { it.centerY }
        if (markers.isEmpty()) return emptyList()

        val rows = buildRows(source)
        val periodTokens = source.mapNotNull { region ->
            barePeriodRegex.matchEntire(region.text.normalized())?.let { match -> region to match }
        }

        return rows.mapNotNull { row ->
            if (!row.text.contains("周数") || !weekRegex.containsMatchIn(row.text)) {
                return@mapNotNull null
            }
            val metadataIndex = row.text.indexOf("周数")
            if (metadataIndex <= 0) return@mapNotNull null

            val prefix = row.text.substring(0, metadataIndex)
                .replace(dayHeaderAnywhereRegex, " ")
                .trim()
                .removeLeadingBarePeriod()
            val name = prefix.cleanCourseName()
            if (!name.isCourseName()) return@mapNotNull null

            val rowPeriod = row.items.asSequence()
                .mapNotNull { item -> barePeriodRegex.matchEntire(item.text.normalized()) }
                .firstOrNull()
            val nearestPeriod = rowPeriod ?: periodTokens.asSequence()
                .filter { (region, _) -> abs(region.centerY - row.centerY) <= LIST_PERIOD_Y_RADIUS }
                .minByOrNull { (region, _) -> abs(region.centerY - row.centerY) }
                ?.second
                ?: return@mapNotNull null
            val startPeriod = nearestPeriod.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val endPeriod = nearestPeriod.groupValues.getOrNull(2)?.toIntOrNull() ?: startPeriod
            val day = markers.minByOrNull { marker -> abs(marker.centerY - row.centerY) }?.day
                ?: return@mapNotNull null
            val recordText = row.text.substring(metadataIndex)

            createCandidate(
                name = name,
                day = day,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                scheduleText = recordText,
                recordText = recordText,
                defaultEndWeek = defaultEndWeek,
            )
        }.distinctCandidates()
    }

    private fun findTableCourseName(rows: List<VisualRow>, anchorIndex: Int): String? {
        val fragments = mutableListOf<String>()
        var previousY = rows[anchorIndex].centerY
        var index = anchorIndex - 1
        while (index >= 0 && fragments.size < MAX_NAME_ROWS) {
            val row = rows[index]
            if (previousY - row.centerY > TABLE_LINE_GAP) break
            if (explicitPeriodRegex.containsMatchIn(row.text) || row.text.isMetadataContinuation()) {
                break
            }
            val fragment = row.text.cleanNameFragment()
            if (fragment.isNotBlank() &&
                (fragment.any { it.isLetter() || it in '\u4e00'..'\u9fff' } ||
                    fragment == ")" || fragment == "）")
            ) {
                fragments.add(0, fragment)
            }
            previousY = row.centerY
            index--
        }
        return fragments.joinToString("").cleanCourseName().takeIf { it.isCourseName() }
    }

    private fun collectTableRecordRows(
        rows: List<VisualRow>,
        anchorIndex: Int,
    ): List<VisualRow> {
        val result = mutableListOf(rows[anchorIndex])
        var previousY = rows[anchorIndex].centerY
        var index = anchorIndex + 1
        while (index < rows.size && result.size < MAX_TABLE_RECORD_ROWS) {
            val row = rows[index]
            if (row.centerY - previousY > TABLE_LINE_GAP) break
            if (explicitPeriodRegex.containsMatchIn(row.text) && weekRegex.containsMatchIn(row.text)) {
                break
            }
            result += row
            previousY = row.centerY

            // A complete unlabeled metadata block has been recovered. Stop before a following
            // course name can contaminate teacher/location fields.
            val compactRows = result.drop(1).map { it.text.compactField() }
            val locationIndex = compactRows.indexOfFirst { it.looksLikeLocation() }
            if (locationIndex >= 0 && compactRows.drop(locationIndex + 1).any { it.looksLikeTeacher() }) {
                break
            }
            index++
        }
        return result
    }

    private fun createCandidate(
        name: String,
        day: Int,
        startPeriod: Int,
        endPeriod: Int,
        scheduleText: String,
        recordText: String,
        defaultEndWeek: Int,
        locationOverride: String = "",
        teacherOverride: String = "",
    ): TimetableCandidate? {
        val week = weekRegex.find(scheduleText) ?: return null
        val parsedStartWeek = week.groupValues[1].toIntOrNull() ?: return null
        val parsedEndWeek = week.groupValues.getOrNull(2)?.toIntOrNull() ?: parsedStartWeek
        val startWeek = parsedStartWeek.coerceAtLeast(1).coerceAtMost(defaultEndWeek)
        val endWeek = parsedEndWeek.coerceAtLeast(startWeek).coerceAtMost(defaultEndWeek)
        val parity = when {
            evenWeekRegex.containsMatchIn(scheduleText) -> WeekParity.EVEN
            oddWeekRegex.containsMatchIn(scheduleText) -> WeekParity.ODD
            else -> WeekParity.ALL
        }
        return TimetableCandidate(
            name = name.take(MAX_COURSE_NAME_LENGTH),
            teacher = teacherOverride.ifBlank {
                teacherRegex.find(recordText)?.groupValues?.getOrNull(1).orEmpty().compactField()
            },
            location = locationOverride.ifBlank {
                locationRegex.find(recordText)?.groupValues?.getOrNull(1).orEmpty().compactField()
            },
            dayOfWeek = day,
            startPeriod = startPeriod,
            endPeriod = endPeriod.coerceAtLeast(startPeriod),
            startWeek = startWeek,
            endWeek = endWeek,
            parity = parity,
        )
    }

    private fun buildRows(regions: List<OcrRegion>): List<VisualRow> {
        val groups = mutableListOf<MutableList<OcrRegion>>()
        regions.sortedWith(compareBy<OcrRegion> { it.centerY }.thenBy { it.left }).forEach { region ->
            val group = groups.lastOrNull()
            val groupY = group?.map { it.centerY }?.average()?.toInt()
            if (group == null || groupY == null || abs(groupY - region.centerY) > ROW_Y_TOLERANCE) {
                groups.add(mutableListOf(region))
            } else {
                group += region
            }
        }
        return groups.map { group ->
            val ordered = group.sortedBy { it.left }
            VisualRow(
                items = ordered,
                centerY = ordered.map { it.centerY }.average().toInt(),
                text = ordered.joinToString(" ") { it.text.trim() }.trim(),
            )
        }
    }

    private fun parseDayHeader(value: String): Int? {
        val match = dayHeaderRegex.matchEntire(value.normalized()) ?: return null
        return dayMap[match.groupValues[1]]
    }

    private fun String.removeLeadingBarePeriod(): String {
        val trimmed = trim()
        val match = barePeriodAtStartRegex.find(trimmed) ?: return trimmed
        return trimmed.removeRange(match.range).trim()
    }

    private fun String.cleanNameFragment(): String = replace(COURSE_SYMBOLS, "")
        .replace(Regex("\\s+"), "")
        .trim(' ', ':', '：')

    private fun String.cleanCourseName(): String = cleanNameFragment()
        .replace(dayHeaderAnywhereRegex, "")
        .replace(STRUCTURAL_PREFIXES, "")
        .trim(' ', ':', '：', '/', '|')

    private fun String.isCourseName(): Boolean = length >= 2 &&
        any { it.isLetter() || it in '\u4e00'..'\u9fff' } &&
        STRUCTURAL_LABELS.none { label -> this == label || startsWith("$label:") || startsWith("$label：") } &&
        !contains("周数") && !contains("教师") && !contains("场地") && !contains("地点") &&
        !contains("考核方式") && !contains("打印时间") && !contains("课表")

    private fun String.looksLikeCourseNameLine(): Boolean = cleanCourseName().isCourseName() &&
        !isMetadataContinuation() &&
        !explicitPeriodRegex.containsMatchIn(this) &&
        !weekRegex.containsMatchIn(this)

    private fun String.isMetadataContinuation(): Boolean {
        val value = normalized()
        return value.startsWith(":") || value.startsWith("：") ||
            value.startsWith("方式") || value.startsWith("理楼") || value.startsWith("楼") ||
            value.contains("教师:") || value.contains("教师：") ||
            value.contains("考核方式") || value.matches(Regex("^\\d{3,4}/.*"))
    }

    private fun String.normalized(): String = replace(Regex("\\s+"), "").trim()

    private fun String.compactField(): String = replace(Regex("\\s+"), "")
        .trim(' ', '/', ':', '：')
        .take(MAX_FIELD_LENGTH)

    private fun String.looksLikeLocation(): Boolean {
        val value = compactField()
        if (value.length !in 2..MAX_FIELD_LENGTH) return false
        if (value.contains("周") || value.contains("节") || value.contains("教师")) return false
        return LOCATION_VALUE_REGEX.containsMatchIn(value) ||
            LOCATION_WORDS.any(value::contains)
    }

    private fun String.looksLikeTeacher(): Boolean {
        val value = compactField().removePrefix("教师").trim(':', '：')
        return value.length in 2..8 &&
            value.all { it in '\u4e00'..'\u9fff' || it == '·' } &&
            !value.looksLikeLocation() &&
            STRUCTURAL_LABELS.none(value::contains)
    }

    private fun List<TimetableCandidate>.distinctCandidates(): List<TimetableCandidate> = distinctBy {
        listOf(it.name, it.dayOfWeek, it.startPeriod, it.endPeriod, it.startWeek, it.endWeek, it.parity)
    }

    private companion object {
        const val TABLE_HEADER_Y_TOLERANCE = 12
        const val DEFAULT_DAY_COLUMN_WIDTH = 104
        const val ROW_Y_TOLERANCE = 5
        const val TABLE_LINE_GAP = 20
        const val MAX_NAME_ROWS = 5
        const val MAX_TABLE_RECORD_ROWS = 5
        const val LIST_PERIOD_Y_RADIUS = 26
        const val MAX_COURSE_NAME_LENGTH = 64
        const val MAX_FIELD_LENGTH = 40

        val dayMap = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "日" to 7, "天" to 7,
        )
        val dayHeaderRegex = Regex("(?:星期|周)([一二三四五六日天])")
        val dayHeaderAnywhereRegex = Regex("(?:星期|周)\\s*[一二三四五六日天]")
        val explicitPeriodRegex = Regex(
            "[（(]?\\s*(\\d{1,2})\\s*[-~至—–]\\s*(\\d{1,2})\\s*节\\s*[）)]?",
        )
        val barePeriodRegex = Regex("(\\d{1,2})\\s*[-~至—–]\\s*(\\d{1,2})")
        val barePeriodAtStartRegex = Regex("^\\s*\\d{1,2}\\s*[-~至—–]\\s*\\d{1,2}(?:\\s*节)?")
        val weekRegex = Regex("(?:第\\s*)?(\\d{1,2})(?:\\s*[-~至—–]\\s*(\\d{1,2}))?\\s*周")
        val oddWeekRegex = Regex("(?:单周|[（(]\\s*单\\s*[）)])")
        val evenWeekRegex = Regex("(?:双周|[（(]\\s*双\\s*[）)])")
        val locationRegex = Regex(
            "(?:场地|地点|教室)\\s*[:：]\\s*([\\s\\S]*?)(?=\\s*/\\s*教师\\s*[:：]|\\s*/\\s*考核|$)",
        )
        val teacherRegex = Regex(
            "教师\\s*[:：]\\s*([\\s\\S]*?)(?=\\s*/\\s*考核|$)",
        )
        val COURSE_SYMBOLS = Regex("[☆★▲※◆◇△●]")
        val LOCATION_VALUE_REGEX = Regex("(?:楼|馆|教室|校区|实验室|操场|场地).{0,12}\\d{0,6}|\\d{3,6}$")
        val LOCATION_WORDS = listOf("明理楼", "博文楼", "求真楼", "跆拳道馆", "体育馆")
        val STRUCTURAL_PREFIXES = Regex(
            "^(?:(?:时间段|节次|上午|下午|晚上|考试|方式|考核方式)\\s*[:：]?)+",
        )
        val STRUCTURAL_LABELS = setOf(
            "时间段", "节次", "上午", "下午", "晚上", "周数", "场地", "地点",
            "教师", "教室", "考试", "方式", "考核方式", "实践课程", "其他课程",
        )
    }
}

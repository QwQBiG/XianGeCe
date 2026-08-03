package win.iqwqi.xiangece.core.importing

import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import win.iqwqi.xiangece.domain.model.WeekParity
import win.iqwqi.xiangece.domain.parser.TimetableCandidate
import win.iqwqi.xiangece.domain.parser.TimetableTextParser

@Singleton
class TabularTimetableImporter @Inject constructor(
    private val textParser: TimetableTextParser,
) {
    fun parseHtml(file: File, defaultEndWeek: Int): List<TimetableCandidate> {
        val html = file.readText()
        val rows = Regex("""(?is)<tr\b[^>]*>(.*?)</tr>""").findAll(html).map { row ->
            Regex("""(?is)<t[dh]\b[^>]*>(.*?)</t[dh]>""").findAll(row.groupValues[1])
                .map { htmlText(it.groupValues[1]) }
                .toList()
        }.filter { it.isNotEmpty() }.toList()
        return parseGrid(rows, defaultEndWeek).ifEmpty {
            textParser.parse(htmlText(html), defaultEndWeek)
        }
    }

    fun parseSpreadsheet(file: File, defaultEndWeek: Int): List<TimetableCandidate> {
        val rows = when (file.extension.lowercase()) {
            "csv", "tsv" -> parseDelimited(file)
            "xlsx" -> parseXlsx(file)
            else -> error("暂不支持旧版 .xls，请在 Excel 中另存为 .xlsx 或 .csv")
        }
        return parseGrid(rows, defaultEndWeek)
    }

    fun parseGrid(rows: List<List<String>>, defaultEndWeek: Int): List<TimetableCandidate> {
        val headerIndex = rows.indexOfFirst { row -> row.count { weekday(it) != null } >= 3 }
        if (headerIndex < 0) return emptyList()
        val header = rows[headerIndex]
        val dayColumns = header.mapIndexedNotNull { index, cell -> weekday(cell)?.let { index to it } }.toMap()
        return rows.drop(headerIndex + 1).flatMap { row ->
            val periodCell = row.firstOrNull().orEmpty()
            val range = periodRange(periodCell) ?: return@flatMap emptyList()
            dayColumns.mapNotNull { (column, day) ->
                val cell = row.getOrNull(column).orEmpty().trim()
                if (cell.isBlank()) return@mapNotNull null
                candidateFromCell(cell, day, range.first, range.second, defaultEndWeek)
            }
        }.distinctBy { listOf(it.name, it.dayOfWeek, it.startPeriod, it.startWeek, it.parity) }
    }

    private fun candidateFromCell(
        cell: String,
        day: Int,
        startPeriod: Int,
        endPeriod: Int,
        defaultEndWeek: Int,
    ): TimetableCandidate? {
        val lines = cell.lines().map(String::trim).filter(String::isNotBlank)
        val name = lines.firstOrNull()
            ?.replace(Regex("""第?\d{1,2}(?:[-–—~至]\d{1,2})?周.*"""), "")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null
        val week = Regex("""第?\s*(\d{1,2})\s*(?:[-–—~至]\s*(\d{1,2}))?\s*周""")
            .find(cell)
        val startWeek = week?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val endWeek = week?.groupValues?.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 } ?: defaultEndWeek
        val parity = when {
            "单周" in cell -> WeekParity.ODD
            "双周" in cell -> WeekParity.EVEN
            else -> WeekParity.ALL
        }
        val location = lines.firstOrNull {
            Regex("""(?:教室|地点|楼|馆|苑|区|校区|[A-Za-z]\s*\d{2,4})""").containsMatchIn(it)
        }.orEmpty()
        val teacher = lines.firstOrNull {
            it != name && it != location && !it.contains("周") && it.length in 2..12
        }.orEmpty()
        return TimetableCandidate(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = day,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            startWeek = startWeek.coerceAtLeast(1),
            endWeek = endWeek.coerceAtLeast(startWeek),
            parity = parity,
        )
    }

    private fun parseDelimited(file: File): List<List<String>> {
        val separator = if (file.extension.equals("tsv", true)) '\t' else ','
        return file.readLines().map { line ->
            val cells = mutableListOf<String>()
            val value = StringBuilder()
            var quoted = false
            var index = 0
            while (index < line.length) {
                val char = line[index]
                when {
                    char == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                        value.append('"')
                        index++
                    }
                    char == '"' -> quoted = !quoted
                    char == separator && !quoted -> {
                        cells += value.toString()
                        value.clear()
                    }
                    else -> value.append(char)
                }
                index++
            }
            cells + value.toString()
        }
    }

    private fun parseXlsx(file: File): List<List<String>> = ZipFile(file).use { zip ->
        val sharedStrings = zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
            val document = xmlBuilder().parse(zip.getInputStream(entry))
            document.descendantElements("si").let { nodes ->
                nodes.map { element ->
                    element.descendantElements("t").joinToString("") { it.textContent }
                }
            }
        }.orEmpty()
        val sheetEntry = zip.entries().asSequence()
            .filter { it.name.matches(Regex("""xl/worksheets/sheet\d+\.xml""")) }
            .sortedBy { it.name }
            .firstOrNull()
            ?: error("Excel 文件中没有工作表")
        val document = xmlBuilder().parse(zip.getInputStream(sheetEntry))
        val cells = document.descendantElements("c")
        val grid = mutableMapOf<Pair<Int, Int>, String>()
        var maxRow = 0
        var maxColumn = 0
        for (cell in cells) {
            val ref = cell.getAttribute("r")
            val row = ref.dropWhile(Char::isLetter).toIntOrNull()?.minus(1) ?: continue
            val column = columnIndex(ref.takeWhile(Char::isLetter))
            val raw = cell.descendantElements("v").firstOrNull()?.textContent.orEmpty()
            val value = when (cell.getAttribute("t")) {
                "s" -> sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                "inlineStr" -> cell.descendantElements("t").firstOrNull()?.textContent.orEmpty()
                else -> raw
            }
            grid[row to column] = value
            maxRow = maxOf(maxRow, row)
            maxColumn = maxOf(maxColumn, column)
        }
        (0..maxRow).map { row -> (0..maxColumn).map { column -> grid[row to column].orEmpty() } }
    }

    private fun columnIndex(value: String): Int =
        value.uppercase().fold(0) { acc, char -> acc * 26 + (char - 'A' + 1) } - 1

    private fun xmlBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder()

    private fun Node.descendantElements(localName: String): List<Element> {
        val nodes = when (this) {
            is Document -> getElementsByTagNameNS("*", localName)
            is Element -> getElementsByTagNameNS("*", localName)
            else -> return emptyList()
        }
        return List(nodes.length) { index -> nodes.item(index) as Element }
    }

    private fun htmlText(value: String): String = value
        .replace(Regex("""(?is)<br\s*/?>"""), "\n")
        .replace(Regex("""(?is)</(?:p|div|li)>"""), "\n")
        .replace(Regex("""(?is)<script\b.*?</script>|<style\b.*?</style>"""), "")
        .replace(Regex("""(?is)<[^>]+>"""), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .trim()

    private fun weekday(value: String): Int? {
        val text = value.replace("星期", "").replace("周", "").trim()
        return when {
            text.startsWith("一") || text.equals("Mon", true) -> 1
            text.startsWith("二") || text.equals("Tue", true) -> 2
            text.startsWith("三") || text.equals("Wed", true) -> 3
            text.startsWith("四") || text.equals("Thu", true) -> 4
            text.startsWith("五") || text.equals("Fri", true) -> 5
            text.startsWith("六") || text.equals("Sat", true) -> 6
            text.startsWith("日") || text.startsWith("天") || text.equals("Sun", true) -> 7
            else -> null
        }
    }

    private fun periodRange(value: String): Pair<Int, Int>? {
        val numbers = Regex("""\d{1,2}""").findAll(value).mapNotNull { it.value.toIntOrNull() }.toList()
        val start = numbers.firstOrNull()?.takeIf { it in 1..24 } ?: return null
        return start to (numbers.getOrNull(1)?.takeIf { it in start..20 } ?: start)
    }
}

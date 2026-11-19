package win.iqwqi.xiangece.core.ai

import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import win.iqwqi.xiangece.domain.model.DraftType
import win.iqwqi.xiangece.domain.model.ParsedDraft

data class DailyExtraction(val sourceText: String, val drafts: List<ParsedDraft>)

@Serializable
private data class DailyResponse(val sourceText: String = "", val items: List<DailyItem> = emptyList())

@Serializable
private data class DailyItem(
    val type: String = "NOTE",
    val title: String = "",
    val description: String = "",
    val sourceQuote: String = "",
    val dateTime: String? = null,
    val dateEvidence: String = "",
    val location: String? = null,
    val ambiguities: List<String> = emptyList(),
)

/** Model dates are suggestions; quotes and explicit date/time evidence are required. */
object DailyExtractionDecoder {
    private val json = Json { ignoreUnknownKeys = true }
    fun decode(content: String, originalText: String? = null, zone: ZoneId = ZoneId.systemDefault()): DailyExtraction {
        val clean = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val response = json.decodeFromString<DailyResponse>(clean)
        val source = (originalText ?: response.sourceText).trim()
        require(source.isNotBlank()) { "未返回可核对的识别原文" }
        require(response.items.size in 1..16) { "请分批整理，每次最多 16 件事项" }
        val compactSource = compact(source)
        val drafts = response.items.mapNotNull { item ->
            val quote = compact(item.sourceQuote)
            if (item.title.isBlank() || quote.length < 2 || !compactSource.contains(quote)) return@mapNotNull null
            val issues = item.ambiguities.filter(String::isNotBlank).toMutableList()
            val evidence = compact(item.dateEvidence)
            val parsedTime = item.dateTime?.let { runCatching { LocalDateTime.parse(it.replace(' ', 'T')) }.getOrNull() }
            val epoch = if (parsedTime != null && evidence.isNotBlank() && quote.contains(evidence) && matchesDateEvidence(evidence, parsedTime)) {
                if (!Regex("20\\d{2}[年/.-]").containsMatchIn(evidence)) issues += "原文未写年份，请确认年份"
                parsedTime.atZone(zone).toInstant().toEpochMilli()
            } else {
                if (!item.dateTime.isNullOrBlank() || evidence.isNotBlank()) issues += "日期或时刻缺少完整原文依据，请手动确认"
                null
            }
            val type = when (item.type) {
                "TASK" -> DraftType.TASK
                "EVENT" -> DraftType.EVENT
                else -> DraftType.NOTE
            }
            if (type == DraftType.EVENT && epoch == null) issues += "事件日期与时间待确认"
            if (!item.location.isNullOrBlank() && !compactSource.contains(compact(item.location))) {
                issues += "地点未能在原文中核对，请确认或修改"
            }
            ParsedDraft(
                type = type,
                title = item.title.trim().take(160),
                description = item.description.trim().ifBlank { item.sourceQuote },
                dateTimeEpochMillis = epoch,
                location = item.location?.trim()?.takeIf(String::isNotBlank),
                confidence = 0f,
                ambiguities = issues.distinct(),
            )
        }.distinctBy { listOf(it.type, it.title, it.dateTimeEpochMillis, it.location) }
        require(drafts.isNotEmpty()) { "未找到有原文依据的事项，已保留原始输入" }
        val checked = if (drafts.size < response.items.size) drafts.map {
            it.copy(ambiguities = (it.ambiguities + "已合并重复项或略去无法核对的候选，请对照原文检查遗漏").distinct())
        } else drafts
        return DailyExtraction(source, checked)
    }

    private fun matchesDateEvidence(evidence: String, time: LocalDateTime): Boolean {
        val dates = Regex("(?:(20\\d{2})[年/.-])?(\\d{1,2})[月/.-](\\d{1,2})").findAll(evidence).toList()
        if (dates.size != 1) return false
        val date = dates.single().groupValues
        if (date[1].isNotEmpty() && date[1].toInt() != time.year) return false
        if (date[2].toInt() != time.monthValue || date[3].toInt() != time.dayOfMonth) return false
        val clocks = Regex("(\\d{1,2})[:：](\\d{2})|(\\d{1,2})[点时](半|\\d{1,2}分?)?").findAll(evidence).toList()
        if (clocks.size != 1) return false
        val clock = clocks.single()
        val parts = clock.groupValues
        var hour = parts[1].ifEmpty { parts[3] }.toInt()
        val minute = if (parts[1].isNotEmpty()) parts[2].toInt() else when (parts[4]) {
            "半" -> 30
            "" -> 0
            else -> parts[4].removeSuffix("分").toInt()
        }
        val prefix = evidence.take(clock.range.first)
        if (("下午" in prefix || "晚上" in prefix || "晚间" in prefix) && hour in 1..11) hour += 12
        if ("凌晨" in prefix && hour == 12) hour = 0
        return hour == time.hour && minute == time.minute
    }

    private fun compact(value: String) = value.filterNot(Char::isWhitespace)
}

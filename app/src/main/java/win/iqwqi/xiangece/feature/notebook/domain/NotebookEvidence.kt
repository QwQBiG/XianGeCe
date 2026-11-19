package win.iqwqi.xiangece.feature.notebook.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import win.iqwqi.xiangece.feature.notebook.data.*

@Serializable data class EvidenceRef(val id: String, val quote: String)
@Serializable data class GroundedPoint(val text: String, val evidence: List<EvidenceRef> = emptyList())
@Serializable data class GroundedResponse(val points: List<GroundedPoint> = emptyList(), val missing: String = "")
@Serializable data class QueryExpansion(val terms: List<String> = emptyList())
@Serializable data class NotePassage(val id: String, val noteId: String, val title: String,
    val revision: String, val start: Int, val text: String)
data class CheckedResponse(val points: List<GroundedPoint>, val missing: String, val rejected: Int)

object NotebookEvidence {
    private val json = Json { ignoreUnknownKeys = true }
    fun cleaned(raw: String) = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    fun expansion(raw: String): List<String> = json.decodeFromString<QueryExpansion>(cleaned(raw)).terms
        .map { it.trim() }.filter { it.length in 2..40 }.distinct().take(8)
    fun check(raw: String, supplied: Map<String, String>): CheckedResponse {
        require(raw.length <= 60_000) { "返回内容过长，请缩小整理范围" }
        val response = json.decodeFromString<GroundedResponse>(cleaned(raw))
        val accepted = response.points.take(16).filter { point ->
            point.text.isNotBlank() && point.text.length <= 1800 && point.evidence.size in 1..4 &&
                point.evidence.all { ref -> ref.quote.length in 4..1000 && supplied[ref.id]?.contains(ref.quote) == true }
        }.distinctBy { it.text }
        return CheckedResponse(accepted, response.missing.take(600), response.points.size - accepted.size)
    }
    fun sourceText(source: NoteSource): Map<String, String> = source.blocks.associate { it.id to it.text }
    fun passages(notes: List<NoteDocument>, query: String, expansions: List<String>): List<NotePassage> {
        val index = NotebookSearch(notes)
        val ranked = index.search(query, expansions).take(10).map { it.note }
        val words = (listOf(query) + expansions + NotebookSearch.terms(query)).filter { it.length > 1 }
        return ranked.flatMap { note ->
            note.text.chunked(1200).mapIndexed { i, text ->
                val score = words.sumOf { word -> if (text.contains(word, true)) word.length else 0 }
                NotePassage("${note.record.id}-$i", note.record.id, note.record.title, note.revision, i * 1200, text) to score
            }.sortedByDescending { it.second }.take(2).map { it.first }
        }.take(16)
    }
    fun stillCurrent(passage: NotePassage, current: NoteDocument?) = current != null &&
        current.record.deletedAt == null && current.revision == passage.revision &&
        current.text.substring(passage.start.coerceAtMost(current.text.length)).startsWith(passage.text)

    fun collectText(text: String): List<NoteSourceBlock> {
        require(text.length <= 100_000) { "一次最多收集 10 万字，请分段保存" }
        return text.chunked(1800).mapIndexed { i, part -> NoteSourceBlock("p${i + 1}", part) }
    }
    fun markdown(title: String, source: NoteSource, points: List<GroundedPoint>): String = buildString {
        append("# ").append(title.replace('\n', ' ')).append("\n\n")
        if (points.isNotEmpty()) {
            append("## 整理\n\n")
            points.forEach { point ->
                append(point.text).append("\n\n")
                point.evidence.forEach { ref ->
                    val block = source.blocks.firstOrNull { it.id == ref.id }
                    val label = block?.noteTitle ?: block?.startMillis?.let(::timeLabel) ?: "片段 ${source.blocks.indexOf(block) + 1}"
                    append("> 依据 · ").append(label).append("\n")
                    ref.quote.lineSequence().forEach { append("> ").append(it).append('\n') }
                    append('\n')
                }
            }
        }
        source.imagePath?.let { append("![原图](../").append(it).append(")\n\n") }
        append("## 原始材料\n\n")
        source.blocks.forEachIndexed { index, block ->
            append("### ").append(block.noteTitle ?: block.startMillis?.let(::timeLabel) ?: "片段 ${index + 1}").append("\n\n")
            block.text.lineSequence().forEach { append("> ").append(it).append('\n') }
            append('\n')
        }
    }
    fun timeLabel(ms: Long) = "%02d:%02d".format(ms / 60_000, ms / 1000 % 60)
}

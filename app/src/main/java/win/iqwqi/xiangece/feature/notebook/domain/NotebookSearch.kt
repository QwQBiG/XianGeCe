package win.iqwqi.xiangece.feature.notebook.domain

import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.sqrt
import win.iqwqi.xiangece.feature.notebook.data.NoteDocument

data class NoteMatch(val note: NoteDocument, val score: Double, val excerpt: String, val reason: String)

/** Local BM25 ranking, with a separate lower-weight expansion channel. No generated search results. */
class NotebookSearch(private val notes: List<NoteDocument>) {
    private data class Entry(val note: NoteDocument, val info: MarkdownInfo, val terms: Map<String, Int>, val title: Set<String>)
    private val entries = notes.map { note ->
        val info = NotebookMarkdown.info(note.text)
        Entry(note, info, terms(info.plainText).groupingBy { it }.eachCount(), terms(note.record.title).toSet())
    }
    private val frequency = entries.flatMap { it.terms.keys + it.title }.groupingBy { it }.eachCount()
    private val average = entries.map { it.terms.values.sum() }.average().takeIf { !it.isNaN() && it > 0 } ?: 1.0
    private fun idf(term: String) = ln(1 + (entries.size - (frequency[term] ?: 0) + 0.5) / ((frequency[term] ?: 0) + 0.5))

    fun search(query: String, expansions: List<String> = emptyList()): List<NoteMatch> {
        if (query.isBlank()) return emptyList()
        val original = terms(query).distinct()
        val expanded = expansions.flatMap(::terms).distinct().filter { it !in original }
        val tagQuery = query.trim().takeIf { it.startsWith('#') }?.drop(1)
        return entries.mapNotNull { entry ->
            if (tagQuery != null && entry.info.tags.none { it.equals(tagQuery, true) }) return@mapNotNull null
            fun score(tokens: List<String>): Double = tokens.sumOf { term ->
                val count = entry.terms[term] ?: 0
                val length = entry.terms.values.sum()
                idf(term) * (if (count > 0) count * 2.2 / (count + 1.2 * (0.25 + 0.75 * length / average)) else 0.0) +
                    (if (term in entry.title) 3 * idf(term) else 0.0)
            }
            val direct = score(original)
            val related = score(expanded) * 0.42
            val exactTitle = if (entry.note.record.title.contains(query, true)) 8.0 else 0.0
            val exactText = if (entry.info.plainText.contains(query, true)) 4.0 else 0.0
            val result = direct + related + exactTitle + exactText
            if (result <= 0.0) null else NoteMatch(entry.note, result,
                excerpt(entry.info.plainText, listOf(query) + original + expansions),
                if (direct > 0 || exactText > 0 || exactTitle > 0) "正文或标题匹配" else "联想词匹配：${expansions.filter { word -> terms(word).any { it in entry.terms || it in entry.title } }.take(3).joinToString("、")}")
        }.sortedByDescending { it.score }
    }

    fun backlinks(note: NoteDocument): List<NoteMatch> = entries.filter { entry ->
        entry.note.record.id != note.record.id && entry.info.links.any {
            NotebookMarkdown.resolve(it, entry.note, notes).singleOrNull()?.record?.id == note.record.id
        }
    }.map { NoteMatch(it.note, 1.0, excerpt(it.info.plainText, listOf(note.record.title)), "链接到这里") }

    fun related(note: NoteDocument): List<NoteMatch> {
        val source = entries.firstOrNull { it.note.record.id == note.record.id } ?: return emptyList()
        fun vector(entry: Entry) = entry.terms.mapValues { (term, count) -> (1 + ln(count.toDouble())) * idf(term) }
        val a = vector(source)
        val aNorm = sqrt(a.values.sumOf { it * it })
        return entries.filter { it.note.record.id != note.record.id }.mapNotNull { other ->
            val shared = source.info.tags.intersect(other.info.tags)
            val b = vector(other)
            val norm = aNorm * sqrt(b.values.sumOf { it * it })
            val overlap = if (norm > 0) a.entries.sumOf { (term, value) -> value * (b[term] ?: 0.0) } / norm else 0.0
            if (shared.isEmpty() && overlap < 0.12) return@mapNotNull null
            val words = a.keys.intersect(b.keys).sortedByDescending { idf(it) * a.getValue(it) }.take(4)
            NoteMatch(other.note, overlap + shared.size * 0.3, excerpt(other.info.plainText, words),
                if (shared.isNotEmpty()) "共同标签：${shared.joinToString("、") { "#$it" }}" else "共同内容：${words.joinToString("、")}")
        }.sortedByDescending { it.score }.take(6)
    }

    companion object {
        private val stop = setOf("的", "了", "是", "在", "和", "我", "你", "与", "以及", "这个", "一个", "我们", "可以", "the", "and", "of", "to", "a", "is", "in")
        fun terms(text: String): List<String> = buildList {
            val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
            Regex("[a-z0-9_]+|[\\p{IsHan}]+").findAll(normalized).forEach { match ->
                val word = match.value
                if (word.first() in 'a'..'z' || word.first().isDigit()) { if (word !in stop) add(word) }
                else if (word.length == 1) { if (word !in stop) add(word) }
                else word.windowed(2).filter { it !in stop }.forEach(::add)
            }
        }
        private fun excerpt(text: String, terms: List<String>): String {
            val first = terms.firstNotNullOfOrNull { term -> text.indexOf(term, ignoreCase = true).takeIf { it >= 0 } } ?: 0
            val start = (first - 30).coerceAtLeast(0)
            return (if (start > 0) "…" else "") + text.drop(start).take(150) + if (text.length > start + 150) "…" else ""
        }
    }
}

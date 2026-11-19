package win.iqwqi.xiangece.feature.notebook.domain

import org.commonmark.node.*
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Paths
import win.iqwqi.xiangece.feature.notebook.data.NoteDocument

/** Change destinations only, preserving the original Markdown formatting and code examples. */
object NotebookLinkRewriter {
    private val parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build()
    private data class Edit(val start: Int, val end: Int, val value: String)

    fun portable(source: NoteDocument, notes: List<NoteDocument>): String = rewrite(source.text) { target, wiki, image ->
        if (image) null else {
            val match = NotebookMarkdown.resolve(NoteLink(target, "", wiki), source, notes).singleOrNull()
            match?.let { note ->
                val suffix = if ('#' in target) "#" + target.substringAfter('#') else ""
                if (wiki) note.record.path.removeSuffix(".md") + suffix
                else encode(relative(source.record.folder, note.record.path)) + suffix
            }
        }
    }

    fun relocate(source: NoteDocument, newFolder: String, notes: List<NoteDocument>): String = rewrite(source.text) { target, wiki, image ->
        if (wiki || target.startsWith('#') || Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(target)) null
        else {
            val path = runCatching { URLDecoder.decode(target.substringBefore('#').replace("+", "%2B"), "UTF-8") }.getOrDefault(target)
            val resolved = if (!image) NotebookMarkdown.resolve(NoteLink(target, "", false), source, notes).singleOrNull()?.record?.path else null
            val absolute = resolved ?: Paths.get(source.record.folder, path).normalize().toString().replace('\\', '/')
            encode(relative(newFolder, absolute)) + if ('#' in target) "#" + target.substringAfter('#') else ""
        }
    }
    private fun relative(folder: String, path: String) = Paths.get(folder.ifBlank { "." }).normalize()
        .relativize(Paths.get(path).normalize()).toString().replace('\\', '/')
    private fun encode(path: String) = URI(null, null, path, null).toASCIIString()

    private fun rewrite(text: String, transform: (String, Boolean, Boolean) -> String?): String {
        val edits = mutableListOf<Edit>()
        fun destination(node: Node, target: String, reference: Boolean = false, image: Boolean = false) {
            val spans = node.sourceSpans
            if (spans.isEmpty()) return
            val start = spans.first().inputIndex
            val end = spans.last().inputIndex + spans.last().length
            val raw = text.substring(start, end)
            val after = if (reference) raw.indexOf("]:").takeIf { it >= 0 }?.plus(2) else raw.indexOf("](").takeIf { it >= 0 }?.plus(2)
            if (after == null) return
            val offset = raw.indexOf(target, after)
            if (offset < 0) return
            transform(target, false, image)?.takeIf { it != target }?.let { edits += Edit(start + offset, start + offset + target.length, it) }
        }
        parser.parse(text).accept(object : AbstractVisitor() {
            override fun visit(node: Text) {
                if (node.parent is Link || node.parent is Image) return
                node.sourceSpans.forEach { span ->
                    val raw = text.substring(span.inputIndex, span.inputIndex + span.length)
                    Regex("(?<!\\\\)\\[\\[([^]\\n]+)]]").findAll(raw).forEach { match ->
                        val target = match.groupValues[1].substringBefore('|')
                        transform(target, true, false)?.takeIf { it != target }?.let {
                            val start = span.inputIndex + match.range.first + 2
                            edits += Edit(start, start + target.length, it)
                        }
                    }
                }
            }
            override fun visit(node: Link) { destination(node, node.destination) }
            override fun visit(node: Image) { destination(node, node.destination, image = true) }
            override fun visit(node: LinkReferenceDefinition) { destination(node, node.destination, reference = true) }
        })
        var result = text
        edits.distinctBy { it.start }.sortedByDescending { it.start }.forEach { result = result.replaceRange(it.start, it.end, it.value) }
        return result
    }
}

package win.iqwqi.xiangece.feature.notebook.domain

import org.commonmark.node.*
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext

/** Accept inert, empty anchors without enabling arbitrary HTML from imported notes. */
internal object NotebookAnchors {
    private val opening = Regex(
        """<(a|span)\s+(?:id|name)\s*=\s*(?:"([\p{L}\p{N}\p{M}_.:+-]{1,256})"|'([\p{L}\p{N}\p{M}_.:+-]{1,256})'|([\p{L}\p{N}\p{M}_.:+-]{1,256}))\s*>""",
        RegexOption.IGNORE_CASE
    )
    private fun id(match: MatchResult) = match.groupValues.drop(2).first { it.isNotEmpty() }
    private fun closing(tag: String, value: String) =
        Regex("""</$tag\s*>""", RegexOption.IGNORE_CASE).matches(value)

    private fun emptyAnchor(line: String): String? {
        // More indentation denotes a code example, which must remain literal.
        if (line.takeWhile { it == ' ' }.length > 3 || line.startsWith('\t')) return null
        val text = line.trim()
        val match = opening.find(text)?.takeIf { it.range.first == 0 } ?: return null
        return if (closing(match.groupValues[1], text.substring(match.range.last + 1).trim())) id(match) else null
    }

    fun convert(document: Node, parse: (String) -> Node) {
        val blocks = mutableListOf<HtmlBlock>()
        val inlines = mutableListOf<HtmlInline>()
        document.accept(object : AbstractVisitor() {
            override fun visit(node: HtmlBlock) { blocks += node }
            override fun visit(node: HtmlInline) { inlines += node }
        })
        blocks.forEach { block ->
            val lines = block.literal.lines()
            var consumed = 0
            for (line in lines) {
                val anchor = emptyAnchor(line) ?: break
                block.insertBefore(AnchorBlock(anchor))
                consumed++
            }
            if (consumed > 0) {
                // CommonMark may absorb the immediately following heading into an HTML
                // block. Reparse only that remainder so headings and prose remain Markdown.
                val remainder = lines.drop(consumed).joinToString("\n")
                if (remainder.isNotBlank()) {
                    val parsed = parse(remainder)
                    convert(parsed, parse)
                    while (parsed.firstChild != null) block.insertBefore(parsed.firstChild)
                }
                block.unlink()
            }
        }
        inlines.forEach { start ->
            if (start.parent == null) return@forEach
            val match = opening.matchEntire(start.literal) ?: return@forEach
            val gap = (start.next as? Text)?.takeIf { it.literal.isBlank() }
            val end = (if (gap != null) gap.next else start.next) as? HtmlInline ?: return@forEach
            if (closing(match.groupValues[1], end.literal)) {
                start.insertBefore(AnchorInline(id(match)))
                gap?.unlink()
                end.unlink()
                start.unlink()
            }
        }
    }

    private class AnchorBlock(val id: String) : CustomBlock()
    private class AnchorInline(val id: String) : CustomNode()

    class Renderer(private val context: HtmlNodeRendererContext) : NodeRenderer {
        override fun getNodeTypes(): Set<Class<out Node>> = setOf(AnchorBlock::class.java, AnchorInline::class.java)
        override fun render(node: Node) {
            val block = node is AnchorBlock
            val id = when (node) {
                is AnchorBlock -> node.id
                is AnchorInline -> node.id
                else -> return
            }
            val tag = if (block) "div" else "span"
            if (block) context.writer.line()
            context.writer.tag(tag, mapOf("id" to id, "class" to "note-anchor"))
            context.writer.tag("/$tag")
            if (block) context.writer.line()
        }
    }
}

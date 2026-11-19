package win.iqwqi.xiangece.feature.notebook.domain

import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.file.Paths
import win.iqwqi.xiangece.feature.notebook.data.NoteDocument

data class NoteLink(val target: String, val label: String, val wiki: Boolean)
data class NoteOutline(val title: String, val level: Int)
data class MarkdownInfo(val plainText: String, val tags: Set<String>, val links: List<NoteLink>, val outline: List<NoteOutline>, val bodyPreview: String = plainText)

object NotebookMarkdown {
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create())
    private val parser = NotebookMarkdownParser(Parser.builder().extensions(extensions)
        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build())
    private val renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(true).sanitizeUrls(true)
        .nodeRendererFactory { NotebookAnchors.Renderer(it) }
        .attributeProviderFactory {
            val headings = mutableMapOf<String, Int>()
            org.commonmark.renderer.html.AttributeProvider { node, _, attributes ->
                if (node is Heading) {
                    val base = textOf(node).lowercase().trim().replace(Regex("\\s+"), "-")
                    val count = headings[base] ?: 0
                    headings[base] = count + 1
                    attributes["id"] = if (count == 0) base else "$base-$count"
                }
            }
        }.build()
    private val wiki = Regex("\\[\\[([^]\\n]+)]]")
    private val tag = Regex("(?:^|[\\s（(])#([\\p{L}\\p{N}_/-]+)")

    fun info(text: String): MarkdownInfo {
        val plain = StringBuilder()
        val body = StringBuilder()
        val links = mutableListOf<NoteLink>()
        val headings = mutableListOf<NoteOutline>()
        val tags = linkedSetOf<String>()
        parser.parse(text).accept(object : AbstractVisitor() {
            override fun visit(node: Text) {
                val value = wiki.replace(node.literal) { match -> match.groupValues[1].substringAfter('|', match.groupValues[1]) }
                plain.append(value).append(' ')
                if (generateSequence(node.parent) { it.parent }.none { it is Heading }) body.append(value).append(' ')
                wiki.findAll(node.literal).forEach { match ->
                    val target = match.groupValues[1].substringBefore('|')
                    links += NoteLink(target, match.groupValues[1].substringAfter('|', target), true)
                }
                tag.findAll(node.literal).forEach { tags += it.groupValues[1] }
            }
            override fun visit(node: Link) { links += NoteLink(node.destination, textOf(node), false); visitChildren(node) }
            override fun visit(node: Heading) { headings += NoteOutline(textOf(node), node.level); visitChildren(node) }
            override fun visit(node: Code) {
                plain.append(node.literal).append(' ')
                if (generateSequence(node.parent) { it.parent }.none { it is Heading }) body.append(node.literal).append(' ')
            }
            override fun visit(node: FencedCodeBlock) { plain.append(node.literal).append(' ') }
            override fun visit(node: IndentedCodeBlock) { plain.append(node.literal).append(' ') }
        })
        return MarkdownInfo(plain.toString().trim(), tags, links, headings, body.toString().trim().ifBlank { plain.toString().trim() })
    }

    fun localImages(text: String, folder: String): Set<String> = buildSet {
        parser.parse(text).accept(object : AbstractVisitor() {
            override fun visit(node: Image) {
                val destination = runCatching { URLDecoder.decode(node.destination.replace("+", "%2B"), "UTF-8") }.getOrNull() ?: return
                if (':' in destination || destination.startsWith('/') || '\\' in destination) return
                val path = runCatching { Paths.get(folder, destination).normalize().toString().replace('\\', '/') }
                    .getOrNull() ?: return
                if (path != ".." && !path.startsWith("../")) add(path)
            }
        })
    }

    fun html(text: String): String {
        val document = parser.parse(text)
        val nodes = mutableListOf<Text>()
        document.accept(object : AbstractVisitor() {
            override fun visit(node: Text) { if (node.parent !is Link && node.parent !is Image) nodes += node }
        })
        nodes.forEach { node ->
            var cursor = 0
            val matches = wiki.findAll(node.literal).toList()
            matches.forEach { match ->
                node.insertBefore(Text(node.literal.substring(cursor, match.range.first)))
                val target = match.groupValues[1].substringBefore('|')
                val link = Link("https://notebook.local/wiki/" + URLEncoder.encode(target, "UTF-8"), null)
                link.appendChild(Text(match.groupValues[1].substringAfter('|', target)))
                node.insertBefore(link)
                cursor = match.range.last + 1
            }
            if (matches.isNotEmpty()) { node.insertBefore(Text(node.literal.substring(cursor))); node.unlink() }
        }
        return renderer.render(document)
    }

    fun resolve(link: NoteLink, source: NoteDocument, notes: List<NoteDocument>): List<NoteDocument> {
        val target = runCatching { URLDecoder.decode(link.target.substringBefore('#').replace("+", "%2B"), "UTF-8") }.getOrDefault(link.target)
        if (target.isBlank()) return listOf(source)
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(target)) return emptyList()
        fun key(path: String) = path.lowercase().removeSuffix(".md")
        val relative = runCatching { Paths.get(source.record.folder, target).normalize().toString().replace('\\', '/') }.getOrDefault(target)
        val exact = notes.filter { key(it.record.path) == key(relative) || key(it.record.path) == key(target) }
        if (exact.isNotEmpty()) return exact
        val aliases = notes.filter { note -> note.record.aliases.any { key(it) == key(target) || key(it) == key(relative) || (link.wiki && key(it).endsWith("/" + key(target))) } }
        if (aliases.isNotEmpty()) return aliases
        if (link.wiki && '/' in target) return notes.filter { key(it.record.path).endsWith("/" + key(target)) }
        return if (link.wiki) notes.filter { key(it.record.title) == key(target) } else emptyList()
    }

    private fun textOf(node: Node): String = buildString {
        node.accept(object : AbstractVisitor() {
            override fun visit(node: Text) { append(node.literal) }
            override fun visit(node: Code) { append(node.literal) }
        })
    }
}

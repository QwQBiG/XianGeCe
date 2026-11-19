package win.iqwqi.xiangece.feature.notebook.domain

import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.node.Emphasis
import org.commonmark.node.StrongEmphasis
import org.commonmark.parser.Parser

/** Chinese prose often places punctuation inside emphasis without surrounding spaces. */
internal class NotebookMarkdownParser(private val parser: Parser) {
    fun parse(source: String): Node = parseEmphasis(source).also {
        NotebookAnchors.convert(it, ::parseEmphasis)
    }

    private fun parseEmphasis(source: String): Node {
        val document = parser.parse(source)
        if ('*' !in source) return document
        // Consider prose and emphasis boundaries, including partially mispaired runs.
        // Code, HTML and destinations stay protected; stored Markdown is never rewritten.
        val textPositions = BooleanArray(source.length)
        val literals = StringBuilder()
        fun markDelimiters(node: Node, length: Int) {
            val first = node.sourceSpans.firstOrNull() ?: return
            val last = node.sourceSpans.last()
            repeat(length) {
                textPositions[first.inputIndex + it] = true
                textPositions[last.inputIndex + last.length - 1 - it] = true
            }
        }
        document.accept(object : AbstractVisitor() {
            override fun visit(node: Emphasis) { markDelimiters(node, 1); visitChildren(node) }
            override fun visit(node: StrongEmphasis) { markDelimiters(node, 2); visitChildren(node) }
            override fun visit(node: Text) {
                literals.append(node.literal)
                node.sourceSpans.forEach { span ->
                    for (index in span.inputIndex until span.inputIndex + span.length) textPositions[index] = true
                }
            }
        })
        val insertions = sortedSetOf<Int>()
        Regex("\\*+").findAll(source).forEach { run ->
            val start = run.range.first
            val end = run.range.last + 1
            if (run.value.length > 3 || run.range.any { !textPositions[it] } || escaped(source, start)) return@forEach
            val before = if (start > 0) source.codePointBefore(start) else -1
            val after = if (end < source.length) source.codePointAt(end) else -1
            if (punctuation(before) && (cjk(after) || widePunctuation(before)) && word(after)) insertions += start
            if (punctuation(after) && (cjk(before) || widePunctuation(after)) && word(before)) insertions += end
        }
        if (insertions.isEmpty()) return document
        // A temporary non-punctuation character makes the delimiter unambiguous. Choose
        // a sequence absent from both source and decoded text, then remove only that sequence.
        var guard = "\uE000"
        while (guard in source || literals.contains(guard)) guard += '\uE000'
        val adjusted = buildString(source.length + insertions.size * guard.length) {
            var cursor = 0
            insertions.forEach { offset -> append(source, cursor, offset); append(guard); cursor = offset }
            append(source, cursor, source.length)
        }
        return parser.parse(adjusted).also { parsed ->
            parsed.accept(object : AbstractVisitor() {
                override fun visit(node: Text) { node.literal = node.literal.replace(guard, "") }
            })
        }
    }

    private fun escaped(source: String, index: Int): Boolean {
        var cursor = index - 1
        while (cursor >= 0 && source[cursor] == '\\') cursor--
        return (index - cursor - 1) % 2 == 1
    }

    private fun cjk(codePoint: Int): Boolean = codePoint >= 0 && Character.UnicodeScript.of(codePoint) in cjkScripts
    private fun widePunctuation(codePoint: Int) = codePoint in 0x3001..0x303f || codePoint in 0xff01..0xff65
    private fun word(codePoint: Int) = codePoint >= 0 && !Character.isWhitespace(codePoint) &&
        !Character.isSpaceChar(codePoint) && !punctuation(codePoint)
    private fun punctuation(codePoint: Int) = codePoint >= 0 && Character.getType(codePoint) in punctuationTypes

    companion object {
        private val cjkScripts = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL, Character.UnicodeScript.BOPOMOFO)
        private val punctuationTypes = setOf(Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(), Character.MATH_SYMBOL.toInt(),
            Character.CURRENCY_SYMBOL.toInt(), Character.MODIFIER_SYMBOL.toInt(), Character.OTHER_SYMBOL.toInt())
    }
}

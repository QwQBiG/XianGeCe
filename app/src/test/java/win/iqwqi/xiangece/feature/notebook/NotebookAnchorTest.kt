package win.iqwqi.xiangece.feature.notebook

import org.junit.Assert.*
import org.junit.Test
import win.iqwqi.xiangece.feature.notebook.domain.NotebookMarkdown

class NotebookAnchorTest {
    @Test fun explicitContentsAnchorsRenderWithoutLiteralTags() {
        val text = "[先读这一页](#a) · [问题](#q1)\n\n<a id=\"a\"></a>\n## A. 先读这一页\n\n<a id=\"q1\"></a>\n### Q1. 时间有限\n\n**重点：**正文"
        val html = NotebookMarkdown.html(text)
        assertTrue(html, html.contains("id=\"a\""))
        assertTrue(html, html.contains("id=\"q1\""))
        assertTrue(html, html.contains("href=\"#a\""))
        assertTrue(html, html.contains("<h2"))
        assertTrue(html, html.contains("<h3"))
        assertTrue(html, html.contains("<strong>重点：</strong>正文"))
        assertFalse(html, html.contains("&lt;a"))
        assertEquals(listOf("A. 先读这一页", "Q1. 时间有限"), NotebookMarkdown.info(text).outline.map { it.title })
    }

    @Test fun inlineAndLegacyNamedAnchorsPreserveNearbyContent() {
        val html = NotebookMarkdown.html("前文<a name='q-1'></a>后文\n\n<A ID=\"章节一\"></A>\n<span id=\"a+b\"></span>\n## **标题：**说明")
        listOf("q-1", "章节一", "a+b").forEach { assertTrue(html, html.contains("id=\"$it\"")) }
        assertTrue(html, html.contains("前文"))
        assertTrue(html, html.contains("后文"))
        assertTrue(html, html.contains("<strong>标题：</strong>说明"))
        assertFalse(html, html.contains("&lt;"))
    }

    @Test fun codeAndEscapedAnchorExamplesStayVisible() {
        val html = NotebookMarkdown.html("`<a id=\"sample\"></a>`\n\n```html\n<a id=\"fenced\"></a>\n```\n\n    <a id=\"indented\"></a>\n\n\\<a id=\"escaped\">\\</a>")
        listOf("sample", "fenced", "indented", "escaped").forEach { assertFalse(html, html.contains("id=\"$it\"")) }
        assertTrue(html, html.contains("&lt;a"))
    }

    @Test fun onlyEmptyInertAnchorsAreAccepted() {
        val text = "<a id=\"bad\" onclick=\"alert(1)\"></a>\n\n<a id=\"link\" href=\"https://example.com\"></a>\n\n<a id=\"nonempty\">内容</a>\n\n<script>alert(1)</script>\n\n<div id=\"div\"></div>\n\n<a id=\"good\"></a>\n## 正常标题"
        val html = NotebookMarkdown.html(text)
        listOf("bad", "link", "nonempty", "div").forEach { assertFalse(html, html.contains("id=\"$it\"")) }
        assertFalse(html, html.contains("<script>"))
        assertTrue(html, html.contains("id=\"good\""))
        assertTrue(html, html.contains("内容"))
    }
}

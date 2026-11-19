package win.iqwqi.xiangece.feature.notebook

import org.junit.Assert.*
import org.junit.Test
import win.iqwqi.xiangece.feature.notebook.data.*
import win.iqwqi.xiangece.feature.notebook.domain.*

class NotebookSearchTest {
    private fun note(id: String, path: String, text: String) = NoteDocument(NoteRecord(id, path), text, "rev", 1)
    @Test fun headingsTablesTasksAndUnsafeHtmlRenderCorrectly() {
        val html = NotebookMarkdown.html("# 标题\n\n| A | B |\n| - | - |\n| 1 | 2 |\n\n- [x] 已做\n\n<script>alert(1)</script>\n\n[危险](javascript:alert(1))")
        assertTrue(html.contains("<h1 id=\"标题\">标题</h1>"))
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("type=\"checkbox\""))
        assertFalse(html.contains("<script>"))
        assertFalse(html.contains("href=\"javascript:"))
    }
    @Test fun linksInCodeAreNotBacklinksAndAmbiguousTitlesRemainAmbiguous() {
        val a = note("a", "甲/课程.md", "课程甲")
        val b = note("b", "乙/课程.md", "课程乙")
        val source = note("c", "随记.md", "[[课程]]\n\n`[[课程]]`\n\n```\n[[课程]]\n```\n")
        val links = NotebookMarkdown.info(source.text).links
        assertEquals(1, links.size)
        assertEquals(2, NotebookMarkdown.resolve(links.single(), source, listOf(a, b, source)).size)
        assertTrue(NotebookSearch(listOf(a, b, source)).backlinks(a).isEmpty())
        assertTrue(NotebookMarkdown.html(source.text).contains("/wiki/"))
    }
    @Test fun searchRanksTitleAndFindsChineseTagsWithoutInventingResults() {
        val a = note("a", "线性代数.md", "矩阵的秩与向量空间 #数学")
        val b = note("b", "午餐.md", "学校食堂菜单 #生活")
        val index = NotebookSearch(listOf(a, b))
        assertEquals("a", index.search("线性代数").first().note.record.id)
        assertEquals("a", index.search("#数学").single().note.record.id)
        assertTrue(index.search("量子隧穿").isEmpty())
        assertFalse(index.related(a).any { it.note.record.id == "b" })
        assertEquals("code", NotebookSearch(listOf(note("code", "代码随记.md", "```kotlin\ntransactionRollback()\n```"))).search("transactionRollback").single().note.record.id)
    }
    @Test fun backlinksResolveRelativePathsAndRenamedAliases() {
        val target = note("a", "阅读/新的题目.md", "内容").copy(record = NoteRecord("a", "阅读/新的题目.md", aliases = listOf("阅读/旧题目.md")))
        val source = note("b", "阅读/随记.md", "[[旧题目]] [读一读](旧题目.md)")
        val index = NotebookSearch(listOf(target, source))
        assertEquals("b", index.backlinks(target).single().note.record.id)
    }
    @Test fun movingAndExportingKeepImagePathsAndLinksButDoNotRewriteCode() {
        val target = note("a", "阅读/新题目.md", "内容").copy(record = NoteRecord("a", "阅读/新题目.md", aliases = listOf("阅读/旧题目.md")))
        val source = note("b", "阅读/随记.md", "[[阅读/旧题目|显示]]\n\n`[[阅读/旧题目]]`\n\n![图](../attachments/photo.png)\n\n[阅读](旧题目.md)")
        val portable = NotebookLinkRewriter.portable(source, listOf(target, source))
        assertTrue(portable.contains("[[阅读/新题目|显示]]"))
        assertTrue(portable.contains("`[[阅读/旧题目]]`"))
        assertTrue(portable.contains("[阅读]("))
        val moved = NotebookLinkRewriter.relocate(source, "分类/笔记", listOf(target, source))
        assertTrue(moved.contains("../../attachments/photo.png"))
        assertEquals(1, NotebookMarkdown.resolve(NoteLink("C++.md", "", true), source, listOf(note("cpp", "C++.md", "语言"))).size)
    }
}

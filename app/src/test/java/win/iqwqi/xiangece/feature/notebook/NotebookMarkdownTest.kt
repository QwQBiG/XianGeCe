package win.iqwqi.xiangece.feature.notebook

import org.junit.Assert.*
import org.junit.Test
import win.iqwqi.xiangece.feature.notebook.domain.NotebookMarkdown

class NotebookMarkdownTest {
    @Test fun chinesePunctuationDoesNotExposeBoldMarkers() {
        val source = "**连接池取消，Q29–Q35：**阅读 OpenDAL\n\n**共享结果，Q17–Q27：**一个 Completer\n\n**最新状态，Q7–Q16：**只保留最新值"
        val html = NotebookMarkdown.html(source)
        assertTrue(html, html.contains("<strong>连接池取消，Q29–Q35：</strong>阅读 OpenDAL"))
        assertTrue(html, html.contains("<strong>共享结果，Q17–Q27：</strong>一个 Completer"))
        assertTrue(html, html.contains("<strong>最新状态，Q7–Q16：</strong>只保留最新值"))
        assertFalse(html, html.contains("**"))
    }

    @Test fun chineseInlineQuotesAndNestedFormattingRender() {
        val html = NotebookMarkdown.html("这是**“重点”**说明，接着*（斜体）*文字，以及***强调：***继续。\n\n**包含 `poll`、[链接](https://example.com) 与 *斜体*：**继续")
        assertTrue(html, html.contains("<strong>“重点”</strong>说明"))
        assertTrue(html, html.contains("<em>（斜体）</em>文字"))
        assertTrue(html, html.contains("<em><strong>强调：</strong></em>继续"))
        assertTrue(html, html.contains("<strong>包含 <code>poll</code>、<a rel=\"nofollow\" href=\"https://example.com\">链接</a> 与 <em>斜体</em>：</strong>继续"))
    }

    @Test fun escapesEntitiesAndCodeRemainLiteral() {
        val source = "\\*\\*原样：\\*\\*文字\n\n&#42;&#42;原样：&#42;&#42;文字\n\n`**代码：**文字`\n\n```md\n**代码：**文字\n```\n\n    **缩进代码：**文字\n\n未闭合 **重点：后文"
        val html = NotebookMarkdown.html(source)
        assertFalse(html, html.contains("<strong>"))
        assertTrue(html, html.contains("<code>**代码：**文字</code>"))
        assertTrue(html, html.contains("<code class=\"language-md\">**代码：**文字\n</code>"))
        assertTrue(html, html.contains("<pre><code>**缩进代码：**文字\n</code>"))
        assertTrue(html, html.contains("未闭合 **重点：后文"))
    }

    @Test fun ordinaryMarkdownAndParagraphBoundariesStayIntact() {
        val html = NotebookMarkdown.html("**bold** *italic* ***both*** ~~gone~~\n\n__strong__ _em_ snake_case\n\n**不跨段：\n\n**第二段**\n\n---\n\n第一行  \n第二行")
        assertTrue(html, html.contains("<strong>bold</strong> <em>italic</em> <em><strong>both</strong></em> <del>gone</del>"))
        assertTrue(html, html.contains("<strong>strong</strong> <em>em</em> snake_case"))
        assertTrue(html, html.contains("<p>**不跨段：</p>"))
        assertTrue(html, html.contains("<p><strong>第二段</strong></p>"))
        assertTrue(html, html.contains("<hr"))
        assertTrue(html, html.contains("第一行<br />\n第二行"))
    }

    @Test fun listsTablesHeadingsAndReferencesKeepInlineFormatting() {
        val html = NotebookMarkdown.html("# **标题：**说明\n\n# **标题：**说明\n\n> **引用：**正文\n\n3. **条目：**说明\n   - [x] **完成：**说明\n   - [ ] 待办\n\n| 项目 | 内容 |\n| :--- | ---: |\n| **重点：**说明 | `a\\|b` |\n\n[**链接：**说明][ref]\n\n[ref]: https://example.com")
        assertTrue(html, html.contains("id=\"标题：说明\""))
        assertTrue(html, html.contains("id=\"标题：说明-1\""))
        assertTrue(html, html.contains("<blockquote>"))
        assertTrue(html, html.contains("<ol start=\"3\">"))
        assertTrue(html, html.contains("type=\"checkbox\""))
        assertTrue(html, html.contains("<strong>完成：</strong>说明"))
        assertTrue(html, html.contains("<table>"))
        assertTrue(html, html.contains("<strong>重点：</strong>说明"))
        assertTrue(html, html.contains("<code>a|b</code>"))
        assertTrue(html, html.contains("<a rel=\"nofollow\" href=\"https://example.com\"><strong>链接：</strong>说明</a>"))
    }

    @Test fun searchAndOutlineUseTheSameReadableTextAsPreview() {
        val info = NotebookMarkdown.info("# **标题：**说明\n\n**重点：**正文 #学习\n\n[[课程]]")
        assertEquals("标题：说明", info.outline.single().title)
        assertFalse(info.plainText, info.plainText.contains("**"))
        assertTrue(info.tags.contains("学习"))
        assertEquals("课程", info.links.single().target)
    }

    @Test fun imageDestinationsAndHtmlSafetyArePreserved() {
        val html = NotebookMarkdown.html("**图片：**如下 ![图](attachments/a.png)\n\n<script>alert(1)</script>\n\n[**危险：**链接](javascript:alert(1))")
        assertTrue(html, html.contains("src=\"attachments/a.png\""))
        assertFalse(html, html.contains("<script>"))
        assertFalse(html, html.contains("href=\"javascript:"))
        assertTrue(html, html.contains("<strong>危险：</strong>链接"))
    }

    @Test fun temporaryParserBoundariesNeverLeakOrRemoveOriginalCharacters() {
        val source = "原有字符 \uE000 与 &#xE000;。\n\n**说明：**API 和 **测试:**中文。\n\n**补充：**𠮷野\r\n\r\n![图](attachments/**截图：**中文.png)"
        val html = NotebookMarkdown.html(source)
        assertEquals(2, html.count { it == '\uE000' })
        assertTrue(html, html.contains("<strong>说明：</strong>API"))
        assertTrue(html, html.contains("<strong>测试:</strong>中文"))
        assertTrue(html, html.contains("<strong>补充：</strong>𠮷野"))
        assertTrue(html, html.contains("src=\"attachments/**截图：**中文.png\""))
        assertEquals(setOf("attachments/截图.png"), NotebookMarkdown.localImages("![图](attachments/截图.png)", ""))
        assertTrue(NotebookMarkdown.localImages("![错误](attachments/bad%00.png)", "").isEmpty())
    }

    @Test fun mixedEmphasisDoesNotConsumeAdjacentParagraphsOrLiteralMarkers() {
        val html = NotebookMarkdown.html("**普通**，**第二项**，*普通斜体*。\n\n这是**（重点）**说明和**‘引用’**文字。\n\n\\**字面：**中文\n\n    **代码：**中文\n\n**结尾：**文字")
        assertTrue(html, html.contains("<strong>普通</strong>，<strong>第二项</strong>，<em>普通斜体</em>。"))
        assertTrue(html, html.contains("<strong>（重点）</strong>说明和<strong>‘引用’</strong>文字"))
        assertTrue(html, html.contains("<pre><code>**代码：**中文\n</code>"))
        assertTrue(html, html.contains("<p><strong>结尾：</strong>文字</p>"))
    }
}

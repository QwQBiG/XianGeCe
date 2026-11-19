package win.iqwqi.xiangece.feature.notebook

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import win.iqwqi.xiangece.feature.notebook.data.NotebookFileStore
import win.iqwqi.xiangece.feature.notebook.domain.NoteLink
import win.iqwqi.xiangece.feature.notebook.ui.MarkdownPreview
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

class MarkdownPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun contentsLinksScrollToExplicitAnchorsWithScriptsDisabled() {
        var dark by mutableStateOf(false)
        val context = compose.activity
        val store = NotebookFileStore(File(context.cacheDir, "anchor-test-" + System.nanoTime()))
        val text = buildString {
            append("# 目录\n\n[第一节](#q1) · [章节](#%E7%AB%A0%E8%8A%82%E4%B8%80) · [加号](#a+b) · [标题](#普通标题)\n\n")
            repeat(16) { append("前文第 $it 段，用于核对目录跳转。\n\n") }
            append("<a id=\"q1\"></a>\n## Q1. 时间有限\n\n")
            repeat(16) { append("第一节第 $it 段。\n\n") }
            append("<a name='章节一'></a>\n## 中文章节\n\n")
            repeat(16) { append("中文章节第 $it 段。\n\n") }
            append("<span id=\"a+b\"></span>\n## 加号编号\n\n")
            repeat(16) { append("加号编号第 $it 段。\n\n") }
            append("## 普通标题\n\n")
            repeat(20) { append("末尾第 $it 段，确保目标可以滚到顶端。\n\n") }
        }
        val original = store.create("目录跳转", folder = "课程", text = text)
        var callbacks = 0
        compose.setContent {
            XiangeceTheme(darkTheme = dark) {
                MarkdownPreview(original.text, "课程", store, Modifier.fillMaxSize()) { callbacks++ }
            }
        }
        compose.waitUntil(15000) { findWebView(context.window.decorView) != null }
        val web = findWebView(context.window.decorView)!!
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night; web.settings.javaScriptEnabled = true }
            compose.waitUntil(15000) { js(web, "document.getElementById('q1') !== null") == true }
            assertEquals(false, js(web, "document.body.innerText.includes('<a id=')"))
            for (id in listOf("q1", "章节一", "a+b", "普通标题")) {
                compose.runOnIdle { web.scrollTo(0, 0) }
                compose.waitUntil(5000) { js(web, "window.scrollY === 0") == true }
                val encodedId = JSONObject.quote(id)
                val rect = JSONObject(js(web, """
                    JSON.stringify((() => {
                        const a = Array.from(document.querySelectorAll('a')).find(
                            a => decodeURIComponent(a.hash.slice(1)) === $encodedId);
                        const r = a.getBoundingClientRect();
                        return {x:r.left+r.width/2,y:r.top+r.height/2};
                    })())
                """.trimIndent()) as String)
                val location = IntArray(2)
                var density = 1f
                compose.runOnIdle {
                    web.getLocationOnScreen(location)
                    density = web.resources.displayMetrics.density
                    web.settings.javaScriptEnabled = false
                    assertFalse(web.settings.javaScriptEnabled)
                }
                val x = location[0] + rect.getDouble("x").toFloat() * density
                val y = location[1] + rect.getDouble("y").toFloat() * density
                assertTrue(rect.toString(), y > location[1] && y < location[1] + web.height)
                val now = android.os.SystemClock.uptimeMillis()
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                    val event = android.view.MotionEvent.obtain(now, now + action * 80L, action, x, y, 0)
                    instrumentation.sendPointerSync(event)
                    event.recycle()
                }
                compose.waitUntil(10000) { web.scrollY > 100 }
                compose.runOnIdle { web.settings.javaScriptEnabled = true }
                compose.waitUntil(5000) {
                    js(web, "Math.abs(document.getElementById($encodedId).getBoundingClientRect().top) < 24") == true
                }
                assertEquals(0, callbacks)
                capture("anchor-$id-$night")
            }
        }
        assertEquals(text, store.read(original.record.id).text)
    }

    @Test fun actualPreviewKeepsFormattingAndLayoutAcrossThemesAndFontSizes() {
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(360)
        val context = compose.activity
        val store = NotebookFileStore(File(context.cacheDir, "markdown-test-${System.nanoTime()}"))
        val original = store.create("排版核对", text = fixture)
        var followed: NoteLink? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                XiangeceTheme(darkTheme = dark) {
                    Box(Modifier.width(width.dp).fillMaxHeight()) {
                        MarkdownPreview(original.text, "", store, Modifier.fillMaxSize()) { followed = it }
                    }
                }
            }
        }
        compose.waitUntil(15000) { findWebView(context.window.decorView) != null }
        val web = findWebView(context.window.decorView)!!
        compose.runOnIdle {
            assertFalse(web.settings.javaScriptEnabled)
            assertFalse(web.settings.allowFileAccess)
            assertFalse(web.settings.allowContentAccess)
            assertTrue(web.settings.blockNetworkLoads)
            // DOM inspection is enabled only by instrumentation, never by the application.
            web.settings.javaScriptEnabled = true
        }
        for (night in listOf(false, true)) for (font in listOf(1f, 1.5f)) for (size in listOf(320, 412)) {
            compose.runOnIdle { dark = night; scale = font; width = size }
            compose.waitUntil(15000) {
                js(web, "document.body && document.body.innerText.includes('连接池取消') && parseFloat(getComputedStyle(document.body).fontSize) === ${16 * font}") == true
            }
            val result = JSONObject(js(web, inspection) as String)
            assertTrue(result.toString(), result.getInt("boldWeight") >= 600)
            assertEquals("连接池取消，Q29–Q35：", result.getString("boldText"))
            assertEquals("italic", result.getString("italic"))
            assertTrue(result.getString("strike").contains("line-through"))
            assertEquals(2, result.getInt("checks"))
            assertEquals(1, result.getInt("checked"))
            assertEquals(2, result.getInt("tableRows"))
            assertTrue(result.toString(), result.getString("alignment").endsWith("right"))
            assertTrue(result.getBoolean("codeLiteral"))
            assertTrue(result.toString(), result.getBoolean("fitsViewport"))
            val rgb = Regex("\\d+").findAll(result.getString("background")).map { it.value.toInt() }.take(3).average()
            assertEquals(night, rgb < 128)
            js(web, "window.scrollTo(0,0)")
            capture("preview-$size-$font-$night")
            js(web, "document.querySelector('table').scrollIntoView()")
            capture("blocks-$size-$font-$night")
        }
        js(web, "document.querySelector('a[href*=\"/wiki/\"]').click()")
        compose.waitUntil(5000) { followed != null }
        assertEquals(NoteLink("课程", "", true), followed)
        assertEquals(fixture, store.read(original.record.id).text)
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }

    private fun js(web: WebView, script: String): Any? {
        val latch = CountDownLatch(1)
        var value: Any? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            web.evaluateJavascript(script) { value = JSONTokener(it).nextValue(); latch.countDown() }
        }
        check(latch.await(5, TimeUnit.SECONDS)) { "Preview DOM inspection timed out" }
        return value
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val output = File(compose.activity.getExternalFilesDir(null), "markdown-preview").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(output, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    companion object {
        private val inspection = """
            JSON.stringify({
                boldWeight: parseInt(getComputedStyle(document.querySelector('strong')).fontWeight),
                boldText: document.querySelector('strong').textContent,
                italic: getComputedStyle(document.querySelector('em')).fontStyle,
                strike: getComputedStyle(document.querySelector('del')).textDecorationLine,
                checks: document.querySelectorAll('input[type=checkbox]').length,
                checked: document.querySelectorAll('input:checked').length,
                tableRows: document.querySelectorAll('table tr').length,
                alignment: getComputedStyle(document.querySelector('td:last-child')).textAlign,
                codeLiteral: document.querySelector('pre code').textContent.includes('**代码：**原样'),
                fitsViewport: document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1,
                background: getComputedStyle(document.body).backgroundColor
            })
        """.trimIndent()
        private val fixture = """
            # Q：怎样核对 Markdown？

            **连接池取消，Q29–Q35：**阅读说明，确认粗体结束后紧接中文也能正常显示。

            **共享结果，Q17–Q27：**这是普通正文。这里有*斜体*与~~删除线~~，以及 `pool.get` 行内代码。

            > **引用：**保留原文与层次。

            3. 第一项
               - [x] **完成：**检查预览
               - [ ] 核对链接

            | 项目 | 内容 | 状态 |
            | :--- | :---: | ---: |
            | **重点：**说明 | `a\|b` | 完成 |

            ```kotlin
            // **代码：**原样，不解析成粗体
            val longLine = "abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456789"
            ```

            ## 内部链接

            [[课程]] 与 [外部说明](https://example.com)，[跳到标题](#内部链接)。

            ---

            **预览不会修改笔记原文。**
        """.trimIndent()
    }
}

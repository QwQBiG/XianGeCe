package win.iqwqi.xiangece.feature.notebook.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.net.URLEncoder
import win.iqwqi.xiangece.feature.notebook.data.NotebookFileStore
import win.iqwqi.xiangece.feature.notebook.domain.NoteLink
import win.iqwqi.xiangece.feature.notebook.domain.NotebookMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MarkdownPreview(text: String, folder: String, store: NotebookFileStore, modifier: Modifier = Modifier, onLink: (NoteLink) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    val latestLink by rememberUpdatedState(onLink)
    var body by remember { mutableStateOf("") }
    LaunchedEffect(text) { body = withContext(Dispatchers.Default) { NotebookMarkdown.html(text) } }
    fun Color.css() = "#%06x".format(toArgb() and 0xffffff)
    val html = remember(body, colors, fontScale) {
        """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src https://notebook.local;">
        <style>html{-webkit-text-size-adjust:100%;text-size-adjust:100%}body{margin:0;padding:12px 22px 64px;color:${colors.onSurface.css()};background:${colors.background.css()};font: ${16 * fontScale}px/1.8 sans-serif;overflow-wrap:anywhere}
        strong,b{font-weight:700}em{font-style:italic}body>:first-child{margin-top:.4em}h1,h2,h3,h4,h5,h6{line-height:1.4;margin:1.2em 0 .55em;font-weight:700}h1{font-size:1.8em}h2{font-size:1.4em}h3{font-size:1.15em}h4{font-size:1.05em}h5,h6{font-size:1em}
        a{color:${colors.primary.css()};text-decoration:none}p{margin:.7em 0}li{margin:.2em 0}ul,ol{padding-left:1.5em}
        blockquote{margin:1em 0;padding:10px 16px;background:${colors.surfaceContainer.css()};border-radius:12px;color:${colors.onSurfaceVariant.css()}}
        pre{overflow:auto;padding:14px;background:${colors.surfaceContainer.css()};border-radius:12px;line-height:1.6;white-space:pre;overflow-wrap:normal;word-break:normal}code{font-family:monospace;font-size:.9em}
        :not(pre)>code{background:${colors.surfaceContainer.css()};padding:2px 5px;border-radius:5px}img{max-width:100%;height:auto;border-radius:12px}
        table{display:block;max-width:100%;overflow:auto;border-collapse:collapse;margin:1em 0}th,td{padding:8px 12px;border:1px solid ${colors.outlineVariant.css()};white-space:normal;min-width:64px}th{background:${colors.surfaceContainer.css()}}
        hr{border:0;border-top:1px solid ${colors.outlineVariant.css()};margin:1.5em 0}input[type=checkbox]{accent-color:${colors.primary.css()};vertical-align:middle;margin:0 .35em 0 0;width:1em;height:1em;opacity:1}</style></head><body>$body</body></html>""".trimIndent()
    }
    val base = "https://notebook.local/files/" + folder.split('/').filter { it.isNotBlank() }.joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }.let { if (it.isBlank()) "" else "$it/" }
    AndroidView(modifier = modifier, factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.domStorageEnabled = false
            settings.setSupportZoom(false)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.host == "notebook.local" && uri.fragment != null && uri.path == android.net.Uri.parse(view.url).path) return false
                    if (uri.host == "notebook.local") {
                        if (uri.path.orEmpty().startsWith("/wiki/")) {
                            latestLink(NoteLink(URLDecoder.decode(uri.encodedPath.orEmpty().removePrefix("/wiki/"), "UTF-8"), "", true))
                        } else {
                            latestLink(NoteLink(uri.path.orEmpty().removePrefix("/files/"), "", false))
                        }
                    } else latestLink(NoteLink(uri.toString(), "", false))
                    return true
                }
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                    val uri = request.url
                    if (uri.scheme == "https" && uri.host == "notebook.local" && uri.path.orEmpty().startsWith("/files/")) {
                        store.attachment(uri.path.orEmpty().removePrefix("/files/"))?.let { file ->
                            val mime = when (file.extension.lowercase()) { "jpg", "jpeg" -> "image/jpeg"; else -> "image/${file.extension.lowercase()}" }
                            return WebResourceResponse(mime, null, file.inputStream())
                        }
                    }
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
        }
    }, update = { web ->
        val key = base + html
        if (web.tag != key) { web.tag = key; web.loadDataWithBaseURL(base, html, "text/html", "UTF-8", null) }
    }, onRelease = { it.stopLoading(); it.destroy() })
}

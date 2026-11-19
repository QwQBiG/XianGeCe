package win.iqwqi.xiangece.feature.notebook.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import win.iqwqi.xiangece.ui.theme.setContentAfterAppearance
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toArgb
import win.iqwqi.xiangece.feature.notebook.data.NotebookArchive
import win.iqwqi.xiangece.feature.notebook.data.NotebookArchive.readBytesLimited
import win.iqwqi.xiangece.feature.notebook.data.NotebookFileStore
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

@AndroidEntryPoint
class NotebookActivity : ComponentActivity() {
    private val model by viewModels<NotebookViewModel>()
    private val research by viewModels<NotebookResearchModel>()
    private var importZip = false
    private var exportWhole = false
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.action {
            if (!model.flush()) return@action
            val name = displayName(uri)
            val added = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法读取文件" }
                    if (importZip || name.endsWith(".zip", true)) NotebookArchive.import(model.repository.store, input)
                    else listOf(model.repository.store.create(name.substringBeforeLast('.'), text = NotebookArchive.decodeMarkdown(input.readBytesLimited(NotebookFileStore.MAX_NOTE_BYTES))))
                }
            }
            model.repository.refresh()
            model.notices.send("已导入 ${added.size} 篇笔记")
            if (added.size == 1) model.open(added.single().record.id)
        }
    }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) model.action {
            if (!model.flush()) return@action
            val current = model.editor.value.document
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri, "wt").use { output ->
                    requireNotNull(output) { "无法创建导出文件" }
                    if (exportWhole) NotebookArchive.export(model.repository.store, output)
                    else { requireNotNull(current); output.write(current.text.toByteArray(Charsets.UTF_8)) }
                }
            }
            model.notices.send("已导出")
        }
    }
    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.action {
            val current = model.editor.value.document ?: return@action
            val path = model.repository.change {
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input)
                    val type = contentResolver.getType(uri).orEmpty().substringAfter("image/")
                    addImage(input.readBytesLimited(20 * 1024 * 1024), if (type == "jpeg") "jpg" else type)
                }
            }
            if (model.editor.value.document?.record?.id == current.record.id) {
                val relative = "../".repeat(current.record.folder.split('/').count { it.isNotEmpty() }) + path
                model.edit(model.editor.value.text + "\n\n![图片]($relative)\n")
            }
        }
    }
    private val collectImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) collectImage(uri)
    }
    private fun collectImage(uri: Uri, text: String = "", title: String? = null) = model.action {
        require(text.length <= 100_000) { "文字较长，请分段收集（一次最多10万字）" }
        if (research.state.value.draft != null) {
            research.page("collect"); research.message("已有未完成的收集，请先保存或放弃，再导入图片")
            return@action
        }
        val path = model.repository.change {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取图片" }
                val type = contentResolver.getType(uri).orEmpty().substringAfter("image/")
                addImage(input.readBytesLimited(20 * 1024 * 1024), if (type == "jpeg") "jpg" else type)
            }
        }
        research.beginText(text, title = title?.take(100) ?: displayName(uri).substringBeforeLast('.').take(100), imagePath = path)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        importZip = savedInstanceState?.getBoolean("importZip") ?: false
        exportWhole = savedInstanceState?.getBoolean("exportWhole") ?: false
        if (savedInstanceState == null && intent.action == Intent.ACTION_SEND) {
            if (intent.type?.startsWith("image/") == true) {
                @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                uri?.let { collectImage(it, intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(), intent.getStringExtra(Intent.EXTRA_SUBJECT)) }
            } else intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                research.beginText(text, intent.getStringExtra(Intent.EXTRA_SUBJECT)?.take(100) ?: "随手收集")
            }
        }
        if (savedInstanceState == null && intent.hasExtra("collect_session")) {
            research.beginClass(intent.getLongExtra("collect_session", -1))
        }
        setContentAfterAppearance(model.settingsStore.settings) { initialSettings ->
            val settings by model.settingsStore.settings.collectAsStateWithLifecycle(initialValue = initialSettings)
            val dark = if (settings.followSystemTheme) isSystemInDarkTheme() else settings.darkMode
            XiangeceTheme(darkTheme = dark, themeSeedId = settings.themeSeed, companion = settings.companion) {
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect { window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background)) }
                SideEffect { WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
                } }
                Surface(color = MaterialTheme.colorScheme.background) {
                    NotebookScreen(model, research, onFinish = ::finish,
                        onImport = { zip -> importZip = zip; importFile.launch(arrayOf("*/*")) },
                        onExport = { whole ->
                            exportWhole = whole
                            exportFile.launch(if (whole) "弦歌册笔记-${java.time.LocalDate.now()}.zip" else model.editor.value.document?.record?.title.orEmpty() + ".md")
                        }, onImage = { pickImage.launch(arrayOf("image/png", "image/jpeg", "image/webp", "image/gif")) },
                        onCollectImage = { collectImage.launch(arrayOf("image/png", "image/jpeg", "image/webp")) })
                }
            }
        }
    }
    override fun onPause() { model.saveSoon(); super.onPause() }
    override fun onStop() { research.stopNetwork(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("importZip", importZip)
        outState.putBoolean("exportWhole", exportWhole)
        super.onSaveInstanceState(outState)
    }
    private fun displayName(uri: Uri): String = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: "导入笔记.md"
}

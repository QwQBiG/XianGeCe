package win.iqwqi.xiangece.feature.notebook.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import win.iqwqi.xiangece.feature.notebook.data.*
import win.iqwqi.xiangece.feature.notebook.domain.*
import win.iqwqi.xiangece.ui.components.AppScene

private data class NotebookScene(val research: NotebookResearchState, val editor: NoteEditorState) {
    val key: String get() = research.page ?: editor.document?.record?.id?.let { "note:$it" } ?: "library"
    val depth: Int get() = if (key == "library") 0 else 1
}

@Composable
fun NotebookScreen(model: NotebookViewModel, research: NotebookResearchModel, onFinish: () -> Unit, onImport: (Boolean) -> Unit,
                   onExport: (Boolean) -> Unit, onImage: () -> Unit, onCollectImage: () -> Unit) {
    val notes by model.notes.collectAsStateWithLifecycle()
    val editor by model.editor.collectAsStateWithLifecycle()
    val researchState by research.state.collectAsStateWithLifecycle()
    var showingSources by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var naming by remember { mutableStateOf<String?>(null) }
    var suggested by remember { mutableStateOf("") }
    var suggestedFolder by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var external by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<NoteDocument>>(emptyList()) }
    var history by remember { mutableStateOf<List<NoteHistory>?>(null) }
    var historyVersion by remember { mutableStateOf<NoteHistory?>(null) }
    val context = LocalContext.current
    val document = editor.document
    val pageMemory = rememberSaveableStateHolder()
    BackHandler { if (researchState.page != null) research.page(null) else if (document != null) model.close() else onFinish() }
    LaunchedEffect(model) { for (message in model.notices) snackbar.showSnackbar(message) }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            AppScene(NotebookScene(researchState, editor), { it.key }, { it.depth }, Modifier.fillMaxSize()) { scene ->
            pageMemory.SaveableStateProvider(scene.key) {
            val document = scene.editor.document
            if (scene.research.page != null) NotebookResearchPage(research, scene.research, notes, onCollectImage,
                onSaved = model::open, onOpenNote = { research.page(null); model.open(it) })
            else if (document == null) NotebookLibrary(notes, model,
                onBack = onFinish, onNew = { suggestedFolder = it; suggested = ""; naming = "new" },
                onFolder = { naming = "folder" }, onImport = onImport, onExport = { onExport(true) },
                onCollect = { research.page("collect") }, onAsk = { research.page("ask") }, hasDraft = scene.research.draft != null)
            else NotebookEditor(scene.editor, notes.filter { it.record.deletedAt == null }, model,
                onBack = { model.close() }, onRename = { naming = "rename" }, onDelete = { deleting = true },
                onExport = { onExport(false) }, onImage = onImage,
                onSources = { showingSources = true },
                onHistory = { model.action { history = withContext(Dispatchers.IO) { model.repository.store.history(document.record.id) } } },
                onLink = { link ->
                    if (link.target.startsWith("https://") || link.target.startsWith("http://")) external = link.target
                    else if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(link.target)) model.notices.trySend("暂不支持打开这类链接")
                    else {
                        val found = NotebookMarkdown.resolve(link, document, notes.filter { it.record.deletedAt == null })
                        when (found.size) {
                            0 -> {
                                val path = link.target.substringBefore('#')
                                suggested = path.substringAfterLast('/').removeSuffix(".md")
                                suggestedFolder = path.substringBeforeLast('/', document.record.folder)
                                naming = "new"
                            }
                            1 -> model.open(found.single().record.id)
                            else -> candidates = found
                        }
                    }
                })
            }
            }
        }
    }
    if (showingSources && document != null) NotebookSourceDialog(document, research, { showingSources = false }, model::open)
    naming?.let { kind ->
        var title by remember(kind) { mutableStateOf(if (kind == "rename") document?.record?.title.orEmpty() else suggested) }
        var folder by remember(kind) { mutableStateOf(if (kind == "rename") document?.record?.folder.orEmpty() else suggestedFolder) }
        AlertDialog(onDismissRequest = { naming = null }, title = { Text(when(kind) { "rename" -> "名称与文件夹"; "folder" -> "新建文件夹"; else -> "新建笔记" }) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("名称") }, singleLine = true)
                if (kind != "folder") OutlinedTextField(folder, { folder = it }, label = { Text("文件夹（可留空）") }, singleLine = true)
            } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
                when (kind) { "rename" -> model.rename(title, folder); "folder" -> model.createFolder(title); else -> model.create(title, folder) }
                naming = null
            }) { Text("确定") } }, dismissButton = { TextButton(onClick = { naming = null }) { Text("取消") } })
    }
    if (deleting && document != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("移到回收站？") },
        text = { Text("「${document.record.title}」可以从回收站找回，图片附件会保留。") },
        confirmButton = { TextButton(onClick = { model.trash(document.record.id); deleting = false }) { Text("移入") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } })
    external?.let { url -> AlertDialog(onDismissRequest = { external = null }, title = { Text("打开外部链接") }, text = { Text(url) },
        confirmButton = { TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }; external = null }) { Text("打开") } },
        dismissButton = { TextButton(onClick = { external = null }) { Text("取消") } }) }
    if (candidates.isNotEmpty()) AlertDialog(onDismissRequest = { candidates = emptyList() }, title = { Text("选择要打开的笔记") },
        text = { Column { candidates.forEach { note -> TextButton(onClick = { model.open(note.record.id); candidates = emptyList() }) { Text(note.record.path) } } } },
        confirmButton = { TextButton(onClick = { candidates = emptyList() }) { Text("取消") } })
    history?.let { versions -> AlertDialog(onDismissRequest = { history = null }, title = { Text("历史版本") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            if (versions.isEmpty()) Text("还没有历史版本，修改后会自动保留。")
            versions.forEach { version -> TextButton(onClick = { historyVersion = version; history = null }) {
                Column { Text(java.time.Instant.ofEpochMilli(version.timestamp).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")))
                    Text(version.text.take(90).ifBlank { "空白笔记" }, maxLines = 2) }
            } }
        } }, confirmButton = { TextButton(onClick = { history = null }) { Text("关闭") } }) }
    historyVersion?.let { version -> AlertDialog(onDismissRequest = { historyVersion = null }, title = { Text("恢复这个版本？") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text("当前正文也会保留在历史版本中。")
            Text(version.text.take(1800).ifBlank { "（空白内容）" }, Modifier.padding(top = 16.dp))
        } }, confirmButton = { TextButton(onClick = { model.edit(version.text); historyVersion = null }) { Text("恢复") } },
        dismissButton = { TextButton(onClick = { historyVersion = null }) { Text("取消") } }) }
}

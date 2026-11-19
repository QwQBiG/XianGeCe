package win.iqwqi.xiangece.feature.notebook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import win.iqwqi.xiangece.ui.components.AppChoice
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import win.iqwqi.xiangece.feature.notebook.data.NoteDocument
import win.iqwqi.xiangece.feature.notebook.domain.*

@Composable
internal fun NotebookEditor(state: NoteEditorState, notes: List<NoteDocument>, model: NotebookViewModel,
    onBack: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit, onImage: () -> Unit,
    onHistory: () -> Unit, onSources: () -> Unit, onLink: (NoteLink) -> Unit) {
    val doc = state.document ?: return
    var mode by rememberSaveable(doc.record.id) { mutableStateOf(if (doc.text.isBlank()) "编辑" else "预览") }
    var menu by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { focus.clearFocus(); keyboard?.hide(); onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回笔记库") }
            Column(Modifier.weight(1f)) {
                Text(doc.record.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(doc.record.folder.ifBlank { "笔记" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "笔记菜单") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("名称与文件夹") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(if (notes.firstOrNull { it.record.id == doc.record.id }?.record?.pinned == true) "取消收藏" else "收藏") }, onClick = { menu = false; model.pin(doc.record.id) })
                    DropdownMenuItem(text = { Text("历史版本") }, onClick = { menu = false; onHistory() })
                    if (doc.record.sources.isNotEmpty()) DropdownMenuItem(text = { Text("来源与回听") }, onClick = { menu = false; onSources() })
                    DropdownMenuItem(text = { Text("导出 Markdown") }, onClick = { menu = false; onExport() })
                    DropdownMenuItem(text = { Text("移到回收站") }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            listOf("编辑", "预览", "联系").forEach { name -> AppChoice(name, selected = mode == name, onClick = {
                if (name != "编辑") { focus.clearFocus(); keyboard?.hide() }
                mode = name; model.saveSoon()
            }) }
            }
            Text(if (state.saving) "保存中" else if (state.text != doc.text) "未保存" else "已保存",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.message?.let { message ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(message, style = MaterialTheme.typography.bodySmall)
                    if (state.conflict) TextButton(onClick = model::saveCopy) { Text("将我的编辑另存一份") }
                    else if (state.text != doc.text) TextButton(onClick = model::saveSoon) { Text("重试保存") }
                }
            }
        }
        if (doc.record.sources.isNotEmpty()) TextButton(onClick = onSources, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text("查看原始材料${if (doc.record.sources.any { it.kind == "class" }) " · 回听课堂" else ""}")
        }
        when (mode) {
            "编辑" -> MarkdownEditorField(doc.record.id, state.text, model::edit, notes, onImage, Modifier.weight(1f))
            "预览" -> MarkdownPreview(state.text, doc.record.folder, model.repository.store, Modifier.fillMaxWidth().weight(1f), onLink)
            else -> NoteConnections(doc.copy(text = state.text), notes, model, Modifier.weight(1f), onLink)
        }
    }
}

@Composable
private fun NoteConnections(doc: NoteDocument, notes: List<NoteDocument>, model: NotebookViewModel, modifier: Modifier, onLink: (NoteLink) -> Unit) {
    var backlinks by remember { mutableStateOf<List<NoteMatch>>(emptyList()) }
    var related by remember { mutableStateOf<List<NoteMatch>>(emptyList()) }
    val info = remember(doc.text) { NotebookMarkdown.info(doc.text) }
    LaunchedEffect(doc, notes) { withContext(Dispatchers.Default) { NotebookSearch(notes) }.let { index ->
        backlinks = withContext(Dispatchers.Default) { index.backlinks(doc) }
        related = withContext(Dispatchers.Default) {
            val linkedIds = backlinks.map { it.note.record.id }.toSet() + info.links.flatMap { NotebookMarkdown.resolve(it, doc, notes) }.map { it.record.id }
            index.related(doc).filter { it.note.record.id !in linkedIds }
        }
    } }
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("这篇笔记提到", style = MaterialTheme.typography.titleMedium) }
        if (info.links.isEmpty()) item { Text("用 [[笔记名称]] 把想法连起来。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(info.links) { link -> OutlinedButton(onClick = { onLink(link) }, modifier = Modifier.fillMaxWidth()) { Text(link.label.ifBlank { link.target }) } }
        item { Text("链接到这里 · ${backlinks.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
        if (backlinks.isEmpty()) item { Text("还没有其他笔记链接到这里。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(backlinks, key = { "back-${it.note.record.id}" }) { match -> NoteListCard(match.note, match, { model.open(match.note.record.id) }, { model.pin(match.note.record.id) }) }
        item { Text("也许有关", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
        if (related.isEmpty()) item { Text("暂时没有更多相关内容。共同标签和正文会帮助你找到新的联系。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(related, key = { "related-${it.note.record.id}" }) { match -> NoteListCard(match.note, match, { model.open(match.note.record.id) }, { model.pin(match.note.record.id) }) }
    }
}

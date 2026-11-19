package win.iqwqi.xiangece.feature.notebook.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import win.iqwqi.xiangece.feature.notebook.data.NoteDocument
import win.iqwqi.xiangece.feature.notebook.domain.*
import win.iqwqi.xiangece.ui.components.AppPageHeader
import win.iqwqi.xiangece.ui.components.AppSearchField
import win.iqwqi.xiangece.ui.components.AppChoice

@Composable
fun NotebookLibrary(notes: List<NoteDocument>, model: NotebookViewModel, onBack: () -> Unit, onNew: (String) -> Unit,
                    onFolder: () -> Unit, onImport: (Boolean) -> Unit, onExport: () -> Unit,
                    onCollect: () -> Unit, onAsk: () -> Unit, hasDraft: Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("全部") }
    var folder by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    val folders by model.repository.folders.collectAsStateWithLifecycle()
    val active = remember(notes) { notes.filter { it.record.deletedAt == null } }
    var index by remember { mutableStateOf<NotebookSearch?>(null) }
    var matches by remember { mutableStateOf<List<NoteMatch>?>(null) }
    val corpus = remember(notes, filter) { if (filter == "回收站") notes.filter { it.record.deletedAt != null } else active }
    LaunchedEffect(corpus) { index = withContext(Dispatchers.Default) { NotebookSearch(corpus) } }
    LaunchedEffect(query, index) {
        matches = null
        val searchIndex = index ?: return@LaunchedEffect
        delay(150)
        matches = withContext(Dispatchers.Default) { searchIndex.search(query) }
    }
    val searching = query.isNotBlank() && matches == null
    val visible = (if (query.isNotBlank()) matches.orEmpty().map { it.note } else notes).filter {
        when (filter) { "收藏" -> it.record.pinned && it.record.deletedAt == null; "回收站" -> it.record.deletedAt != null; else -> it.record.deletedAt == null }
    }.filter { folder.isBlank() || it.record.folder == folder || it.record.folder.startsWith("$folder/") }
    LazyColumn(modifier = Modifier.testTag("notebook-list"), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AppPageHeader("笔记库", "${active.size} 篇笔记", onBack = onBack) {
                IconButton(onClick = { onNew(folder) }) { Icon(Icons.Outlined.EditNote, "新建笔记", tint = MaterialTheme.colorScheme.primary) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "笔记库菜单") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("回收站") }, onClick = { menu = false; filter = "回收站"; query = "" })
                        DropdownMenuItem(text = { Text("导入 Markdown") }, onClick = { menu = false; onImport(false) })
                        DropdownMenuItem(text = { Text("导入 ZIP 笔记库") }, onClick = { menu = false; onImport(true) })
                        DropdownMenuItem(text = { Text("导出全部笔记与图片") }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text("新建文件夹") }, onClick = { menu = false; onFolder() })
                        DropdownMenuItem(text = { Text("写作示例") }, onClick = { menu = false; model.create("写作示例", "", NOTEBOOK_GUIDE) })
                    }
                }
            }
        }
        item {
            AppSearchField(query, { query = it }, "查找笔记或 #标签")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onCollect, modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp, 10.dp)) {
                    Icon(Icons.Outlined.NoteAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (hasDraft) "继续收集" else "收集资料")
                }
                FilledTonalButton(onClick = onAsk, modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp, 10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Icon(Icons.Outlined.TravelExplore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("问问笔记")
                }
            }
        }
        if (query.isNotBlank()) item { Text(if (searching) "正在查找…" else "${visible.size} 篇匹配的笔记", style = MaterialTheme.typography.labelLarge) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("全部", "收藏") + if (filter == "回收站") listOf("回收站") else emptyList()).forEach { name ->
                    AppChoice(name, filter == name, { filter = name })
                }
                Box {
                    AppChoice(folder.ifBlank { "文件夹" }, folder.isNotBlank(), { folderMenu = true })
                    DropdownMenu(folderMenu, { folderMenu = false }) {
                        DropdownMenuItem(text = { Text("全部文件夹") }, onClick = { folder = ""; folderMenu = false })
                        folders.forEach { path -> DropdownMenuItem(text = { Text(path) }, onClick = { folder = path; folderMenu = false }) }
                    }
                }
            }
        }
        if (!searching && visible.isEmpty()) item { NotebookEmpty(query.isNotBlank(), filter == "回收站", { onNew(folder) }, onImport) }
        items(visible, key = { it.record.id }) { note ->
            NoteListCard(note, matches.orEmpty().firstOrNull { it.note.record.id == note.record.id }.takeIf { query.isNotBlank() },
                onClick = { if (note.record.deletedAt == null) model.open(note.record.id) else model.restore(note.record.id) }, onPin = { model.pin(note.record.id) }, onTag = { query = "#$it" })
        }
    }
}

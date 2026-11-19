package win.iqwqi.xiangece.feature.notebook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import win.iqwqi.xiangece.feature.notebook.data.*
import win.iqwqi.xiangece.feature.notebook.domain.*

@Composable
internal fun NotebookAskPage(model: NotebookResearchModel, state: NotebookResearchState,
    notes: List<NoteDocument>, onOpenNote: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var citation by remember { mutableStateOf<Pair<NotePassage, String>?>(null) }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.testTag("notebook-ask"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(state.question, model::question, enabled = !state.busy, minLines = 2, maxLines = 4,
                label = { Text("想找什么，或者想了解什么？") }, placeholder = { Text("例如：之前保存的安卓状态栏遮挡解决方法") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = model::findLocal, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("本机查找") }
                Button(onClick = { model.prepare("expand") }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("换个说法找") }
            }
            Text("换个说法找：联网理解你的描述，再在本机检索资料。", style = MaterialTheme.typography.bodySmall)
        }
        if (state.expansions.isNotEmpty()) item {
            Text("这次还找了：${state.expansions.joinToString("、")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        state.answer?.let { answer ->
            item { Text("根据所选资料", style = MaterialTheme.typography.titleLarge) }
            items(answer.points) { point ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(point.text)
                        point.evidence.forEach { evidence ->
                            state.passages.firstOrNull { it.id == evidence.id }?.let { passage ->
                                val current = notes.firstOrNull { it.record.id == passage.noteId }
                                val valid = NotebookEvidence.stillCurrent(passage, current)
                                TextButton(onClick = { citation = passage to evidence.quote }) {
                                    Text("${if (valid) "查看依据" else "依据已变化，请复核"} · ${passage.title}")
                                }
                            }
                        }
                    }
                }
            }
            if (answer.missing.isNotBlank()) item { Text("还缺少：${answer.missing}", style = MaterialTheme.typography.bodyMedium) }
            if (answer.rejected > 0) item { Text("${answer.rejected} 个未通过引用核对的要点未显示。", style = MaterialTheme.typography.bodySmall) }
            item { Text("引用可核对原文，结论仍可能理解有误。", style = MaterialTheme.typography.bodySmall) }
            if (answer.points.isNotEmpty()) item { OutlinedButton(onClick = model::keepAnswer, enabled = !state.busy) { Text("将回答整理为笔记") } }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本次使用的资料", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { choosing = true }, enabled = !state.busy) { Text("选择笔记") }
            }
            Text("已选 ${state.selected.size} 段；可以展开核对，取消不相关的片段。", style = MaterialTheme.typography.bodySmall)
        }
        items(state.passages, key = { it.id }) { passage ->
            var expanded by remember(passage.id) { mutableStateOf(false) }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(passage.id in state.selected, { model.selectPassage(passage.id) }, enabled = !state.busy)
                        Text(passage.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "展开") }
                    }
                    Text(passage.text, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        item { Button(onClick = { model.prepare("answer") }, enabled = !state.busy && state.selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("根据所选资料回答") } }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text("选择笔记") }, text = {
        Column {
            OutlinedTextField(filter, { filter = it }, label = { Text("筛选标题") }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = 360.dp)) { items(notes.filter { it.record.deletedAt == null && it.record.title.contains(filter, true) }) { note ->
                TextButton(onClick = { model.includeNote(note); choosing = false }) { Text(note.record.title + " · " + note.record.folder) }
            } }
        }
    }, confirmButton = { TextButton(onClick = { choosing = false }) { Text("关闭") } })
    citation?.let { (passage, quote) ->
        Dialog(onDismissRequest = { citation = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(passage.title, style = MaterialTheme.typography.titleLarge)
                    Text("引用：$quote", color = MaterialTheme.colorScheme.primary)
                    Text("下方为回答时使用的原文片段", style = MaterialTheme.typography.labelLarge)
                    Text(passage.text, Modifier.weight(1f).verticalScroll(rememberScrollState()))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { citation = null }) { Text("关闭") }
                        Button(onClick = { scope.launch {
                            val current = withContext(Dispatchers.IO) { runCatching { model.collection.repository.store.read(passage.noteId) }.getOrNull() }
                            if (NotebookEvidence.stillCurrent(passage, current)) { citation = null; onOpenNote(passage.noteId) }
                            else { citation = null; model.message("笔记已修改或移入回收站，引用保留为快照。请重新查找核对。") }
                        } }) { Text("打开对应笔记") }
                    }
                }
            }
        }
    }
}

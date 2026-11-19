package win.iqwqi.xiangece.feature.notebook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.feature.notebook.domain.*

@Composable
internal fun NotebookCollectPage(model: NotebookResearchModel, state: NotebookResearchState,
    onSaved: (String) -> Unit, onViewImage: (String) -> Unit) {
    val draft = state.draft ?: return
    val source = draft.source
    var expanded by remember(source.id) { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).testTag("notebook-collect"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(draft.title, { text -> model.editDraft { it.copy(title = text.take(100)) } },
                label = { Text("笔记名称") }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) }
            source.imagePath?.let { path -> item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onViewImage(path) }) { Text("查看原图 · 支持放大") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = model::localOcr, enabled = !state.busy) { Text("本机识别") }
                            OutlinedButton(onClick = { model.prepare("image") }, enabled = !state.busy) { Text("联网识别") }
                        }
                    }
                }
            } }
            if (source.kind in setOf("text", "image")) item {
                OutlinedTextField(source.text, { text ->
                    if (text.length <= 100_000) model.editDraft { current ->
                        val blocks = NotebookEvidence.collectText(text)
                        current.copy(source = current.source.copy(blocks = blocks), points = emptyList(), selected = blocks.take(6).map { it.id }.toSet())
                    }
                }, label = { Text(if (source.kind == "image") "核对识别文字" else "原始文字") },
                    minLines = 5, maxLines = 10, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            }
            item {
                Text("选择整理范围", style = MaterialTheme.typography.titleMedium)
                Text("原始材料会完整保留；仅勾选的片段参与本次联网整理。", style = MaterialTheme.typography.bodySmall)
                Text("已选 ${draft.selected.size} 段 · ${source.blocks.filter { it.id in draft.selected }.sumOf { it.text.length }} 字", style = MaterialTheme.typography.labelLarge)
            }
            itemsIndexed(source.blocks, key = { _, block -> block.id }) { index, block ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(block.id in draft.selected, { checked -> model.editDraft {
                                it.copy(selected = if (checked) it.selected + block.id else it.selected - block.id, points = emptyList())
                            } }, enabled = !state.busy)
                            Text(block.startMillis?.let(NotebookEvidence::timeLabel) ?: "片段 ${index + 1}", Modifier.weight(1f))
                            TextButton(onClick = { expanded = if (block.id in expanded) expanded - block.id else expanded + block.id }) { Text(if (block.id in expanded) "收起" else "展开") }
                        }
                        Text(block.text, style = MaterialTheme.typography.bodySmall, maxLines = if (block.id in expanded) Int.MAX_VALUE else 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
            item { Button(onClick = { model.prepare("organize") }, enabled = !state.busy && draft.selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("整理所选内容") } }
            if (draft.points.isNotEmpty()) item {
                Text("整理预览", style = MaterialTheme.typography.titleLarge)
                Text("可修改或移除要点。引用已核对文字是否存在，仍请检查结论是否准确。", style = MaterialTheme.typography.bodySmall)
            }
            itemsIndexed(draft.points) { index, point ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(point.text, { text -> model.editDraft { it.copy(points = it.points.mapIndexed { i, p -> if (i == index) p.copy(text = text.take(1800)) else p }) } },
                            enabled = !state.busy, label = { Text("要点 ${index + 1}") }, modifier = Modifier.fillMaxWidth())
                        point.evidence.forEach { ref -> Text("依据：${ref.quote}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = { model.editDraft { it.copy(points = it.points.filterIndexed { i, _ -> i != index }) } }, enabled = !state.busy) { Text("移除此要点") }
                    }
                }
            }
        }
        Surface(shadowElevation = 3.dp) {
            Button(onClick = { model.save(onSaved) }, enabled = !state.busy && draft.title.isNotBlank() &&
                (source.text.isNotBlank() || source.imagePath != null), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(if (draft.points.isEmpty()) "保存原始材料为笔记" else "确认并保存笔记")
            }
        }
    }
}

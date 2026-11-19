package win.iqwqi.xiangece.feature.notebook.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import win.iqwqi.xiangece.feature.notebook.data.*
import win.iqwqi.xiangece.feature.notebook.domain.NotebookEvidence
import win.iqwqi.xiangece.feature.diting.ui.DitingActivity
import win.iqwqi.xiangece.ui.components.SourceImagePreview

@Composable
internal fun NotebookSourceDialog(note: NoteDocument, model: NotebookResearchModel, onDismiss: () -> Unit, onOpenNote: (String) -> Unit) {
    var statuses by remember(note.record.id) { mutableStateOf<List<SourceStatus>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var image by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(note.record.sources) {
        statuses = note.record.sources.map { model.collection.status(it) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                Row {
                    Text("来源与回听", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    statuses.forEach { status ->
                        val source = status.source
                        item {
                            Text(source.title, style = MaterialTheme.typography.titleMedium)
                            Text(status.message, style = MaterialTheme.typography.bodySmall,
                                color = if (status.changedBlocks.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            source.imagePath?.let { path -> TextButton(onClick = {
                                image = model.collection.repository.store.attachment(path)?.path
                                if (image == null) message = "原图文件不可用，文字快照仍保留"
                            }) { Text("查看原图") } }
                        }
                        items(source.blocks, key = { source.id + it.id }) { block ->
                            Card {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(block.startMillis?.let(NotebookEvidence::timeLabel) ?: "原文片段", style = MaterialTheme.typography.labelLarge)
                                    if (block.id in status.changedBlocks) Text("此段需要复核", color = MaterialTheme.colorScheme.error)
                                    Text(block.text)
                                    block.noteId?.let { noteId -> TextButton(onClick = { scope.launch {
                                        val current = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            runCatching { model.collection.repository.store.read(noteId) }.getOrNull()
                                        }
                                        if (current == null || current.record.deletedAt != null) message = "原笔记不可用，快照仍保留"
                                        else { onDismiss(); onOpenNote(noteId) }
                                    } }) { Text("打开原笔记 · ${block.noteTitle.orEmpty()}") } }
                                    if (source.kind == "class" && source.sessionId != null) TextButton(onClick = { scope.launch {
                                        val latest = model.collection.status(source)
                                        statuses = statuses.map { if (it.source.id == source.id) latest else it }
                                        context.startActivity(Intent(context, DitingActivity::class.java)
                                            .putExtra("notebook_session", source.sessionId)
                                            .putExtra("notebook_session_created", source.sessionCreatedAt)
                                            .putExtra("notebook_segment", block.segmentId)
                                            .putExtra("notebook_revision", block.transcriptRevision))
                                    } }) { Text("回听这一段") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    image?.let { SourceImagePreview(it) { image = null } }
}

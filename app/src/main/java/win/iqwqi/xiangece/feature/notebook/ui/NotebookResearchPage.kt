package win.iqwqi.xiangece.feature.notebook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import win.iqwqi.xiangece.feature.notebook.data.*
import win.iqwqi.xiangece.ui.components.SourceImagePreview
import win.iqwqi.xiangece.ui.components.AppPageHeader

@Composable
internal fun NotebookResearchPage(model: NotebookResearchModel, state: NotebookResearchState,
    notes: List<NoteDocument>, onImage: () -> Unit, onSaved: (String) -> Unit, onOpenNote: (String) -> Unit) {
    var discard by remember { mutableStateOf(false) }
    var image by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        AppPageHeader(if (state.page == "ask") "问问笔记" else "收集资料",
            modifier = Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp),
            onBack = { model.page(null) }, backDescription = "返回笔记库") {
            if (state.page == "collect" && state.draft != null) TextButton(onClick = { discard = true }, enabled = !state.busy) { Text("放弃") }
        }
        state.message?.let { message ->
            Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(12.dp)) {
                    Text(message, style = MaterialTheme.typography.bodySmall)
                    if (state.retryKind != null && !state.busy) TextButton(onClick = model::retry) { Text("核对后重试") }
                }
            }
        }
        if (state.busy) Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(state.busyLabel, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
            if (!state.committing) TextButton(onClick = model::stop) { Text("停止") }
        }
        if (state.page == "ask") NotebookAskPage(model, state, notes, onOpenNote)
        else if (state.draft != null) NotebookCollectPage(model, state, onSaved,
            onViewImage = { path -> image = model.collection.repository.store.attachment(path)?.path })
        else {
            val sessions by model.sessions.collectAsStateWithLifecycle()
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("选择材料，核对后留成笔记。联网整理会在发送前请你确认。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Button(onClick = { model.beginText() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("写入或粘贴文字") } }
                item { OutlinedButton(onClick = onImage, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("从图片收集") } }
                item { Text("已保存的课堂", style = MaterialTheme.typography.titleMedium) }
                items(sessions.filter { it.status !in setOf("draft", "recording", "paused", "processing") }, key = { it.id }) { session ->
                    Card(onClick = { model.beginClass(session.id) }, enabled = !state.busy, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(session.title); Text("选择转写片段 · 保留回听位置", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (sessions.isEmpty()) item { Text("课堂录音保存后，可以在这里收集转写内容。", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃这次收集？") },
        text = { Text("已保存的笔记和谛听录音不会受到影响。") },
        confirmButton = { TextButton(onClick = { discard = false; model.discard() }) { Text("放弃收集") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
    image?.let { SourceImagePreview(it) { image = null } }
    state.consent?.let { consent -> NotebookConsentDialog(consent, model) }
}

@Composable
private fun NotebookConsentDialog(consent: NotebookConsent, model: NotebookResearchModel) {
    var image by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = model::dismissConsent, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                Text("确认本次发送内容", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("接收地址：${model.cloud.endpoint(consent.settings)}", Modifier.padding(top = 16.dp))
                    Text("模型：${if (consent.kind == "image") consent.settings.aiVisionModel else consent.settings.aiModel}")
                    if (model.cloud.endpoint(consent.settings).startsWith("http://")) Text("此地址使用未加密连接", color = MaterialTheme.colorScheme.error)
                    Text(if (consent.kind == "image") "发送下面这张图片用于识别文字。" else "只发送下列文字。本次不发送原图、录音或其他笔记。", style = MaterialTheme.typography.bodySmall)
                    if (consent.imagePath != null) TextButton(onClick = { image = model.collection.repository.store.attachment(consent.imagePath)?.path }) { Text("查看将要发送的图片") }
                    else {
                        Text(consent.question)
                        consent.material.forEach { (id, text) ->
                            HorizontalDivider(); Text(text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = model::dismissConsent, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(onClick = model::confirm, modifier = Modifier.weight(1f)) { Text("确认发送") }
                }
            }
        }
    }
    image?.let { SourceImagePreview(it) { image = null } }
}

package win.iqwqi.xiangece.feature.diting.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.feature.diting.domain.DitingMode
import win.iqwqi.xiangece.feature.diting.domain.DitingLanguageMode
import win.iqwqi.xiangece.ui.components.AppFormSheet

@Composable
internal fun DitingSetupSheet(
    title: String, onTitle: (String) -> Unit,
    mode: DitingMode, onMode: (DitingMode) -> Unit,
    language: DitingLanguageMode, onLanguage: (DitingLanguageMode) -> Unit,
    glossary: String, onGlossary: (String) -> Unit,
    transcriptionEnabled: Boolean, onTranscription: (Boolean) -> Unit,
    aiAnnotationForSession: Boolean, onAnnotation: (Boolean) -> Unit,
    aiAnnotationEnabled: Boolean, aiAnnotationConfigured: Boolean,
    cloudTranscriptionConfigured: Boolean, offlineInstalled: Boolean,
    selectedOfflineTitle: String, localRecognitionAvailable: Boolean,
    notificationsAvailable: Boolean, creatingSession: Boolean,
    onStart: () -> Unit, onDismiss: () -> Unit, onOpenResources: () -> Unit,
) {
    var showOptions by remember { mutableStateOf(false) }
    AppFormSheet(
        title = "新录音", subtitle = "选择这节课的记录方式",
        onConfirm = onStart, onDismiss = onDismiss,
        confirmEnabled = !creatingSession,
        confirmLabel = if (creatingSession) "正在准备…" else "开始录音",
    ) {
        OutlinedTextField(title, onTitle, label = { Text("课堂名称") }, placeholder = { Text("例如：高等数学第 3 讲") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (aiAnnotationEnabled || aiAnnotationConfigured) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = aiAnnotationForSession,
                    onCheckedChange = onAnnotation,
                    enabled = aiAnnotationConfigured,
                )
                Column(Modifier.weight(1f)) {
                    Text("智能整理本节课")
                    Text(
                        if (aiAnnotationConfigured) "将转写文字发送到已配置服务，整理提问与重点" else "请先完成智能服务设置",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = transcriptionEnabled,
                onCheckedChange = onTranscription,
            )
            Column(Modifier.weight(1f)) {
                Text("课堂文字记录")
                Text(
                    when {
                        !transcriptionEnabled -> "已关闭，只保存录音"
                        cloudTranscriptionConfigured -> "优先使用云端转写，将上传本节课的音频片段"
                        offlineInstalled -> "使用已选离线资源：$selectedOfflineTitle"
                        localRecognitionAvailable -> "使用手机识别"
                        else -> "暂未准备好，仍可录音，之后可补充文字"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (transcriptionEnabled && cloudTranscriptionConfigured) {
                    Text(
                        if (offlineInstalled) "云端失败时尝试所选离线资源；原音继续保存。"
                        else "云端失败仍保存原音，可之后回听或补充文字。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "戴耳机或使用其他应用时，也可以继续录音。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!notificationsAvailable) {
                    Text("通知权限未开启，课堂提醒可能无法及时出现", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        TextButton(onClick = onOpenResources) { Text("管理离线资源") }
        TextButton(onClick = { showOptions = !showOptions }) {
            Text(if (showOptions) "收起识别选项" else "${mode.label} · ${language.label} · 识别选项")
        }
        if (showOptions) {
            Text("课堂类型", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DitingMode.entries.forEach { item -> FilterChip(mode == item, { onMode(item) }, label = { Text(item.label) }) }
            }
            Text("识别语言", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DitingLanguageMode.entries.forEach { item ->
                    FilterChip(language == item, { onLanguage(item) }, label = { Text(item.label) })
                }
            }
            OutlinedTextField(
                value = glossary,
                onValueChange = onGlossary,
                label = { Text("课上专有名词（可选）") },
                supportingText = { Text("例如：Cache、傅里叶变换。是否采用取决于所选识别服务，可回听确认结果。") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

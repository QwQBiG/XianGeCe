package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.domain.model.DraftType
import win.iqwqi.xiangece.domain.parser.DraftConfirmationValidator
import win.iqwqi.xiangece.ui.DraftEditorState
import win.iqwqi.xiangece.ui.components.AppConfirmDialog
import win.iqwqi.xiangece.ui.components.AppFormSheet
import win.iqwqi.xiangece.ui.components.AppTextField
import win.iqwqi.xiangece.ui.confirmationFields

@Composable
fun DraftEditorDialog(
    editor: DraftEditorState,
    aiEnabled: Boolean,
    isWorking: Boolean,
    semesterWeekCount: Int,
    onDismiss: () -> Unit,
    onSourceTextChange: (String) -> Unit,
    onReparse: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDateTimeChange: (String) -> Unit,
    onCourseNameChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onTeachingWeekChange: (String) -> Unit,
    onDayOfWeekChange: (String) -> Unit,
    onStartPeriodChange: (String) -> Unit,
    onEndPeriodChange: (String) -> Unit,
    onAmbiguitiesAcknowledged: (Boolean) -> Unit,
    onTypeChange: (DraftType) -> Unit,
    onEnhance: () -> Unit,
    onConfirm: () -> Unit,
) {
    var confirmAi by remember { mutableStateOf(false) }
    val validationError = DraftConfirmationValidator.error(
        editor.confirmationFields(),
        semesterWeekCount,
    )
    AppFormSheet(
        title = "确认草稿",
        subtitle = "只有确认后才会写入日程",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmLabel = "确认写入",
        confirmEnabled = validationError == null && !isWorking,
    ) {
        AppTextField(
            value = editor.sourceText,
            onValueChange = onSourceTextChange,
            label = "识别原文（可修正）",
            singleLine = false,
            minLines = 3,
            maxLines = 7,
        )
        OutlinedButton(
            onClick = onReparse,
            enabled = editor.sourceText.isNotBlank() && !isWorking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("按修改后的原文重新解析")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                DraftType.TASK to "任务",
                DraftType.EVENT to "事件",
                DraftType.NOTE to "备忘",
                DraftType.COURSE_MEETING to "课程",
            ).forEach { (type, label) ->
                FilterChip(
                    selected = editor.draft.type == type,
                    onClick = { onTypeChange(type) },
                    label = { Text(label) },
                )
            }
        }
        AppTextField(
            value = editor.title,
            onValueChange = onTitleChange,
            label = "标题",
        )
        AppTextField(
            value = editor.dateTimeText,
            onValueChange = onDateTimeChange,
            label = "日期与时间",
            supportingText = "格式：2026-09-01 18:00；有歧义时请手动确认",
        )
        AppTextField(
            value = editor.courseName,
            onValueChange = onCourseNameChange,
            label = if (editor.draft.type == DraftType.COURSE_MEETING) "课程名称" else "关联课程（可选）",
        )
        AppTextField(
            value = editor.location,
            onValueChange = onLocationChange,
            label = "地点（可选）",
        )
        if (editor.draft.type == DraftType.COURSE_MEETING) {
            NumericDraftFields(
                label = "教学周（留空表示整学期） / 星期（1–7）",
                values = listOf(editor.teachingWeekText, editor.dayOfWeekText),
                onChange = { index, value ->
                    if (index == 0) onTeachingWeekChange(value) else onDayOfWeekChange(value)
                },
            )
            NumericDraftFields(
                label = "开始节 / 结束节",
                values = listOf(editor.startPeriodText, editor.endPeriodText),
                onChange = { index, value ->
                    if (index == 0) onStartPeriodChange(value) else onEndPeriodChange(value)
                },
            )
        }
        if (editor.draft.ambiguities.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("需要你确认", fontWeight = FontWeight.SemiBold)
                    editor.draft.ambiguities.forEach { Text("• $it") }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = editor.ambiguitiesAcknowledged,
                            onCheckedChange = onAmbiguitiesAcknowledged,
                        )
                        Text("我已逐项核对并确认")
                    }
                }
            }
        }
        Text(
            "识别置信度 ${(editor.draft.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        validationError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (aiEnabled) {
            OutlinedButton(
                onClick = { confirmAi = true },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Text(" 使用 AI 增强")
            }
        }
    }

    if (confirmAi) {
        AppConfirmDialog(
            title = "发送 OCR 文字？",
            message = "将发送当前 OCR 文字和学期信息到你配置的接口，不发送原图。请确认文字中不含不希望离开设备的隐私。",
            onConfirm = {
                confirmAi = false
                onEnhance()
            },
            onDismiss = { confirmAi = false },
            confirmLabel = "同意并发送",
        )
    }
}

@Composable
private fun NumericDraftFields(
    label: String,
    values: List<String>,
    onChange: (Int, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEachIndexed { index, value ->
                AppTextField(
                    value = value,
                    onValueChange = { onChange(index, it.filter(Char::isDigit)) },
                    label = "",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                )
            }
        }
    }
}

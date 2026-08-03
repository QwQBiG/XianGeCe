package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import win.iqwqi.xiangece.domain.model.WeekParity
import win.iqwqi.xiangece.ui.TimetableEditorState
import win.iqwqi.xiangece.ui.TimetableRowState
import win.iqwqi.xiangece.ui.components.PaperCard

/** A roomy review workspace; imports never write directly into the timetable. */
@Composable
fun TimetableEditorDialog(
    editor: TimetableEditorState,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onUpdateRow: (Int, TimetableRowState) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    fun rowValid(row: TimetableRowState): Boolean {
        if (row.name.isBlank()) return true
        val day = row.day.toIntOrNull()
        val startPeriod = row.startPeriod.toIntOrNull()
        val endPeriod = row.endPeriod.toIntOrNull()
        val startWeek = row.startWeek.toIntOrNull()
        val endWeek = row.endWeek.toIntOrNull()
        return day in 1..7 &&
            startPeriod != null && endPeriod != null && startPeriod in 1..24 && endPeriod in startPeriod..24 &&
            startWeek != null && endWeek != null && startWeek > 0 && endWeek >= startWeek
    }

    val invalidCount = editor.rows.count { !rowValid(it) }
    val recognizedRows = editor.rows.count { it.name.isNotBlank() }
    val missingLocationCount = editor.rows.count { it.name.isNotBlank() && it.location.isBlank() }
    val missingTeacherCount = editor.rows.count { it.name.isNotBlank() && it.teacher.isBlank() }
    var showSource by remember(editor.inboxId) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("核对并写入课表", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                PaperCard {
                    Text(editor.sourceLabel, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text("识别到 $recognizedRows 个上课时段。请确认课程名、楼宇教室和老师后再写入。", style = MaterialTheme.typography.bodySmall)
                    if (missingLocationCount > 0 || missingTeacherCount > 0) {
                        Text(
                            buildString {
                                append("待补充：")
                                if (missingLocationCount > 0) append("$missingLocationCount 个教室")
                                if (missingLocationCount > 0 && missingTeacherCount > 0) append("、")
                                if (missingTeacherCount > 0) append("$missingTeacherCount 位老师")
                            },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { showSource = !showSource }) {
                        Text(if (showSource) "收起识别原文" else "查看识别原文")
                    }
                    if (showSource) {
                        Text(
                            editor.sourceText.take(1_500).ifBlank { "没有可显示的识别原文" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.heightIn(max = 120.dp),
                        )
                    }
                }
                Text(
                    "课程名、教室、教师是导入的核心字段。空白项可直接补充；此操作只追加缺失时段，不会覆盖原有课表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(editor.rows, key = { index, _ -> index }) { index, row ->
                        TimetableReviewRow(
                            index = index,
                            row = row,
                            valid = rowValid(row),
                            onUpdate = { onUpdateRow(index, it) },
                            onDelete = { onRemoveRow(index) },
                        )
                    }
                    item {
                        OutlinedButton(onClick = onAddRow, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text(" 添加一个上课时段")
                        }
                    }
                }
                if (invalidCount > 0) {
                    Text("还有 $invalidCount 条的星期、节次或周次不正确，请先修改。", color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("暂不导入") }
                    Button(
                        onClick = onConfirm,
                        enabled = recognizedRows > 0 && invalidCount == 0 && !isWorking,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (isWorking) "写入中…" else "确认写入课表") }
                }
            }
        }
    }
}

@Composable
private fun TimetableReviewRow(
    index: Int,
    row: TimetableRowState,
    valid: Boolean,
    onUpdate: (TimetableRowState) -> Unit,
    onDelete: () -> Unit,
) {
    PaperCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("上课时段 ${index + 1}", fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除这一条")
            }
        }
        OutlinedTextField(row.name, { onUpdate(row.copy(name = it)) }, label = { Text("课程名称") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(row.location, { onUpdate(row.copy(location = it)) }, label = { Text("楼宇与教室，例如 明理楼 0343") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(row.teacher, { onUpdate(row.copy(teacher = it)) }, label = { Text("任课老师") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("星期", row.day, Modifier.weight(1f)) { onUpdate(row.copy(day = it)) }
            NumberField("开始节", row.startPeriod, Modifier.weight(1f)) { onUpdate(row.copy(startPeriod = it)) }
            NumberField("结束节", row.endPeriod, Modifier.weight(1f)) { onUpdate(row.copy(endPeriod = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("开始周", row.startWeek, Modifier.weight(1f)) { onUpdate(row.copy(startWeek = it)) }
            NumberField("结束周", row.endWeek, Modifier.weight(1f)) { onUpdate(row.copy(endWeek = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeekParity.entries.forEach { parity ->
                FilterChip(
                    selected = row.parity == parity,
                    onClick = { onUpdate(row.copy(parity = parity)) },
                    label = { Text(when (parity) { WeekParity.ALL -> "每周"; WeekParity.ODD -> "单周"; WeekParity.EVEN -> "双周" }) },
                )
            }
        }
        if (!valid) Text("请检查星期（1-7）、节次（1-24）和教学周。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
    )
}

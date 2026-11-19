package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import win.iqwqi.xiangece.data.local.*
import win.iqwqi.xiangece.domain.model.TaskStatus
import win.iqwqi.xiangece.ui.components.PaperCard

@Composable
internal fun CalendarAgendaList(
    date: LocalDate, tasks: List<TaskEntity>, events: List<CampusEventEntity>,
    undated: List<TaskEntity>, note: String,
    onAddTask: () -> Unit, onToggleTask: (TaskEntity) -> Unit,
    onEditTask: (TaskEntity) -> Unit, onDeleteTask: (TaskEntity) -> Unit,
    onEditEvent: (CampusEventEntity) -> Unit, onEditNote: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().testTag("calendar-agenda"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("calendar-selected-date"))
                    Text("${tasks.size} 项待办 · ${events.size} 项日程",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                FilledTonalButton(onClick = onAddTask) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Text("待办")
                }
            }
        }
        if (tasks.isEmpty() && events.isEmpty()) item {
            Text("这一天还没有安排，留些时间给自己。", Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(tasks, key = { "task-${it.id}" }) { task ->
            CalendarTaskCard(task, onToggleTask, onEditTask, onDeleteTask)
        }
        items(events, key = { "event-${it.id}" }) { event ->
            CalendarEventCard(event, onEdit = { onEditEvent(event) })
        }
        item {
            PaperCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("当日手记", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onEditNote) { Text(if (note.isBlank()) "写备注" else "编辑备注") }
                }
                if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (undated.isNotEmpty()) {
            item { Text("未安排日期 · ${undated.size}", style = MaterialTheme.typography.titleMedium) }
            items(undated, key = { "undated-${it.id}" }) { task ->
                CalendarTaskCard(task, onToggleTask, onEditTask, onDeleteTask)
            }
        }
    }
}

internal fun calendarTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm"))
internal fun calendarEditTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

@Composable
internal fun CalendarTaskCard(task: TaskEntity, onToggle: (TaskEntity) -> Unit,
    onEdit: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit) {
    val done = task.status == TaskStatus.DONE
    PaperCard(modifier = Modifier.testTag("calendar-task-${task.id}")) {
        Text(task.title, Modifier.fillMaxWidth().testTag("calendar-title-${task.id}"),
            style = MaterialTheme.typography.titleMedium,
            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None)
        if (task.note.isNotBlank()) Text(task.note, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (done) "已完成" else task.dueAtEpochMillis?.let { "${calendarTime(it)} 截止" } ?: "待安排日期",
                Modifier.weight(1f), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium)
            Checkbox(done, onCheckedChange = { onToggle(task) },
                modifier = Modifier.testTag("calendar-toggle-${task.id}"))
            IconButton(onClick = { onEdit(task) }) { Icon(Icons.Outlined.Edit, "编辑待办：${task.title}") }
            IconButton(onClick = { onDelete(task) }) { Icon(Icons.Outlined.DeleteOutline, "删除待办：${task.title}") }
        }
    }
}

@Composable
internal fun CalendarEventCard(event: CampusEventEntity, onEdit: () -> Unit) {
    PaperCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${calendarTime(event.startsAtEpochMillis)} · 日程", Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑日程：${event.title}") }
        }
        Text(event.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
        if (event.location.isNotBlank()) Text(event.location, style = MaterialTheme.typography.bodyMedium)
        if (event.note.isNotBlank()) Text(event.note, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

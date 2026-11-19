package win.iqwqi.xiangece.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.LocalDate
import kotlinx.coroutines.launch
import win.iqwqi.xiangece.data.local.*
import win.iqwqi.xiangece.ui.components.AppConfirmDialog
import win.iqwqi.xiangece.ui.components.AppFormSheet
import win.iqwqi.xiangece.ui.components.AppTextField

@Composable
internal fun CalendarEditors(
    date: LocalDate, note: String, showNote: Boolean, addTask: Boolean,
    task: TaskEntity?, event: CampusEventEntity?, deleting: TaskEntity?,
    onCloseNote: () -> Unit, onCloseTask: () -> Unit, onCloseEvent: () -> Unit, onCloseDelete: () -> Unit,
    onSaveNote: suspend (Long, String) -> Boolean,
    onCreateTask: (String, String, String) -> Unit,
    onUpdateTask: (TaskEntity, String, String, String) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onUpdateEvent: (CampusEventEntity, String, String, String, String) -> Unit,
) {
    if (showNote) {
        var draft by rememberSaveable(date.toEpochDay()) { mutableStateOf(note) }
        var saving by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        AppFormSheet(title = "${date.monthValue}月${date.dayOfMonth}日 · 手记",
            subtitle = "保存空白内容即可移除这一天的备注。",
            onConfirm = {
                saving = true
                scope.launch {
                    try { if (onSaveNote(date.toEpochDay(), draft)) onCloseNote() }
                    finally { saving = false }
                }
            }, onDismiss = { if (!saving) onCloseNote() }, confirmEnabled = !saving && draft.length <= 10_000,
            confirmLabel = if (saving) "保存中" else "保存备注") {
            AppTextField(draft, { draft = it }, "写下这一天", singleLine = false, minLines = 5, maxLines = 10,
                enabled = !saving, isError = draft.length > 10_000)
            Text("${draft.length} / 10000")
        }
    }
    if (addTask || task != null) {
        ScheduleEditDialog(
            title = if (task == null) "新增待办" else "编辑待办",
            initialTitle = task?.title.orEmpty(),
            initialDateTime = if (task == null) "$date 18:00" else task.dueAtEpochMillis?.let(::calendarEditTime).orEmpty(),
            initialLocation = "", initialNote = task?.note.orEmpty(), isEvent = false, allowEmptyTime = true,
            onDismiss = onCloseTask,
            onSave = { title, time, _, text ->
                if (task == null) onCreateTask(title, time, text) else onUpdateTask(task, title, time, text)
                onCloseTask()
            },
        )
    }
    event?.let {
        ScheduleEditDialog(title = "编辑日程", initialTitle = it.title,
            initialDateTime = calendarEditTime(it.startsAtEpochMillis), initialLocation = it.location,
            initialNote = it.note, isEvent = true, allowEmptyTime = false, onDismiss = onCloseEvent,
            onSave = { title, time, location, text -> onUpdateEvent(it, title, time, location, text); onCloseEvent() })
    }
    deleting?.let {
        AppConfirmDialog("删除待办？", "“${it.title}”也会从今日移除，可通过下方提示撤销。",
            onConfirm = { onDeleteTask(it); onCloseDelete() }, onDismiss = onCloseDelete,
            confirmLabel = "删除", isDanger = true)
    }
}

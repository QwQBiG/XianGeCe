package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.time.LocalDate
import win.iqwqi.xiangece.data.local.*
import win.iqwqi.xiangece.ui.AppUiState
import win.iqwqi.xiangece.domain.model.TaskStatus

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CalendarScreen(
    state: AppUiState, notes: List<CalendarNoteEntity>, onBack: () -> Unit,
    onSaveNote: suspend (Long, String) -> Boolean,
    onCreateTask: (String, String, String) -> Unit,
    onUpdateTask: (TaskEntity, String, String, String) -> Unit,
    onToggleTask: (TaskEntity) -> Unit, onDeleteTask: (TaskEntity) -> Unit,
    onUpdateEvent: (CampusEventEntity, String, String, String, String) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var selectedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var showAgenda by rememberSaveable { mutableStateOf(false) }
    val selected = LocalDate.ofEpochDay(selectedDay)
    var editTask by remember { mutableStateOf<TaskEntity?>(null) }
    var addTask by rememberSaveable { mutableStateOf(false) }
    var editNote by rememberSaveable { mutableStateOf(false) }
    var deleteTask by remember { mutableStateOf<TaskEntity?>(null) }
    var editEvent by remember { mutableStateOf<CampusEventEntity?>(null) }
    val byDate = remember(state.tasks) { state.tasks.filter { it.dueAtEpochMillis != null }
        .groupBy { calendarDate(it.dueAtEpochMillis!!) } }
    val eventsByDate = remember(state.events) { state.events.groupBy { calendarDate(it.startsAtEpochMillis) } }
    val previews = remember(byDate, eventsByDate) {
        (byDate.keys + eventsByDate.keys).associateWith { date ->
            byDate[date].orEmpty().sortedWith(compareBy<TaskEntity> { it.status == TaskStatus.DONE }
                .thenBy { it.dueAtEpochMillis }).map { CalendarDayPreview(it.title, it.status == TaskStatus.DONE) } +
                eventsByDate[date].orEmpty().sortedBy { it.startsAtEpochMillis }.map { CalendarDayPreview(it.title, isEvent = true) }
        }
    }
    val noteByDate = remember(notes) { notes.associateBy { LocalDate.ofEpochDay(it.dateEpochDay) } }
    val notePreviews = remember(noteByDate) { noteByDate.mapValues { it.value.text } }
    val tasks = byDate[selected].orEmpty().sortedWith(
        compareBy<TaskEntity> { it.status == TaskStatus.DONE }.thenBy { it.dueAtEpochMillis })
    val undated = state.tasks.filter { it.dueAtEpochMillis == null }
    val note = noteByDate[selected]?.text.orEmpty()
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()
            .testTag("calendar-screen")) {
            CalendarMonthCard(selected, today, byDate.keys + eventsByDate.keys, noteByDate.keys,
                onSelect = { selectedDay = it.toEpochDay() }, previews = previews,
                modifier = Modifier.weight(1f), notePreviews = notePreviews,
                onOpenDay = { showAgenda = true }, onBack = onBack,
                onOpenAgenda = { showAgenda = true }, onAddTask = { addTask = true },
                agendaLabel = if (undated.isEmpty()) "当日清单" else "清单 · ${undated.size}项未定日期")
        }
        val editing = editNote || addTask || editTask != null || editEvent != null || deleteTask != null
        if (showAgenda && !editing) {
            ModalBottomSheet(onDismissRequest = { showAgenda = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                CalendarAgendaList(selected, tasks, eventsByDate[selected].orEmpty(), undated, note,
                    onAddTask = { addTask = true }, onToggleTask = onToggleTask,
                    onEditTask = { editTask = it }, onDeleteTask = { deleteTask = it },
                    onEditEvent = { editEvent = it }, onEditNote = { editNote = true })
            }
        }
        CalendarEditors(selected, note, editNote, addTask, editTask, editEvent, deleteTask,
            onCloseNote = { editNote = false }, onCloseTask = { addTask = false; editTask = null },
            onCloseEvent = { editEvent = null }, onCloseDelete = { deleteTask = null },
            onSaveNote, onCreateTask, onUpdateTask, onDeleteTask, onUpdateEvent)
    }
}

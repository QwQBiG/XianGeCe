package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.CampusEventEntity
import win.iqwqi.xiangece.data.local.TaskEntity
import win.iqwqi.xiangece.core.reminder.ReminderTargets
import win.iqwqi.xiangece.domain.model.TaskStatus
import win.iqwqi.xiangece.domain.model.WeekParity
import win.iqwqi.xiangece.domain.semester.TeachingWeekCalculator
import win.iqwqi.xiangece.domain.semester.ScheduleCalculator
import win.iqwqi.xiangece.ui.AppUiState
import win.iqwqi.xiangece.ui.components.BrandHeader
import win.iqwqi.xiangece.ui.components.EmptyStateCard
import win.iqwqi.xiangece.ui.components.AppConfirmDialog
import win.iqwqi.xiangece.ui.components.AppFormSheet
import win.iqwqi.xiangece.ui.components.AppTextField
import win.iqwqi.xiangece.ui.components.InkDivider
import win.iqwqi.xiangece.ui.components.PaperCard
import win.iqwqi.xiangece.ui.components.SectionTitle

private val dateLabel = DateTimeFormatter.ofPattern("M月d日 EEEE")
private val timeLabel = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private val editableTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private enum class TaskViewFilter(val label: String) {
    ALL("全部"),
    DUE_SOON("7天内"),
    OVERDUE("逾期"),
    NO_DATE("无日期"),
}

@Composable
fun TodayScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    onToggleTask: (TaskEntity) -> Unit,
    onUpdateTask: (TaskEntity, String, String) -> Unit,
    onUpdateEvent: (CampusEventEntity, String, String) -> Unit,
    onCreateTask: (String, String, String) -> Unit,
    onCreateEvent: (String, String, String, String) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onDeleteEvent: (CampusEventEntity) -> Unit,
) {
    var pendingTaskDelete by remember { mutableStateOf<TaskEntity?>(null) }
    var pendingEventDelete by remember { mutableStateOf<CampusEventEntity?>(null) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var editingEvent by remember { mutableStateOf<CampusEventEntity?>(null) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }
    var taskFilter by remember { mutableStateOf(TaskViewFilter.ALL) }
    var taskCourseFilter by remember { mutableStateOf<Long?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val deviceZone = ZoneId.systemDefault()
    val nowDateTime = Instant.ofEpochMilli(now).atZone(deviceZone)
    val today = nowDateTime.toLocalDate()
    val nowMinutes = nowDateTime.toLocalTime().let { it.hour * 60 + it.minute }
    val semester = state.semester
    val currentWeek = semester?.let {
        TeachingWeekCalculator.weekOf(LocalDate.ofEpochDay(it.startDateEpochDay), today, it.weekCount)
    } ?: 0
    val semesterProgress = semester?.let {
        TeachingWeekCalculator.progress(LocalDate.ofEpochDay(it.startDateEpochDay), today, it.weekCount)
    } ?: 0f
    val isInSemester = semester?.let {
        TeachingWeekCalculator.contains(LocalDate.ofEpochDay(it.startDateEpochDay), today, it.weekCount)
    } ?: false
    val todayMeetings = state.meetings
        .filter { isInSemester && it.dayOfWeek == today.dayOfWeek.value && it.activeIn(currentWeek) }
        .filter { meeting ->
            val end = state.periods.firstOrNull { it.periodIndex == meeting.endPeriod }?.endMinutes
            end == null || end > nowMinutes
        }
        .sortedBy { it.startPeriod }
    val allTodayMeetings = state.meetings
        .filter { isInSemester && it.dayOfWeek == today.dayOfWeek.value && it.activeIn(currentWeek) }
        .sortedBy { it.startPeriod }
    val completedTodayCount = allTodayMeetings.count { meeting ->
        state.periods.firstOrNull { it.periodIndex == meeting.endPeriod }?.endMinutes?.let { it <= nowMinutes } == true
    }
    val currentMeeting = allTodayMeetings.firstOrNull { meeting ->
        val start = state.periods.firstOrNull { it.periodIndex == meeting.startPeriod }?.startMinutes ?: Int.MAX_VALUE
        val end = state.periods.firstOrNull { it.periodIndex == meeting.endPeriod }?.endMinutes ?: Int.MIN_VALUE
        nowMinutes in start until end
    }
    val allPendingTasks = state.tasks
        .filter { it.status == TaskStatus.TODO }
        .sortedWith(compareBy(nullsLast()) { it.dueAtEpochMillis })
    val taskCourseIds = allPendingTasks.mapNotNull { it.courseId }.distinct()
    val activeCourseFilter = taskCourseFilter?.takeIf(taskCourseIds::contains)
    val pendingTasks = allPendingTasks.filter { task ->
        val matchesTime = when (taskFilter) {
            TaskViewFilter.ALL -> true
            TaskViewFilter.DUE_SOON ->
                task.dueAtEpochMillis?.let { it in now..(now + 7 * 24 * 3_600_000L) } == true
            TaskViewFilter.OVERDUE -> ScheduleCalculator.isOverdue(task.dueAtEpochMillis, now)
            TaskViewFilter.NO_DATE -> task.dueAtEpochMillis == null
        }
        matchesTime && (activeCourseFilter == null || task.courseId == activeCourseFilter)
    }
    val completedTasks = state.tasks
        .filter { it.status == TaskStatus.DONE }
        .sortedByDescending { it.createdAtEpochMillis }
    val tasks = if (showCompleted) completedTasks else pendingTasks
    val events = state.events.filter { it.startsAtEpochMillis >= now }.take(3)


    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            BrandHeader(
                title = "今日",
                subtitle = today.format(dateLabel),
                icon = Icons.Outlined.Home,
                action = {
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Outlined.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { showQuickAdd = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建事项")
                    }
                },
            )
        }
        item {
            PaperCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(semester?.name ?: "尚未设置学期", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (semester == null) "前往设置添加学期"
                            else semesterStatus(semester.startDateEpochDay, semester.weekCount, today, currentWeek),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (semester != null) {
                        Text("${(semesterProgress * 100).toInt().coerceIn(0, 100)}%")
                    }
                }
                if (semester != null) {
                    LinearProgressIndicator(
                        progress = { semesterProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            val currentCourse = currentMeeting?.let { meeting -> state.courses.firstOrNull { it.id == meeting.courseId } }
            PaperCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "今日共 ${allTodayMeetings.size} 节",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            currentCourse?.name ?: "已完成 $completedTodayCount 节",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text("今日剩余 ${todayMeetings.size} 节", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionTitle("今日课程", "已结束自动收起 · 点击课程查看详情") }
        if (todayMeetings.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "今天没有课程",
                    message = "给自己留一点从容，或去「课程」补充课表。",
                    icon = Icons.Outlined.CalendarMonth,
                )
            }
        } else {
            items(todayMeetings, key = { "meeting-${it.id}" }) { meeting ->
                val course = state.courses.firstOrNull { it.id == meeting.courseId }
                PaperCard {
                    Text(course?.name ?: "未命名课程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        todayCourseLabel(meeting, state, nowMinutes),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val location = meeting.location.ifBlank { course?.defaultLocation.orEmpty() }
                    if (location.isNotBlank()) Text("⌖ $location", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    course?.teacher?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(
                    if (showCompleted) "已完成（${completedTasks.size}）" else "待办（${pendingTasks.size}/${allPendingTasks.size}）",
                    if (showCompleted) "可勾选恢复为待办"
                    else "默认提前 ${state.settings.taskReminderHoursFirst} 小时和 ${state.settings.taskReminderHoursSecond} 小时提醒",
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = showCompleted,
                    onClick = { showCompleted = !showCompleted },
                    label = { Text(if (showCompleted) "看待办" else "看完成") },
                )
            }
        }
        if (!showCompleted && allPendingTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TaskViewFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = taskFilter == filter,
                            onClick = { taskFilter = filter },
                            label = { Text(filter.label) },
                        )
                    }
                }
            }
        }
        if (!showCompleted && taskCourseIds.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = activeCourseFilter == null,
                        onClick = { taskCourseFilter = null },
                        label = { Text("全部课程") },
                    )
                    taskCourseIds.forEach { courseId ->
                        val courseName = state.courses.firstOrNull { it.id == courseId }?.name
                            ?: "未命名课程"
                        FilterChip(
                            selected = activeCourseFilter == courseId,
                            onClick = { taskCourseFilter = courseId },
                            label = { Text(courseName) },
                        )
                    }
                }
            }
        }
        if (tasks.isEmpty()) {
            item {
                if (showCompleted) {
                    EmptyStateCard(
                        title = "还没有已完成任务",
                        message = "完成任务后，可以在这里查看和恢复。",
                        icon = Icons.Outlined.CheckCircle,
                    )
                } else {
                    EmptyStateCard(
                        title = "暂无待办",
                        message = "点击右上角新建，或从收件箱确认通知。",
                        icon = Icons.Outlined.TaskAlt,
                        actionLabel = "新建事项",
                        onAction = { showQuickAdd = true },
                    )
                }
            }
        } else {
            items(tasks, key = { "task-${it.id}" }) { task ->
                val courseName = task.courseId?.let { courseId ->
                    state.courses.firstOrNull { it.id == courseId }?.name
                }
                val taskReminders = state.reminders
                    .filter {
                        it.targetType == ReminderTargets.TASK &&
                            it.targetId == task.id &&
                            it.enabled &&
                            it.triggerAtEpochMillis > now
                    }
                    .sortedBy { it.triggerAtEpochMillis }
                PaperCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = task.status == TaskStatus.DONE,
                            onCheckedChange = { onToggleTask(task) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                task.title,
                                fontWeight = FontWeight.Medium,
                                textDecoration = if (task.status == TaskStatus.DONE) TextDecoration.LineThrough else null,
                            )
                            Text(
                                when {
                                    ScheduleCalculator.isOverdue(task.dueAtEpochMillis, now) ->
                                        "已逾期 · ${task.dueAtEpochMillis?.let(::formatEpoch)}"
                                    else -> task.dueAtEpochMillis?.let(::formatEpoch) ?: "未设置截止时间"
                                },
                                color = if (ScheduleCalculator.isOverdue(task.dueAtEpochMillis, now)) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            courseName?.let {
                                Text(
                                    "课程 · $it",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (task.status == TaskStatus.TODO) {
                                Text(
                                    if (taskReminders.isEmpty()) {
                                        "无待触发提醒"
                                    } else {
                                        "${taskReminders.size} 个提醒 · 最近 ${formatEpoch(taskReminders.first().triggerAtEpochMillis)}"
                                    },
                                    color = if (taskReminders.isEmpty()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        IconButton(onClick = { editingTask = task }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "修改任务")
                        }
                        IconButton(onClick = { pendingTaskDelete = task }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除任务")
                        }
                    }
                }
            }
        }
        item { SectionTitle("校园事件", "近期活动一览，已过期的自动隐藏") }
        if (events.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "近期没有事件",
                    message = "新增事件，或在「百宝」里把通知转成日程，都会出现在这里。",
                    icon = Icons.Outlined.Event,
                )
            }
        } else {
            items(events, key = { "event-${it.id}" }) { event ->
                PaperCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            event.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { editingEvent = event }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "修改事件")
                        }
                        IconButton(onClick = { pendingEventDelete = event }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除事件")
                        }
                    }
                    InkDivider()
                    Text(formatEpoch(event.startsAtEpochMillis), color = MaterialTheme.colorScheme.primary)
                    if (event.location.isNotBlank()) Text("⌖ ${event.location}")
                }
            }
        }
    }

    pendingTaskDelete?.let { task ->
        DeleteConfirmation(
            title = "删除任务？",
            message = "“${task.title}”及其尚未触发的提醒会一起删除。",
            onDismiss = { pendingTaskDelete = null },
            onConfirm = {
                onDeleteTask(task)
                pendingTaskDelete = null
            },
        )
    }
    pendingEventDelete?.let { event ->
        DeleteConfirmation(
            title = "删除事件？",
            message = "“${event.title}”及其尚未触发的提醒会一起删除。",
            onDismiss = { pendingEventDelete = null },
            onConfirm = {
                onDeleteEvent(event)
                pendingEventDelete = null
            },
        )
    }
    editingTask?.let { task ->
        ScheduleEditDialog(
            title = "修改任务",
            initialTitle = task.title,
            initialDateTime = task.dueAtEpochMillis?.let(::formatEditable).orEmpty(),
            allowEmptyTime = true,
            onDismiss = { editingTask = null },
            onSave = { title, dateTime ->
                onUpdateTask(task, title, dateTime)
                editingTask = null
            },
        )
    }
    editingEvent?.let { event ->
        ScheduleEditDialog(
            title = "修改事件",
            initialTitle = event.title,
            initialDateTime = formatEditable(event.startsAtEpochMillis),
            allowEmptyTime = false,
            onDismiss = { editingEvent = null },
            onSave = { title, dateTime ->
                onUpdateEvent(event, title, dateTime)
                editingEvent = null
            },
        )
    }
    if (showQuickAdd) {
        QuickAddDialog(
            onDismiss = { showQuickAdd = false },
            onCreateTask = { title, dateTime, note ->
                onCreateTask(title, dateTime, note)
                showQuickAdd = false
            },
            onCreateEvent = { title, dateTime, location, note ->
                onCreateEvent(title, dateTime, location, note)
                showQuickAdd = false
            },
        )
    }
    if (showSearch) {
        SearchOverlay(state = state, onDismiss = { showSearch = false })
    }
}

@Composable
private fun DeleteConfirmation(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppConfirmDialog(
        title = title,
        message = message,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        confirmLabel = "删除",
        isDanger = true,
    )
}

@Composable
private fun ScheduleEditDialog(
    title: String,
    initialTitle: String,
    initialDateTime: String,
    allowEmptyTime: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var editedTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var dateTime by remember(initialDateTime) { mutableStateOf(initialDateTime) }
    val validTime = dateTime.isBlank() && allowEmptyTime ||
        runCatching { LocalDateTime.parse(dateTime.trim(), editableTime) }.isSuccess
    AppFormSheet(
        title = title,
        onConfirm = { onSave(editedTitle, dateTime) },
        onDismiss = onDismiss,
        confirmEnabled = editedTitle.isNotBlank() && validTime,
    ) {
        AppTextField(
            value = editedTitle,
            onValueChange = { editedTitle = it },
            label = "标题",
        )
        AppTextField(
            value = dateTime,
            onValueChange = { dateTime = it },
            label = "时间（yyyy-MM-dd HH:mm）",
            supportingText = if (allowEmptyTime) "任务可以留空，表示无截止时间" else null,
            isError = !validTime,
        )
    }
}

@Composable
private fun QuickAddDialog(
    onDismiss: () -> Unit,
    onCreateTask: (String, String, String) -> Unit,
    onCreateEvent: (String, String, String, String) -> Unit,
) {
    var isEvent by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var dateTime by remember {
        mutableStateOf(
            LocalDateTime.now().plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0)
                .format(editableTime),
        )
    }
    var location by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val validTime = dateTime.isBlank() && !isEvent ||
        runCatching { LocalDateTime.parse(dateTime.trim(), editableTime) }.isSuccess
    AppFormSheet(
        title = "新建事项",
        subtitle = "选择类型后填写信息，事件必须有时间。",
        onConfirm = {
            if (isEvent) onCreateEvent(title, dateTime, location, note)
            else onCreateTask(title, dateTime, note)
        },
        onDismiss = onDismiss,
        confirmLabel = "创建",
        confirmEnabled = title.isNotBlank() && validTime,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !isEvent,
                onClick = { isEvent = false },
                label = { Text("任务") },
            )
            FilterChip(
                selected = isEvent,
                onClick = { isEvent = true },
                label = { Text("事件") },
            )
        }
        AppTextField(
            value = title,
            onValueChange = { title = it },
            label = "标题",
        )
        AppTextField(
            value = dateTime,
            onValueChange = { dateTime = it },
            label = if (isEvent) "开始时间" else "截止时间（可留空）",
            supportingText = "格式：2026-09-01 18:00",
            isError = !validTime,
        )
        if (isEvent) {
            AppTextField(
                value = location,
                onValueChange = { location = it },
                label = "地点",
            )
        }
        AppTextField(
            value = note,
            onValueChange = { note = it },
            label = "备注",
            singleLine = false,
            minLines = 2,
        )
    }
}

private fun CourseMeetingEntity.activeIn(week: Int): Boolean {
    if (week !in startWeek..endWeek) return false
    return when (weekParity) {
        WeekParity.ALL -> true
        WeekParity.ODD -> week % 2 == 1
        WeekParity.EVEN -> week % 2 == 0
    }
}

private fun periodLabel(meeting: CourseMeetingEntity, state: AppUiState): String {
    val start = state.periods.firstOrNull { it.periodIndex == meeting.startPeriod }
    val end = state.periods.firstOrNull { it.periodIndex == meeting.endPeriod }
    val span = "第 ${meeting.startPeriod}${if (meeting.endPeriod != meeting.startPeriod) "–${meeting.endPeriod}" else ""} 节"
    return if (start == null || end == null) span
    else "$span  ${start.startMinutes.toClock()}–${end.endMinutes.toClock()}"
}

private fun todayCourseLabel(meeting: CourseMeetingEntity, state: AppUiState, nowMinutes: Int): String {
    val start = state.periods.firstOrNull { it.periodIndex == meeting.startPeriod }
    val end = state.periods.firstOrNull { it.periodIndex == meeting.endPeriod }
    val base = periodLabel(meeting, state)
    if (start == null || end == null) return base
    return when {
        nowMinutes in start.startMinutes until end.endMinutes -> "上课中 · $base"
        nowMinutes < start.startMinutes -> "还有 ${minutesDistance(start.startMinutes - nowMinutes)} · $base"
        else -> "已结束 · $base"
    }
}

private fun minutesDistance(minutes: Int): String =
    if (minutes < 60) "${minutes} 分钟"
    else "${minutes / 60} 小时 ${minutes % 60} 分钟"

private fun Int.toClock(): String = "%02d:%02d".format(this / 60, this % 60)

private fun formatEpoch(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeLabel)

private fun formatEditable(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(editableTime)

private fun nextCourseLabel(startsAt: Long, now: Long, week: Int): String {
    val dateTime = Instant.ofEpochMilli(startsAt).atZone(ZoneId.systemDefault())
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val day = when (dateTime.toLocalDate()) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> dateTime.format(DateTimeFormatter.ofPattern("M月d日 EEEE"))
    }
    return "$day ${dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))} · 第 $week 周"
}

private fun semesterStatus(
    startEpochDay: Long,
    weekCount: Int,
    today: LocalDate,
    currentWeek: Int,
): String {
    val start = LocalDate.ofEpochDay(startEpochDay)
    val end = start.plusWeeks(weekCount.toLong())
    return when {
        today.isBefore(start) -> "距离开学 ${java.time.temporal.ChronoUnit.DAYS.between(today, start)} 天"
        !today.isBefore(end) -> "本学期已结束"
        else -> "第 $currentWeek 周 · 共 $weekCount 周"
    }
}

/** 单条搜索结果。 */
private data class SearchResult(
    val type: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

/** 跨课程 / 任务 / 事件 / 长期事项做关键词匹配，返回扁平结果列表。 */
private fun computeSearchResults(state: AppUiState, query: String): List<SearchResult> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val results = mutableListOf<SearchResult>()
    state.courses.forEach { course ->
        if (course.name.contains(q, true) || course.teacher.contains(q, true) ||
            course.defaultLocation.contains(q, true) || course.note.contains(q, true)
        ) {
            results.add(
                SearchResult("课程", course.name, course.teacher.ifBlank { "未填写教师" }, Icons.Outlined.CalendarMonth),
            )
        }
    }
    state.tasks.forEach { task ->
        if (task.title.contains(q, true) || task.note.contains(q, true)) {
            results.add(
                SearchResult("任务", task.title, task.dueAtEpochMillis?.let(::formatEpoch) ?: "无截止时间", Icons.Outlined.TaskAlt),
            )
        }
    }
    state.events.forEach { event ->
        if (event.title.contains(q, true) || event.location.contains(q, true) || event.note.contains(q, true)) {
            results.add(
                SearchResult("事件", event.title, formatEpoch(event.startsAtEpochMillis), Icons.Outlined.Event),
            )
        }
    }
    state.habits.forEach { habit ->
        if (habit.title.contains(q, true) || habit.description.contains(q, true)) {
            results.add(
                SearchResult("长期事项", habit.title, habit.description.ifBlank { "坚持中" }, Icons.Outlined.AutoGraph),
            )
        }
    }
    return results
}

/** 全局搜索浮层：输入关键词，按类型分组展示课程/任务/事件/长期事项。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchOverlay(state: AppUiState, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, state) { computeSearchResults(state, query) }
    val grouped = results.groupBy { it.type }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text("搜索", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            AppTextField(
                value = query,
                onValueChange = { query = it },
                label = "搜索课程、任务、事件、习惯",
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            when {
                query.isBlank() -> {
                    Text(
                        "输入关键词，可在课程、任务、事件、长期事项里查找。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                results.isEmpty() -> {
                    EmptyStateCard(
                        title = "没有找到「$query」",
                        message = "换个关键词试试，搜索范围包括标题、备注、地点和教师。",
                        icon = Icons.Outlined.Search,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        grouped.forEach { (type, typeItems) ->
                            item(key = "header-$type") {
                                Text(
                                    "$type（${typeItems.size}）",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            items(typeItems, key = { "$type-${it.title}-${it.subtitle}" }) { result ->
                                PaperCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(result.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f)) {
                                            Text(result.title, fontWeight = FontWeight.Medium, maxLines = 1)
                                            Text(
                                                result.subtitle,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

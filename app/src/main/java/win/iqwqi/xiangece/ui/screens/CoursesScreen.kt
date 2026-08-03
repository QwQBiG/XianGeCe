package win.iqwqi.xiangece.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import win.iqwqi.xiangece.data.local.CourseEntity
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.PeriodTemplateEntity
import win.iqwqi.xiangece.data.local.TimetableEntity
import win.iqwqi.xiangece.data.settings.AppSettings
import win.iqwqi.xiangece.core.importing.TimetableCodeCodec
import win.iqwqi.xiangece.domain.model.WeekParity
import win.iqwqi.xiangece.domain.semester.CourseConflictDetector
import win.iqwqi.xiangece.domain.semester.TeachingWeekCalculator
import win.iqwqi.xiangece.ui.AppUiState
import win.iqwqi.xiangece.ui.components.AppConfirmDialog
import win.iqwqi.xiangece.ui.components.AppFormSheet
import win.iqwqi.xiangece.ui.components.AppTextField
import win.iqwqi.xiangece.ui.components.EmptyStateCard
import win.iqwqi.xiangece.ui.components.PaperCard

typealias SaveCourse = (
    Long?, Long?, String, String, String, Int, Int, Int, Int, Int, WeekParity,
    Long, String,
) -> Unit

private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val courseColors = listOf(
    0xFF52796F, 0xFF4C6A92, 0xFF7B6D9C, 0xFF9B5D66,
    0xFFB07042, 0xFF6D7E45, 0xFF397C8C, 0xFF795548,
)

@Composable
fun CoursesScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    onSaveCourse: SaveCourse,
    onDeleteMeeting: (CourseMeetingEntity) -> Unit,
    onPickTimetablePdf: () -> Unit,
    onPickTimetableHtml: () -> Unit,
    onPickTimetableExcel: () -> Unit,
    onImportTimetableCode: (String) -> Unit,
    onSaveSemester: (String, String, String) -> Unit,
    onCreateTimetable: (String) -> Unit,
    onSwitchTimetable: (Long) -> Unit,
    onRenameTimetable: (Long, String) -> Unit,
    onSaveTimetableLayout: (String, String, String) -> Unit,
    onSavePeriod: (Int, String, String) -> Unit,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onSaveBackgroundOptions: (Float, Boolean) -> Unit,
    /** 当课表已滚到头且用户继续同方向拖时，请求跳转到相邻 Tab（-1=左页，+1=右页）。 */
    onNavigateAdjacent: (Int) -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val semester = state.semester
    val currentWeek = semester?.let {
        TeachingWeekCalculator.weekOf(
            LocalDate.ofEpochDay(it.startDateEpochDay),
            LocalDate.now(),
            it.weekCount,
        )
    } ?: 1
    val maxWeek = (semester?.weekCount ?: 20).coerceAtLeast(1)
    var selectedWeek by remember(semester?.id, currentWeek) { mutableIntStateOf(currentWeek.coerceIn(1, maxWeek)) }
    var selectedDay by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var showWeekGrid by remember { mutableStateOf(true) }
    var newCourseDefaults by remember { mutableStateOf(selectedDay to 1) }
    var editing by remember { mutableStateOf<Pair<CourseEntity?, CourseMeetingEntity?>?>(null) }
    var detail by remember { mutableStateOf<Pair<CourseEntity, CourseMeetingEntity>?>(null) }
    var pendingMeetingDelete by remember { mutableStateOf<CourseMeetingEntity?>(null) }
    var showImportCenter by remember { mutableStateOf(false) }
    var showTimetableControls by remember { mutableStateOf(false) }
    var showTimeSheet by remember { mutableStateOf(false) }
    var showTimetableActions by remember { mutableStateOf(false) }
    var showBackgroundSettings by remember { mutableStateOf(false) }
    var showAddedCourses by remember { mutableStateOf(false) }
    var showFaq by remember { mutableStateOf(false) }
    var renamingTimetable by remember { mutableStateOf<TimetableEntity?>(null) }
    var showCreateTimetableDialog by remember { mutableStateOf(false) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var showCurrentWeekEditor by remember { mutableStateOf(false) }
    var timetableCode by remember { mutableStateOf("") }

    val activeMeetings = state.meetings.filter {
        CourseConflictDetector.activeInWeek(it, selectedWeek)
    }
    val dailyMeetings = activeMeetings
        .filter { it.dayOfWeek == selectedDay }
        .sortedBy { it.startPeriod }
    val todayMeetingCount = activeMeetings.count { it.dayOfWeek == LocalDate.now().dayOfWeek.value }
    val selectedWeekStart = semester?.let {
        val start = LocalDate.ofEpochDay(it.startDateEpochDay)
        start.minusDays((start.dayOfWeek.value - 1).toLong()).plusWeeks((selectedWeek - 1).toLong())
    } ?: LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(
                start = 0.dp,
                top = 10.dp,
                end = 0.dp,
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        CourseTopBar(
            selectedWeek = selectedWeek,
            currentWeek = currentWeek.coerceIn(1, maxWeek),
            todayMeetingCount = todayMeetingCount,
            onAdd = {
                newCourseDefaults = selectedDay to 1
                editing = null to null
            },
            onImport = { showImportCenter = true },
            onShare = {
                val value = TimetableCodeCodec.encode(state.courses, state.meetings)
                timetableCode = value
                clipboard.setText(AnnotatedString(value))
                showCodeDialog = true
            },
            onOpenTimeSheet = { showTimeSheet = true },
            onOpenActions = { showTimetableActions = true },
        )
        if (showWeekGrid) {
            WeeklyTimetableGrid(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp),
                periods = state.periods,
                courses = state.courses,
                meetings = activeMeetings,
                weekStartDate = selectedWeekStart,
                dayWidthDp = state.settings.timetableColumnWidthDp,
                rowHeightDp = state.settings.timetableRowHeightDp,
                periodCount = state.settings.timetablePeriodCount,
                wallpaperPath = state.settings.timetableWallpaperPath,
                wallpaperAlpha = state.settings.timetableWallpaperAlpha,
                showEmptyCellsAlways = state.settings.timetableShowEmptyCellsAlways,
                highlightToday = selectedWeek == currentWeek,
                todayDay = LocalDate.now().dayOfWeek.value,
                nowMinutes = java.time.LocalTime.now().let { it.hour * 60 + it.minute },
                onMeetingClick = { meeting ->
                    state.courses.firstOrNull { it.id == meeting.courseId }?.let { course ->
                        detail = course to meeting
                    }
                },
                onMeetingLongClick = { meeting ->
                    val course = state.courses.firstOrNull { it.id == meeting.courseId }
                    editing = course to meeting
                },
                onEmptyClick = { day, period ->
                    selectedDay = day
                    newCourseDefaults = day to period
                    editing = null to null
                },
                onNavigateAdjacent = onNavigateAdjacent,
            )
            if (activeMeetings.isEmpty()) {
                EmptyStateCard(
                    title = "第 $selectedWeek 周还没有课程",
                    message = "点击课表中的空白格快速添加，也可以从上方导入已有课表。",
                    icon = Icons.Outlined.CalendarMonth,
                )
            }
        } else {
            DaySelector(
                selectedDay = selectedDay,
                onDayChange = { selectedDay = it },
            )
            if (dailyMeetings.isEmpty()) {
                EmptyStateCard(
                    title = "${weekdays[selectedDay - 1]}没有课程",
                    message = "可以切换日期，或点击添加新建课程。",
                    icon = Icons.Outlined.CalendarMonth,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(dailyMeetings, key = { it.id }) { meeting ->
                        val course = state.courses.firstOrNull { it.id == meeting.courseId }
                        val conflictCount = CourseConflictDetector.conflicts(meeting, state.meetings).size
                        PaperCard(
                            modifier = Modifier.clickable {
                                if (course != null) detail = course to meeting else editing = null to meeting
                            },
                        ) {
                            Text(
                                course?.name ?: "未命名课程",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "第 ${meeting.startPeriod}${if (meeting.endPeriod != meeting.startPeriod) "-${meeting.endPeriod}" else ""} 节 · " +
                                    "第 ${meeting.startWeek}-${meeting.endWeek} 周${meeting.weekParity.label()}",
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val location = meeting.location.ifBlank { course?.defaultLocation.orEmpty() }
                            if (location.isNotBlank()) {
                                Text("地点：$location", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            course?.teacher?.takeIf(String::isNotBlank)?.let {
                                Text("教师：$it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (conflictCount > 0) {
                                Text(
                                    "⚠ $conflictCount 个课程时段冲突",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { (course, meeting) ->
        val relatedTasks = state.tasks.filter { it.courseId == course.id }
        val relatedEvents = state.events.filter { course.name in it.title || course.name in it.note }
        CourseDetailDialog(
            course = course,
            meeting = meeting,
            meetings = state.meetings.filter { it.courseId == course.id }.sortedWith(
                compareBy<CourseMeetingEntity> { it.dayOfWeek }.thenBy { it.startPeriod },
            ),
            tasks = relatedTasks.map {
                it.title to it.dueAtEpochMillis?.let(::courseDate).orEmpty()
            },
            events = relatedEvents.map { it.title to courseDate(it.startsAtEpochMillis) },
            reminderMinutes = state.settings.courseReminderMinutes,
            conflictCount = CourseConflictDetector.conflicts(meeting, state.meetings).size,
            onDismiss = { detail = null },
            onEdit = {
                newCourseDefaults = meeting.dayOfWeek to meeting.startPeriod
                editing = course to meeting
                detail = null
            },
            onAddMeeting = {
                newCourseDefaults = meeting.dayOfWeek to meeting.startPeriod
                editing = course to null
                detail = null
            },
            onDelete = {
                pendingMeetingDelete = meeting
                detail = null
            },
        )
    }

    editing?.let { (course, meeting) ->
        CourseEditorDialog(
            course = course,
            meeting = meeting,
            defaultDay = newCourseDefaults.first,
            defaultStartPeriod = newCourseDefaults.second,
            maxPeriod = state.settings.timetablePeriodCount,
            defaultEndWeek = maxWeek,
            onDismiss = { editing = null },
            onSave = { name, teacher, location, day, start, end, startWeek, endWeek, parity, color, note ->
                onSaveCourse(
                    course?.id,
                    meeting?.id,
                    name,
                    teacher,
                    location,
                    day,
                    start,
                    end,
                    startWeek,
                    endWeek,
                    parity,
                    color,
                    note,
                )
                editing = null
            },
        )
    }

    pendingMeetingDelete?.let { meeting ->
        AppConfirmDialog(
            title = "删除这个上课时段？",
            message = "对应提醒会被取消。如果这是该课程最后一个时段，课程也会删除；关联任务会保留为未分类任务。",
            onConfirm = {
                onDeleteMeeting(meeting)
                pendingMeetingDelete = null
            },
            onDismiss = { pendingMeetingDelete = null },
            confirmLabel = "删除",
            isDanger = true,
        )
    }

    if (showImportCenter) {
        TimetableImportDialog(
            onDismiss = { showImportCenter = false },
            onPickPdf = {
                showImportCenter = false
                onPickTimetablePdf()
            },
            onPickHtml = {
                showImportCenter = false
                onPickTimetableHtml()
            },
            onPickExcel = {
                showImportCenter = false
                onPickTimetableExcel()
            },
            onOpenCode = {
                showCodeDialog = true
                showImportCenter = false
            },
        )
    }
    if (showCodeDialog) {
        TimetableCodeDialog(
            code = timetableCode,
            onCodeChange = { timetableCode = it },
            onDismiss = { showCodeDialog = false },
            onCopyCode = {
                val value = TimetableCodeCodec.encode(state.courses, state.meetings)
                timetableCode = value
                clipboard.setText(AnnotatedString(value))
            },
            onShareCode = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, timetableCode)
                context.startActivity(Intent.createChooser(send, "分享课表口令"))
            },
            onPasteCode = {
                timetableCode = clipboard.getText()?.text.orEmpty()
            },
            onImportCode = {
                onImportTimetableCode(timetableCode)
                showCodeDialog = false
            },
        )
    }
    if (showTimeSheet) {
        TimeSheet(
            selectedWeek = selectedWeek,
            currentWeek = currentWeek.coerceIn(1, maxWeek),
            maxWeek = maxWeek,
            onWeekChange = { selectedWeek = it },
            onEditCurrentWeek = {
                showCurrentWeekEditor = true
                showTimeSheet = false
            },
            onPreviousWeek = { selectedWeek = (selectedWeek - 1).coerceAtLeast(1) },
            onNextWeek = { selectedWeek = (selectedWeek + 1).coerceAtMost(maxWeek) },
            onCurrentWeek = { selectedWeek = currentWeek.coerceIn(1, maxWeek) },
            onPeriodTime = {
                showTimetableControls = true
                showTimeSheet = false
            },
            onDismiss = { showTimeSheet = false },
        )
    }
    if (showTimetableActions) {
        TimetableActionsSheet(
            semesterName = semester?.name ?: "默认课表",
            timetables = state.timetables,
            courseCount = state.courses.size,
            meetingCount = activeMeetings.size,
            onNewTimetable = {
                showCreateTimetableDialog = true
                showTimetableActions = false
            },
            onSwitchTimetable = onSwitchTimetable,
            onRenameTimetable = {
                renamingTimetable = it
                showTimetableActions = false
            },
            onWeekView = { showWeekGrid = true },
            onDayView = { showWeekGrid = false },
            onSettings = {
                showTimetableControls = true
                showTimetableActions = false
            },
            onBackgroundSettings = {
                showBackgroundSettings = true
                showTimetableActions = false
            },
            onAddedCourses = {
                showAddedCourses = true
                showTimetableActions = false
            },
            onFaq = {
                showFaq = true
                showTimetableActions = false
            },
            onDismiss = { showTimetableActions = false },
        )
    }
    if (showCreateTimetableDialog) {
        TimetableNameDialog(
            title = "新建课表",
            initialName = "",
            onDismiss = { showCreateTimetableDialog = false },
            onSave = {
                onCreateTimetable(it)
                showCreateTimetableDialog = false
            },
        )
    }
    renamingTimetable?.let { timetable ->
        TimetableNameDialog(
            title = "修改课表名称",
            initialName = timetable.name,
            onDismiss = { renamingTimetable = null },
            onSave = {
                onRenameTimetable(timetable.id, it)
                renamingTimetable = null
            },
        )
    }
    if (showAddedCourses) {
        AddedCoursesSheet(
            courses = state.courses,
            meetings = state.meetings,
            onDismiss = { showAddedCourses = false },
            onOpenCourse = { course ->
                val meeting = state.meetings.firstOrNull { it.courseId == course.id }
                if (meeting != null) {
                    detail = course to meeting
                    showAddedCourses = false
                } else {
                    editing = course to null
                    showAddedCourses = false
                }
            },
        )
    }
    if (showFaq) {
        TimetableFaqSheet(onDismiss = { showFaq = false })
    }
    if (showBackgroundSettings) {
        BackgroundSettingsSheet(
            wallpaperPath = state.settings.timetableWallpaperPath,
            wallpaperAlpha = state.settings.timetableWallpaperAlpha,
            showEmptyCellsAlways = state.settings.timetableShowEmptyCellsAlways,
            onPickWallpaper = onPickWallpaper,
            onClearWallpaper = onClearWallpaper,
            onSave = onSaveBackgroundOptions,
            onDismiss = { showBackgroundSettings = false },
        )
    }
    if (showCurrentWeekEditor) {
        CurrentWeekDialog(
            semesterName = semester?.name ?: "默认课表",
            selectedWeek = selectedWeek,
            maxWeek = maxWeek,
            onDismiss = { showCurrentWeekEditor = false },
            onSave = { name, week, weeks ->
                val start = LocalDate.now().minusWeeks((week - 1).toLong())
                onSaveSemester(name, start.toString(), weeks.toString())
                selectedWeek = week.coerceIn(1, weeks)
                showCurrentWeekEditor = false
            },
        )
    }
    if (showTimetableControls) {
        TimetableControlsDialog(
            settings = state.settings,
            periods = state.periods,
            onDismiss = { showTimetableControls = false },
            onSaveLayout = onSaveTimetableLayout,
            onSavePeriod = onSavePeriod,
        )
    }
}

@Composable
private fun CourseTopBar(
    selectedWeek: Int,
    currentWeek: Int,
    todayMeetingCount: Int,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onShare: () -> Unit,
    onOpenTimeSheet: () -> Unit,
    onOpenActions: () -> Unit,
) {
    val today = LocalDate.now()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenTimeSheet),
        ) {
            Text(
                "${today.year}/${today.monthValue}/${today.dayOfMonth}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                "第 $selectedWeek 周  ${if (selectedWeek == currentWeek) "本周" else "非本周"}  今天 $todayMeetingCount 节",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAdd, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = "添加课程")
            }
            IconButton(onClick = onImport, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.FileDownload, contentDescription = "导入课表")
            }
            IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.IosShare, contentDescription = "复制课表口令")
            }
            IconButton(onClick = onOpenActions, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TimeSheet(
    selectedWeek: Int,
    currentWeek: Int,
    maxWeek: Int,
    onWeekChange: (Int) -> Unit,
    onEditCurrentWeek: () -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onPeriodTime: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("时间与周数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onEditCurrentWeek) { Text("修改当前周") }
            }
            Slider(
                value = (selectedWeek - 1).toFloat(),
                onValueChange = { onWeekChange(it.toInt() + 1) },
                valueRange = 0f..(maxWeek - 1).coerceAtLeast(1).toFloat(),
                steps = (maxWeek - 2).coerceAtLeast(0),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("第 $selectedWeek 周", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(if (selectedWeek == currentWeek) "本周" else "当前为第 $currentWeek 周", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPreviousWeek, modifier = Modifier.weight(1f)) { Text("上一周") }
                OutlinedButton(onClick = onCurrentWeek, modifier = Modifier.weight(1f)) { Text("本周") }
                OutlinedButton(onClick = onNextWeek, modifier = Modifier.weight(1f)) { Text("下一周") }
            }
            HubActionRow(
                actions = listOf(
                    HubActionItem("上课时间", Icons.Outlined.AccessTime, onPeriodTime),
                    HubActionItem("修改周数", Icons.Outlined.CalendarMonth, onEditCurrentWeek),
                ),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TimetableActionsSheet(
    semesterName: String,
    timetables: List<TimetableEntity>,
    courseCount: Int,
    meetingCount: Int,
    onNewTimetable: () -> Unit,
    onSwitchTimetable: (Long) -> Unit,
    onRenameTimetable: (TimetableEntity) -> Unit,
    onWeekView: () -> Unit,
    onDayView: () -> Unit,
    onSettings: () -> Unit,
    onBackgroundSettings: () -> Unit,
    onAddedCourses: () -> Unit,
    onFaq: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("课表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row {
                    TextButton(onClick = onNewTimetable) { Text("新建课表") }
                    TextButton(onClick = onSettings) { Text("管理") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val books = timetables.ifEmpty { listOf(TimetableEntity(id = 1, name = semesterName, isCurrent = true)) }
                books.forEach { timetable ->
                    TimetableBookTile(
                        name = timetable.name,
                        selected = timetable.isCurrent,
                        caption = if (timetable.isCurrent) "$courseCount 门 · 本周 $meetingCount 节" else "点击切换",
                        onClick = {
                            onSwitchTimetable(timetable.id)
                            onDismiss()
                        },
                        onRename = { onRenameTimetable(timetable) },
                    )
                }
            }
            Text("功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HubActionRow(
                actions = listOf(
                    HubActionItem("周课表", Icons.Outlined.TableChart, onWeekView),
                    HubActionItem("按天查看", Icons.Outlined.CalendarMonth, onDayView),
                    HubActionItem("课表设置", Icons.Outlined.Settings, onSettings),
                    HubActionItem("背景设置", Icons.Outlined.Image, onBackgroundSettings),
                    HubActionItem("已添课程", Icons.Outlined.FormatListBulleted, onAddedCourses),
                    HubActionItem("常见问题", Icons.Outlined.HelpOutline, onFaq),
                ),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BackgroundSettingsSheet(
    wallpaperPath: String,
    wallpaperAlpha: Float,
    showEmptyCellsAlways: Boolean,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onSave: (Float, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var alpha by remember(wallpaperAlpha) { mutableStateOf(wallpaperAlpha.coerceIn(0f, 1f)) }
    var showCells by remember(showEmptyCellsAlways) { mutableStateOf(showEmptyCellsAlways) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("背景设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            PaperCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (wallpaperPath.isBlank()) "当前使用默认背景" else "已设置自定义壁纸", fontWeight = FontWeight.Medium)
                        Text(
                            "壁纸会放在课表格子后面，并加浅色遮罩保证课程文字可读。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onPickWallpaper, modifier = Modifier.weight(1f)) {
                        Text("上传壁纸")
                    }
                    OutlinedButton(onClick = onClearWallpaper, modifier = Modifier.weight(1f)) {
                        Text("清除壁纸")
                    }
                }
            }
            PaperCard {
                Text("壁纸不透明度 ${(alpha * 100).toInt()}%", fontWeight = FontWeight.Medium)
                Slider(
                    value = alpha,
                    onValueChange = { alpha = it },
                    valueRange = 0f..1f,
                )
            }
            PaperCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("空白方格显示", fontWeight = FontWeight.Medium)
                        Text(
                            if (showCells) "始终显示没有课的方格"
                            else "平时隐藏没有课的方格，点空白位置仍可添加课程",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(showCells, { showCells = it })
                }
            }
            Button(
                onClick = {
                    onSave(alpha, showCells)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存背景设置")
            }
        }
    }
}

@Composable
private fun TimetableBookTile(
    name: String,
    selected: Boolean,
    caption: String,
    onClick: () -> Unit,
    onRename: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    RoundedCornerShape(14.dp),
                )
                .border(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                    RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.TableChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            name,
            modifier = Modifier.clickable(onClick = onRename),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimetableNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AppFormSheet(
        title = title,
        onConfirm = { onSave(name) },
        onDismiss = onDismiss,
        confirmEnabled = name.isNotBlank(),
    ) {
        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = "课表名称",
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AddedCoursesSheet(
    courses: List<CourseEntity>,
    meetings: List<CourseMeetingEntity>,
    onDismiss: () -> Unit,
    onOpenCourse: (CourseEntity) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("已添课程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("点开课程可查看详情或继续编辑。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (courses.isEmpty()) {
                item {
                    PaperCard {
                        Text("暂无课程", fontWeight = FontWeight.Medium)
                        Text("可以导入课表，或点击空白格添加课程。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(courses, key = { "added-course-${it.id}" }) { course ->
                    val count = meetings.count { it.courseId == course.id }
                    PaperCard(modifier = Modifier.clickable { onOpenCourse(course) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(Color(course.colorArgb), CircleShape),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(course.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(
                                        course.defaultLocation.takeIf(String::isNotBlank),
                                        course.teacher.takeIf(String::isNotBlank),
                                        "$count 个上课时段",
                                    ).filterNotNull().joinToString(" · "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TimetableFaqSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("常见问题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            PaperCard {
                Text("为什么有些周没有课？", fontWeight = FontWeight.Medium)
                Text("课表会按当前教学周、单双周和课程周次过滤。点左上角日期可切换周数。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PaperCard {
                Text("为什么导入后要核对？", fontWeight = FontWeight.Medium)
                Text("PDF、HTML、Excel、AI 截图识别都可能有歧义。确认前不会真正写入课表。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PaperCard {
                Text("多课表怎么用？", fontWeight = FontWeight.Medium)
                Text("在三点面板中新建或切换课表。每个课表的课程、上课时段和导入草稿彼此隔离。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PaperCard {
                Text("课程卡片显示哪些信息？", fontWeight = FontWeight.Medium)
                Text("课程名优先显示，其次是楼栋和教室；点击课程查看完整教师、周次、单双周和提醒，长按可以快速编辑。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PaperCard {
                Text("为什么同一门课会出现多次？", fontWeight = FontWeight.Medium)
                Text("一门课可以有多个上课时段，例如不同星期、单双周或实验课。删除其中一个时段不会误删其他时段。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PaperCard {
                Text("教室为什么没有显示？", fontWeight = FontWeight.Medium)
                Text("部分来源只提供课程名和时间。可以在核对页或课程详情中补充楼栋与教室，之后会优先显示在课程卡片和今日页。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PaperCard {
                Text("提醒什么时候触发？", fontWeight = FontWeight.Medium)
                Text("课程提醒按“我的 → 提醒诊断”中的设置提前触发；单双周和当前教学周不匹配时，课程不会出现在当天。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PaperCard {
                Text("课表口令安全吗？", fontWeight = FontWeight.Medium)
                Text("口令只包含你选择分享的课程表字段。它可以复制给同学导入，也可以随时在本地重新生成；不要把包含隐私的备注发给陌生人。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class HubActionItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun HubActionRow(actions: List<HubActionItem>) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        actions.forEach { action ->
            HubAction(action.label, action.icon, action.onClick)
        }
    }
}

@Composable
private fun HubAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CurrentWeekDialog(
    semesterName: String,
    selectedWeek: Int,
    maxWeek: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int) -> Unit,
) {
    var name by remember(semesterName) { mutableStateOf(semesterName) }
    var week by remember(selectedWeek) { mutableStateOf(selectedWeek.toString()) }
    var weeks by remember(maxWeek) { mutableStateOf(maxWeek.toString()) }
    val weekValue = week.toIntOrNull()?.coerceIn(1, weeks.toIntOrNull() ?: maxWeek)
    val weeksValue = weeks.toIntOrNull()?.coerceIn(1, 30)
    AppFormSheet(
        title = "修改当前周",
        onConfirm = { onSave(name.ifBlank { "默认课表" }, weekValue ?: 1, weeksValue ?: maxWeek) },
        onDismiss = onDismiss,
        confirmEnabled = weekValue != null && weeksValue != null,
    ) {
        AppTextField(name, { name = it }, label = "课表名称")
        AppTextField(
            week,
            { week = it.filter(Char::isDigit).take(2) },
            label = "现在是第几周",
            keyboardType = KeyboardType.Number,
        )
        AppTextField(
            weeks,
            { weeks = it.filter(Char::isDigit).take(2) },
            label = "总周数",
            keyboardType = KeyboardType.Number,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TimetableImportDialog(
    onDismiss: () -> Unit,
    onPickPdf: () -> Unit,
    onPickHtml: () -> Unit,
    onPickExcel: () -> Unit,
    onOpenCode: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("导入课表", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "可从教务系统 HTML、PDF、Excel 或课表口令导入。所有结果都会先进入核对页，确认后才写入当前课表。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "HTML 与 Excel 通常字段更完整；PDF 会尽量提取课程、楼宇、教室和周次，扫描版或排版复杂的文件需要逐条核对。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPickHtml, modifier = Modifier.weight(1f)) { Text("教务 HTML") }
                OutlinedButton(onClick = onPickPdf, modifier = Modifier.weight(1f)) { Text("PDF") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPickExcel, modifier = Modifier.weight(1f)) { Text("Excel") }
                OutlinedButton(onClick = onOpenCode, modifier = Modifier.weight(1f)) { Text("课表口令") }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
        }
    }
}

@Composable
private fun TimetableCodeDialog(
    code: String,
    onCodeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onPasteCode: () -> Unit,
    onImportCode: () -> Unit,
) {
    AppFormSheet(
        title = "课表口令",
        onConfirm = onImportCode,
        onDismiss = onDismiss,
        confirmLabel = "解析导入",
        confirmEnabled = code.isNotBlank(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCopyCode, modifier = Modifier.weight(1f)) { Text("复制当前") }
            OutlinedButton(onClick = onPasteCode, modifier = Modifier.weight(1f)) { Text("粘贴口令") }
        }
        OutlinedButton(onClick = onShareCode, modifier = Modifier.fillMaxWidth(), enabled = code.isNotBlank()) {
            Icon(Icons.Outlined.IosShare, contentDescription = null)
            Text(" 分享课表口令")
        }
        AppTextField(
            value = code,
            onValueChange = onCodeChange,
            label = "口令内容",
            singleLine = false,
            minLines = 4,
            maxLines = 6,
        )
    }
}

@Composable
private fun TimetableControlsDialog(
    settings: AppSettings,
    periods: List<PeriodTemplateEntity>,
    onDismiss: () -> Unit,
    onSaveLayout: (String, String, String) -> Unit,
    onSavePeriod: (Int, String, String) -> Unit,
) {
    var columnWidth by remember(settings.timetableColumnWidthDp) { mutableStateOf(settings.timetableColumnWidthDp.toString()) }
    var rowHeight by remember(settings.timetableRowHeightDp) { mutableStateOf(settings.timetableRowHeightDp.toString()) }
    var periodCount by remember(settings.timetablePeriodCount) { mutableStateOf(settings.timetablePeriodCount.toString()) }
    var editingPeriod by remember { mutableStateOf<PeriodTemplateEntity?>(null) }
    AppFormSheet(
        title = "课表设置",
        subtitle = "先选显示密度；需要精调时再改列宽、行高和节次数。",
        onConfirm = {
            onSaveLayout(columnWidth, rowHeight, periodCount)
            onDismiss()
        },
        onDismiss = onDismiss,
        confirmLabel = "保存课表设置",
    ) {
        Text("显示密度", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = columnWidth.toIntOrNull() == 46 && rowHeight.toIntOrNull() == 74,
                onClick = {
                    columnWidth = "46"
                    rowHeight = "74"
                },
                label = { Text("紧凑") },
            )
            FilterChip(
                selected = columnWidth.toIntOrNull() == 64 && rowHeight.toIntOrNull() == 78,
                onClick = {
                    columnWidth = "64"
                    rowHeight = "78"
                },
                label = { Text("标准") },
            )
            FilterChip(
                selected = columnWidth.toIntOrNull() == 76 && rowHeight.toIntOrNull() == 118,
                onClick = {
                    columnWidth = "76"
                    rowHeight = "118"
                },
                label = { Text("舒展") },
            )
        }
        Text("高级尺寸", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = columnWidth,
                onValueChange = { columnWidth = it.filter(Char::isDigit) },
                label = "列宽 dp",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = rowHeight,
                onValueChange = { rowHeight = it.filter(Char::isDigit) },
                label = "行高 dp",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        AppTextField(
            value = periodCount,
            onValueChange = { periodCount = it.filter(Char::isDigit) },
            label = "显示节次数（6-24）",
            keyboardType = KeyboardType.Number,
        )
        Text("节次时间", fontWeight = FontWeight.SemiBold)
        Text("点选任一节修改开始与结束时间；减少显示节数不会删除已有课程。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        periods.sortedBy { it.periodIndex }.take(periodCount.toIntOrNull()?.coerceIn(6, 24) ?: 16).forEach { period ->
            OutlinedButton(
                onClick = { editingPeriod = period },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("第 ${period.periodIndex} 节  ${formatMinutes(period.startMinutes)} - ${formatMinutes(period.endMinutes)}")
            }
        }
    }
    editingPeriod?.let { period ->
        PeriodTimeDialog(
            period = period,
            onDismiss = { editingPeriod = null },
            onSave = { start, end ->
                onSavePeriod(period.periodIndex, start, end)
                editingPeriod = null
            },
        )
    }
}

@Composable
private fun PeriodTimeDialog(
    period: PeriodTemplateEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var start by remember(period) { mutableStateOf(formatMinutes(period.startMinutes)) }
    var end by remember(period) { mutableStateOf(formatMinutes(period.endMinutes)) }
    AppFormSheet(
        title = "第 ${period.periodIndex} 节时间",
        onConfirm = { onSave(start, end) },
        onDismiss = onDismiss,
    ) {
        AppTextField(start, { start = it }, label = "开始，例如 08:00")
        AppTextField(end, { end = it }, label = "结束，例如 08:45")
    }
}

@Composable
private fun WeekSelector(
    selectedWeek: Int,
    currentWeek: Int,
    maxWeek: Int,
    onWeekChange: (Int) -> Unit,
) {
    PaperCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(
                enabled = selectedWeek > 1,
                onClick = { onWeekChange((selectedWeek - 1).coerceAtLeast(1)) },
            ) { Text("上一周") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "第 $selectedWeek 周",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (selectedWeek == currentWeek) {
                    Text("本周", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(onClick = { onWeekChange(currentWeek) }) { Text("回到本周") }
                }
            }
            OutlinedButton(
                enabled = selectedWeek < maxWeek,
                onClick = { onWeekChange((selectedWeek + 1).coerceAtMost(maxWeek)) },
            ) { Text("下一周") }
        }
        Slider(
            value = (selectedWeek - 1).toFloat(),
            onValueChange = { onWeekChange(it.toInt() + 1) },
            valueRange = 0f..(maxWeek - 1).coerceAtLeast(1).toFloat(),
            steps = (maxWeek - 2).coerceAtLeast(0),
            modifier = Modifier.height(30.dp),
        )
        Text(
            "第 1 周                        第 $maxWeek 周",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TimetableStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DaySelector(
    selectedDay: Int,
    onDayChange: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        weekdays.forEachIndexed { index, label ->
            FilterChip(
                selected = selectedDay == index + 1,
                onClick = { onDayChange(index + 1) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun WeeklyTimetableGrid(
    modifier: Modifier = Modifier,
    periods: List<PeriodTemplateEntity>,
    courses: List<CourseEntity>,
    meetings: List<CourseMeetingEntity>,
    weekStartDate: LocalDate,
    dayWidthDp: Int,
    rowHeightDp: Int,
    periodCount: Int,
    wallpaperPath: String,
    wallpaperAlpha: Float,
    showEmptyCellsAlways: Boolean,
    highlightToday: Boolean,
    todayDay: Int,
    nowMinutes: Int,
    onMeetingClick: (CourseMeetingEntity) -> Unit,
    onMeetingLongClick: (CourseMeetingEntity) -> Unit,
    onEmptyClick: (day: Int, period: Int) -> Unit,
    onNavigateAdjacent: (Int) -> Unit,
) {
    val savedPeriods = periods.associateBy { it.periodIndex }
    val visiblePeriodCount = maxOf(
        periodCount.coerceIn(6, 24),
        meetings.maxOfOrNull { maxOf(it.startPeriod, it.endPeriod) } ?: 0,
    ).coerceIn(6, 24)
    val periodRows = (1..visiblePeriodCount).map { index ->
        savedPeriods[index] ?: PeriodTemplateEntity(index, 0, 0)
    }
    val coursesById = courses.associateBy { it.id }
    // The period axis stays fixed while the seven-day grid scrolls horizontally.
    val rowHeight = rowHeightDp.coerceIn(44, 220).dp
    val dayWidth = dayWidthDp.coerceIn(38, 180).dp
    val axisWidth = 34.dp
    val headerHeight = 30.dp
    val compactColumns = dayWidthDp <= 46
    val weekDates = (0..6).map { weekStartDate.plusDays(it.toLong()) }
    val gridScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val wallpaper = remember(wallpaperPath) {
        wallpaperPath
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        ),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll)
                .padding(start = 0.dp, top = 5.dp, end = 0.dp, bottom = 0.dp),
        ) {
            Column(modifier = Modifier.width(axisWidth)) {
                Box(
                    modifier = Modifier.height(headerHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${weekStartDate.monthValue}月",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
                periodRows.forEach { period ->
                    val hasTime = period.startMinutes > 0 && period.endMinutes > period.startMinutes
                    Box(
                        modifier = Modifier
                            .height(rowHeight)
                            .background(
                                if (highlightToday && hasTime && nowMinutes in period.startMinutes..period.endMinutes) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                } else {
                                    Color.Transparent
                                },
                                RoundedCornerShape(6.dp),
                            ),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                period.periodIndex.toString(),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (hasTime) {
                                Text(
                                    formatAxisMinutes(period.startMinutes),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                Text(
                                    formatAxisMinutes(period.endMinutes),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(gridScroll),
            ) {
                Row {
                    weekdays.forEachIndexed { index, day ->
                        Box(
                            modifier = Modifier
                                .width(dayWidth)
                                .height(headerHeight)
                                .background(
                                    if (highlightToday && day == weekdays[todayDay - 1]) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                    RoundedCornerShape(6.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    day.removePrefix("周"),
                                    fontWeight = FontWeight.SemiBold,
                                    style = if (compactColumns) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    weekDates[index].dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .width(dayWidth * 7)
                        .height(rowHeight * periodRows.size),
                ) {
                    if (wallpaper != null) {
                        ComposeImage(
                            bitmap = wallpaper,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                            alpha = wallpaperAlpha.coerceIn(0f, 1f),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.34f * (1f - wallpaperAlpha.coerceIn(0f, 1f)))),
                        )
                    }
                    periodRows.forEachIndexed { rowIndex, period ->
                        (1..7).forEach { day ->
                            Box(
                                modifier = Modifier
                                    .offset(x = dayWidth * (day - 1), y = rowHeight * rowIndex)
                                    .width(dayWidth)
                                    .height(rowHeight)
                                    .padding(1.dp)
                                    .background(
                                        if (!showEmptyCellsAlways) {
                                            Color.Transparent
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
                                        },
                                        RoundedCornerShape(6.dp),
                                    )
                                    .clickable { onEmptyClick(day, period.periodIndex) },
                            )
                        }
                    }
                    meetings
                        .sortedWith(compareBy<CourseMeetingEntity> { it.dayOfWeek }.thenBy { it.startPeriod })
                        .forEach { meeting ->
                            val startIndex = periodRows.indexOfFirst { it.periodIndex == meeting.startPeriod }
                            val endIndex = periodRows.indexOfLast { it.periodIndex == meeting.endPeriod }
                            val course = coursesById[meeting.courseId]
                            if (startIndex >= 0 && endIndex >= startIndex) {
                                val span = endIndex - startIndex + 1
                                val blockHeight = rowHeight * span - 4.dp
                                val compactBlock = dayWidthDp <= 46
                                val titleLines = when {
                                    blockHeight >= 190.dp -> if (compactBlock) 5 else 6
                                    blockHeight >= 118.dp -> if (compactBlock) 4 else 5
                                    else -> if (compactBlock) 2 else 3
                                }
                                val infoLines = when {
                                    blockHeight >= 190.dp -> 4
                                    blockHeight >= 118.dp -> 3
                                    else -> 1
                                }
                                val blockPadding = if (compactBlock) 4.dp else 6.dp
                                // 准确的冲突检测：同一天 + 节次重叠 + 周次范围重叠（含单双周）
                                val conflict = meetings.any { other ->
                                    other.id != meeting.id &&
                                        CourseConflictDetector.overlaps(meeting, other)
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = dayWidth * (meeting.dayOfWeek - 1) + 2.dp,
                                            y = rowHeight * startIndex + 2.dp,
                                        )
                                        .width(dayWidth - 4.dp)
                                        .height(blockHeight)
                                        .background(
                                            if (conflict) MaterialTheme.colorScheme.errorContainer
                                            else Color(course?.colorArgb ?: 0xFF52796F),
                                            RoundedCornerShape(6.dp),
                                        )
                                        .border(
                                            width = if (conflict) 2.dp else 0.dp,
                                            color = if (conflict) MaterialTheme.colorScheme.error else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                        .combinedClickable(
                                            onClick = { onMeetingClick(meeting) },
                                            onLongClick = { onMeetingLongClick(meeting) },
                                        )
                                        .padding(blockPadding),
                                ) {
                                    Column {
                                        if (conflict && !compactBlock) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Outlined.WarningAmber,
                                                    contentDescription = "时间冲突",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    "时间冲突",
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                        Text(
                                            course?.name ?: "未命名课程",
                                            color = if (conflict) MaterialTheme.colorScheme.onErrorContainer else Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            style = if (compactBlock) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                            maxLines = titleLines,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val location = meeting.location.ifBlank { course?.defaultLocation.orEmpty() }
                                        if (location.isNotBlank()) {
                                            Text(
                                                compactLocation(location),
                                                color = if (conflict) {
                                                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                                                } else {
                                                    Color.White.copy(alpha = 0.76f)
                                                },
                                                style = if (compactBlock) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                                maxLines = infoLines,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        course?.teacher?.takeIf { it.isNotBlank() && !compactBlock && blockHeight >= 118.dp }?.let { teacher ->
                                            Text(
                                                teacher,
                                                color = if (conflict) {
                                                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.68f)
                                                } else {
                                                    Color.White.copy(alpha = 0.68f)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
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

@Composable
private fun GridHeaderCell(label: String, width: Int) {
    Box(
        modifier = Modifier
            .width(width.dp)
            .height(38.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PeriodCell(period: PeriodTemplateEntity) {
    val hasTime = period.startMinutes > 0 || period.endMinutes > 0
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(76.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("第 ${period.periodIndex} 节", style = MaterialTheme.typography.labelMedium)
            if (hasTime) {
                Text(
                    formatMinutes(period.startMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimetableCell(
    meetings: List<CourseMeetingEntity>,
    coursesById: Map<Long, CourseEntity>,
    onMeetingClick: (CourseMeetingEntity) -> Unit,
    onEmptyClick: () -> Unit,
) {
    val primaryMeeting = meetings.firstOrNull()
    val course = primaryMeeting?.let { coursesById[it.courseId] }
    val conflict = meetings.size > 1
    val containerColor = when {
        conflict -> MaterialTheme.colorScheme.errorContainer
        course != null -> Color(course.colorArgb)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    val contentColor = when {
        conflict -> MaterialTheme.colorScheme.onErrorContainer
        course != null -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .width(106.dp)
            .height(76.dp)
            .background(containerColor, RoundedCornerShape(10.dp))
            .clickable {
                if (primaryMeeting == null) onEmptyClick() else onMeetingClick(primaryMeeting)
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (primaryMeeting == null) {
            Text("＋", color = contentColor.copy(alpha = 0.48f), style = MaterialTheme.typography.titleLarge)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    course?.name ?: "未命名课程",
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                val location = primaryMeeting.location.ifBlank { course?.defaultLocation.orEmpty() }
                if (location.isNotBlank()) {
                    Text(
                        location,
                        color = contentColor.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (conflict) {
                    Text(
                        "冲突 ${meetings.size}",
                        color = contentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CourseDetailDialog(
    course: CourseEntity,
    meeting: CourseMeetingEntity,
    meetings: List<CourseMeetingEntity>,
    tasks: List<Pair<String, String>>,
    events: List<Pair<String, String>>,
    reminderMinutes: Int,
    conflictCount: Int,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAddMeeting: () -> Unit,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val rawLocation = meeting.location.ifBlank { course.defaultLocation }.trim()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(course.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (rawLocation.isNotBlank()) {
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(rawLocation)) }) {
                    Text("复制地点")
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (course.teacher.isNotBlank()) Text("教师：${course.teacher}")
                Text("教室：${meeting.location.ifBlank { course.defaultLocation }.ifBlank { "未填写" }}")
                Text(
                    "${weekdays.getOrElse(meeting.dayOfWeek - 1) { "星期${meeting.dayOfWeek}" }} · " +
                        "第 ${meeting.startPeriod}-${meeting.endPeriod} 节",
                )
                Text("第 ${meeting.startWeek}-${meeting.endWeek} 周${meeting.weekParity.label()}")
                Text("上课前 $reminderMinutes 分钟提醒", color = MaterialTheme.colorScheme.primary)
                Text("全部上课时段", fontWeight = FontWeight.SemiBold)
                meetings.forEach { item ->
                    Text(
                        "· ${weekdays.getOrElse(item.dayOfWeek - 1) { "星期${item.dayOfWeek}" }} " +
                            "第 ${item.startPeriod}-${item.endPeriod} 节 · " +
                            "第 ${item.startWeek}-${item.endWeek} 周${item.weekParity.label()}",
                    )
                }
                if (conflictCount > 0) {
                    Text(
                        "⚠ $conflictCount 个课程时段冲突",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (course.note.isNotBlank()) Text("备注：${course.note}")
                Text("关联作业", fontWeight = FontWeight.SemiBold)
                if (tasks.isEmpty()) Text("暂无", color = MaterialTheme.colorScheme.onSurfaceVariant)
                tasks.take(4).forEach { (title, date) ->
                    Text("· $title${date.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}")
                }
                Text("关联考试与事件", fontWeight = FontWeight.SemiBold)
                if (events.isEmpty()) Text("暂无", color = MaterialTheme.colorScheme.onSurfaceVariant)
                events.take(4).forEach { (title, date) -> Text("· $title · $date") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAddMeeting, modifier = Modifier.weight(1f)) { Text("添加时段") }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            }
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("修改课程") }
        }
    }
}

@Composable
private fun CourseEditorDialog(
    course: CourseEntity?,
    meeting: CourseMeetingEntity?,
    defaultDay: Int,
    defaultStartPeriod: Int,
    maxPeriod: Int,
    defaultEndWeek: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Int, Int, Int, Int, WeekParity, Long, String) -> Unit,
) {
    var name by remember(course) { mutableStateOf(course?.name.orEmpty()) }
    var teacher by remember(course) { mutableStateOf(course?.teacher.orEmpty()) }
    var location by remember(meeting, course) {
        mutableStateOf(meeting?.location ?: course?.defaultLocation.orEmpty())
    }
    var day by remember(meeting, defaultDay) {
        mutableStateOf((meeting?.dayOfWeek ?: defaultDay).toString())
    }
    var start by remember(meeting, defaultStartPeriod) {
        mutableStateOf((meeting?.startPeriod ?: defaultStartPeriod).toString())
    }
    var end by remember(meeting, defaultStartPeriod, maxPeriod) {
        mutableStateOf((meeting?.endPeriod ?: (defaultStartPeriod + 1).coerceAtMost(maxPeriod.coerceIn(1, 24))).toString())
    }
    var startWeek by remember(meeting) { mutableStateOf((meeting?.startWeek ?: 1).toString()) }
    var endWeek by remember(meeting) {
        mutableStateOf((meeting?.endWeek ?: defaultEndWeek).toString())
    }
    var parity by remember(meeting) { mutableStateOf(meeting?.weekParity ?: WeekParity.ALL) }
    var selectedColor by remember(course) { mutableStateOf(course?.colorArgb ?: courseColors.first()) }
    var note by remember(course) { mutableStateOf(course?.note.orEmpty()) }
    var showRgbPicker by remember { mutableStateOf(false) }
    val dayValue = day.toIntOrNull()
    val startValue = start.toIntOrNull()
    val endValue = end.toIntOrNull()
    val startWeekValue = startWeek.toIntOrNull()
    val endWeekValue = endWeek.toIntOrNull()
    val valid = name.isNotBlank() &&
        dayValue in 1..7 &&
        startValue != null && endValue != null && startValue in 1..24 && endValue in startValue..24 &&
        startWeekValue != null && endWeekValue != null &&
        startWeekValue in 1..defaultEndWeek && endWeekValue in startWeekValue..defaultEndWeek

    AppFormSheet(
        title = if (meeting == null) "添加课程" else "修改课程",
        onConfirm = {
            onSave(
                name.trim(),
                teacher.trim(),
                location.trim(),
                dayValue ?: 1,
                startValue ?: 1,
                endValue ?: startValue ?: 1,
                startWeekValue ?: 1,
                endWeekValue ?: defaultEndWeek,
                parity,
                selectedColor,
                note.trim(),
            )
        },
        onDismiss = onDismiss,
        confirmEnabled = valid,
    ) {
        AppTextField(name, { name = it }, label = "课程名称")
        AppTextField(teacher, { teacher = it }, label = "教师")
        AppTextField(location, { location = it }, label = "教室")
        NumericFields("星期 / 开始节 / 结束节", listOf(day, start, end)) { index, value ->
            when (index) {
                0 -> day = value
                1 -> start = value
                else -> end = value
            }
        }
        NumericFields("开始周 / 结束周", listOf(startWeek, endWeek)) { index, value ->
            if (index == 0) startWeek = value else endWeek = value
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WeekParity.entries.forEach { option ->
                FilterChip(
                    selected = parity == option,
                    onClick = { parity = option },
                    label = { Text(option.choiceLabel()) },
                )
            }
        }
        Text("课程颜色", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            courseColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(color), CircleShape)
                        .border(
                            width = if (selectedColor == color) 3.dp else 1.dp,
                            color = if (selectedColor == color) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .clickable { selectedColor = color },
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color(selectedColor), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { showRgbPicker = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Palette,
                    contentDescription = "自定义 RGB",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        AppTextField(
            value = note,
            onValueChange = { note = it },
            label = "课程备注",
            singleLine = false,
            minLines = 2,
        )
        if (!valid) {
            Text(
                "请检查星期（1-7）、节次（1-24）和教学周范围。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (showRgbPicker) {
        RgbColorSheet(
            initialColor = selectedColor,
            onDismiss = { showRgbPicker = false },
            onApply = {
                selectedColor = it
                showRgbPicker = false
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RgbColorSheet(
    initialColor: Long,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit,
) {
    var red by remember(initialColor) { mutableStateOf(((initialColor shr 16) and 0xFF).toString()) }
    var green by remember(initialColor) { mutableStateOf(((initialColor shr 8) and 0xFF).toString()) }
    var blue by remember(initialColor) { mutableStateOf((initialColor and 0xFF).toString()) }
    val initialHsv = remember(initialColor) {
        FloatArray(3).also {
            AndroidColor.colorToHSV(initialColor.toInt(), it)
        }
    }
    var hue by remember(initialColor) { mutableStateOf(initialHsv[0]) }
    var valueLevel by remember(initialColor) { mutableStateOf(initialHsv[2].coerceIn(0.18f, 1f)) }
    fun channel(value: String) = value.toIntOrNull()?.coerceIn(0, 255) ?: 0
    fun syncPaletteFromRgb() {
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(channel(red), channel(green), channel(blue), hsv)
        hue = hsv[0]
        valueLevel = hsv[2].coerceIn(0.18f, 1f)
    }
    fun applyPalettePosition(x: Float, y: Float, width: Float, height: Float) {
        hue = ((x / width.coerceAtLeast(1f)) * 360f).coerceIn(0f, 359f)
        valueLevel = (1f - y / height.coerceAtLeast(1f)).coerceIn(0.18f, 1f)
        val color = AndroidColor.HSVToColor(floatArrayOf(hue, 0.62f, valueLevel))
        red = AndroidColor.red(color).toString()
        green = AndroidColor.green(color).toString()
        blue = AndroidColor.blue(color).toString()
    }
    val preview = 0xFF000000 or
        (channel(red).toLong() shl 16) or
        (channel(green).toLong() shl 8) or
        channel(blue).toLong()
    val paletteColors = listOf(
        Color.Red,
        Color.Yellow,
        Color.Green,
        Color.Cyan,
        Color.Blue,
        Color.Magenta,
        Color.Red,
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("自定义课程颜色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(preview), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
                Text("RGB(${channel(red)}, ${channel(green)}, ${channel(blue)})", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .background(Brush.horizontalGradient(paletteColors), RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.46f),
                            ),
                        ),
                        RoundedCornerShape(18.dp),
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            applyPalettePosition(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            applyPalettePosition(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat())
                            change.consume()
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pointer = Offset(
                        x = size.width * (hue / 360f),
                        y = size.height * (1f - valueLevel),
                    )
                    drawCircle(Color.Black.copy(alpha = 0.55f), radius = 14f, center = pointer)
                    drawCircle(Color.White, radius = 10f, center = pointer)
                    drawCircle(Color(preview), radius = 7f, center = pointer)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(red, {
                    red = it.filter(Char::isDigit).take(3)
                    syncPaletteFromRgb()
                }, label = { Text("R") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(green, {
                    green = it.filter(Char::isDigit).take(3)
                    syncPaletteFromRgb()
                }, label = { Text("G") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(blue, {
                    blue = it.filter(Char::isDigit).take(3)
                    syncPaletteFromRgb()
                }, label = { Text("B") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Button(onClick = { onApply(preview) }, modifier = Modifier.fillMaxWidth()) {
                Text("使用这个颜色")
            }
        }
    }
}

@Composable
private fun NumericFields(
    label: String,
    values: List<String>,
    onChange: (Int, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEachIndexed { index, value ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { onChange(index, it.filter(Char::isDigit)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
}

private fun WeekParity.label(): String = when (this) {
    WeekParity.ALL -> ""
    WeekParity.ODD -> "（单周）"
    WeekParity.EVEN -> "（双周）"
}

private fun WeekParity.choiceLabel(): String = when (this) {
    WeekParity.ALL -> "每周"
    WeekParity.ODD -> "单周"
    WeekParity.EVEN -> "双周"
}

private fun formatMinutes(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

private val courseDateFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun courseDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(courseDateFormatter)

private fun formatAxisMinutes(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

private fun compactLocation(location: String): String =
    location
        .replace("教室", "")
        .replace("@", "")
        .trim()
        .ifBlank { location.trim() }

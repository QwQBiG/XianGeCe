package win.iqwqi.xiangece.ui

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import win.iqwqi.xiangece.core.ai.AiCampusEnhancer
import win.iqwqi.xiangece.core.backup.BackupManager
import win.iqwqi.xiangece.core.importing.InboxImporter
import win.iqwqi.xiangece.core.importing.PdfTimetableImporter
import win.iqwqi.xiangece.core.importing.SharedInput
import win.iqwqi.xiangece.core.importing.TabularTimetableImporter
import win.iqwqi.xiangece.core.importing.TimetableCodeCodec
import win.iqwqi.xiangece.core.reminder.ReminderScheduler
import win.iqwqi.xiangece.core.reminder.ReminderTargets
import win.iqwqi.xiangece.core.security.ApiKeyCipher
import win.iqwqi.xiangece.data.CampusRepository
import win.iqwqi.xiangece.data.local.CampusEventEntity
import win.iqwqi.xiangece.data.local.CourseEntity
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.CustomQuoteEntity
import win.iqwqi.xiangece.data.local.GradeRecordEntity
import win.iqwqi.xiangece.data.local.HabitCheckinEntity
import win.iqwqi.xiangece.data.local.HabitTemplateEntity
import win.iqwqi.xiangece.data.local.InboxItemEntity
import win.iqwqi.xiangece.data.local.PeriodTemplateEntity
import win.iqwqi.xiangece.data.local.ReminderEntity
import win.iqwqi.xiangece.data.local.SemesterEntity
import win.iqwqi.xiangece.data.local.TaskEntity
import win.iqwqi.xiangece.data.local.TimetableEntity
import win.iqwqi.xiangece.data.settings.AppSettings
import win.iqwqi.xiangece.data.settings.AppSettingsStore
import win.iqwqi.xiangece.domain.model.DraftType
import win.iqwqi.xiangece.domain.model.HabitFrequency
import win.iqwqi.xiangece.domain.model.ParsedDraft
import win.iqwqi.xiangece.domain.model.TaskStatus
import win.iqwqi.xiangece.domain.model.WeekParity
import win.iqwqi.xiangece.domain.parser.TimetableCandidate
import win.iqwqi.xiangece.domain.parser.DraftConfirmationFields
import win.iqwqi.xiangece.domain.parser.DraftConfirmationValidator
import win.iqwqi.xiangece.domain.semester.CourseConflictDetector

data class DraftEditorState(
    val inboxId: Long?,
    val sourceText: String,
    val draft: ParsedDraft,
    val title: String = draft.title,
    val dateTimeText: String = draft.dateTimeEpochMillis?.let(::formatDateTime).orEmpty(),
    val courseName: String = draft.courseName.orEmpty(),
    val location: String = draft.location.orEmpty(),
    val teachingWeekText: String = draft.teachingWeek?.toString().orEmpty(),
    val dayOfWeekText: String = draft.dayOfWeek?.toString().orEmpty(),
    val startPeriodText: String = draft.startPeriod?.toString().orEmpty(),
    val endPeriodText: String = draft.endPeriod?.toString().orEmpty(),
    val ambiguitiesAcknowledged: Boolean = draft.ambiguities.isEmpty(),
)

data class AppUiState(
    val semester: SemesterEntity? = null,
    val periods: List<PeriodTemplateEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val meetings: List<CourseMeetingEntity> = emptyList(),
    val timetables: List<TimetableEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val events: List<CampusEventEntity> = emptyList(),
    val inbox: List<InboxItemEntity> = emptyList(),
    val grades: List<GradeRecordEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val habits: List<HabitTemplateEntity> = emptyList(),
    val habitCheckins: List<HabitCheckinEntity> = emptyList(),
    val customQuotes: List<CustomQuoteEntity> = emptyList(),
    val habitStats: HabitStats = HabitStats(),
    val settings: AppSettings = AppSettings(),
    val editor: DraftEditorState? = null,
    val timetableEditor: TimetableEditorState? = null,
    val isWorking: Boolean = false,
    val lastCelebratedEpochDay: Long = -1L,
    val celebrationDismissedEpochDay: Long = -1L,
)

/**
 * 厚积页面的统计聚合。基于 [HabitCheckinEntity] 与 [HabitTemplateEntity] 计算。
 * - [totalDays]：第一次打卡到今天跨越的总天数（哪怕中间断了也算累积厚度）。
 * - [currentStreak]：从今天（或昨天）往回数不中断的连续天数。
 * - [totalCheckins]：累计打卡次数。
 * - [monthCheckins]：本月已打卡次数。
 * - [todayCompletedHabitIds]：今天已经打卡的 habitId 集合。
 */
data class HabitStats(
    val totalDays: Int = 0,
    val currentStreak: Int = 0,
    val totalCheckins: Int = 0,
    val monthCheckins: Int = 0,
    val todayCompletedHabitIds: Set<Long> = emptySet(),
)

data class TimetableRowState(
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val day: String = "1",
    val startPeriod: String = "1",
    val endPeriod: String = "2",
    val startWeek: String = "1",
    val endWeek: String = "20",
    val parity: WeekParity = WeekParity.ALL,
)

data class TimetableEditorState(
    val inboxId: Long,
    val sourceText: String,
    val rows: List<TimetableRowState>,
    val sourceLabel: String = "课表导入",
)

@Serializable
private data class AiTimetableItem(
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int? = null,
    val startPeriod: Int? = null,
    val endPeriod: Int? = null,
    val startWeek: Int? = null,
    val endWeek: Int? = null,
    val weekParity: String = "ALL",
)

private data class CoreData(
    val semester: SemesterEntity?,
    val periods: List<PeriodTemplateEntity>,
    val courses: List<CourseEntity>,
    val meetings: List<CourseMeetingEntity>,
    val timetables: List<TimetableEntity>,
    val tasks: List<TaskEntity>,
)

private data class CourseBookData(
    val courses: List<CourseEntity>,
    val meetings: List<CourseMeetingEntity>,
    val timetables: List<TimetableEntity>,
)

private data class AuxData(
    val events: List<CampusEventEntity>,
    val inbox: List<InboxItemEntity>,
    val grades: List<GradeRecordEntity>,
    val settings: AppSettings,
    val reminders: List<ReminderEntity>,
    val habits: List<HabitTemplateEntity>,
    val habitCheckins: List<HabitCheckinEntity>,
    val customQuotes: List<CustomQuoteEntity>,
)

private data class HabitData(
    val templates: List<HabitTemplateEntity>,
    val checkins: List<HabitCheckinEntity>,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: CampusRepository,
    private val settingsStore: AppSettingsStore,
    private val importer: InboxImporter,
    private val aiEnhancer: AiCampusEnhancer,
    private val json: Json,
    private val keyCipher: ApiKeyCipher,
    private val backupManager: BackupManager,
    private val reminderScheduler: ReminderScheduler,
    private val tabularTimetableImporter: TabularTimetableImporter,
    private val pdfTimetableImporter: PdfTimetableImporter,
) : ViewModel() {
    private val editor = MutableStateFlow<DraftEditorState?>(null)
    private val timetableEditor = MutableStateFlow<TimetableEditorState?>(null)
    private val working = MutableStateFlow(false)
    private val lastCelebratedEpochDay = MutableStateFlow(-1L)
    private val celebrationDismissedEpochDay = MutableStateFlow(-1L)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** 待撤销动作：删除后弹出带「撤销」按钮的 Snackbar，点击执行 [onUndo] 恢复。 */
    val pendingUndo = MutableStateFlow<PendingUndo?>(null)

    class PendingUndo(val actionLabel: String, val onUndo: () -> Unit)

    private val courseBook = combine(
        repository.courses,
        repository.meetings,
        repository.timetables,
    ) { courses, meetings, timetables ->
        CourseBookData(courses, meetings, timetables)
    }

    private val core = combine(
        repository.semester,
        repository.periods,
        courseBook,
        repository.tasks,
    ) { semester, periods, courseBook, tasks ->
        CoreData(semester, periods, courseBook.courses, courseBook.meetings, courseBook.timetables, tasks)
    }

    private val aux = combine(
        repository.events,
        repository.inbox,
        repository.grades,
        settingsStore.settings,
        repository.reminders,
    ) { events, inbox, grades, settings, reminders ->
        AuxData(
            events = events,
            inbox = inbox,
            grades = grades,
            settings = settings,
            reminders = reminders,
            habits = emptyList(),
            habitCheckins = emptyList(),
            customQuotes = emptyList(),
        )
    }

    private val mergedAuxWithQuotes = combine(aux, repository.customQuotes) { base, customQuotes ->
        base.copy(customQuotes = customQuotes)
    }

    private val habitData = combine(
        repository.habitTemplates,
        repository.habitCheckins,
    ) { templates, checkins ->
        HabitData(templates, checkins)
    }

    private val mergedAux = combine(mergedAuxWithQuotes, habitData) { base, habits ->
        base.copy(habits = habits.templates, habitCheckins = habits.checkins)
    }

    private val baseUiState = combine(
        core,
        mergedAux,
        editor,
        timetableEditor,
        working,
    ) { coreData, auxData, currentEditor, currentTimetable, isWorking ->
        AppUiState(
            semester = coreData.semester,
            periods = coreData.periods,
            courses = coreData.courses,
            meetings = coreData.meetings,
            timetables = coreData.timetables,
            tasks = coreData.tasks,
            events = auxData.events,
            inbox = auxData.inbox,
            grades = auxData.grades,
            reminders = auxData.reminders,
            habits = auxData.habits,
            habitCheckins = auxData.habitCheckins,
            customQuotes = auxData.customQuotes,
            habitStats = computeHabitStats(auxData.habitCheckins),
            settings = auxData.settings,
            editor = currentEditor,
            timetableEditor = currentTimetable,
            isWorking = isWorking,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, AppUiState())

    val uiState: StateFlow<AppUiState> = combine(
        baseUiState,
        lastCelebratedEpochDay,
        celebrationDismissedEpochDay,
    ) { base, celebratedDay, dismissedDay ->
        base.copy(
            lastCelebratedEpochDay = celebratedDay,
            celebrationDismissedEpochDay = dismissedDay,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, AppUiState())

    init {
        viewModelScope.launch {
            // Remove alarms accumulated by old versions and keep only the next
            // reminders for each course meeting.
            val settings = settingsStore.settings.first()
            repository.ensurePeriodCount(settings.timetablePeriodCount)
            rescheduleAll(settings)
        }
        viewModelScope.launch {
            // 首次启动若无内置箴言，补齐预置 6 条
            repository.seedBuiltInQuotesIfEmpty()
        }
    }

    fun handleIntent(intent: Intent?) {
        importer.fromIntent(intent)?.let(::handleSharedInput)
    }

    fun importImage(uri: Uri) = handleSharedInput(SharedInput.Image(uri))

    fun importTimetableImage(uri: Uri) {
        viewModelScope.launch {
            withWorking {
                val file = importer.copyToPrivateStorage(uri)
                val id = repository.createImageInbox(file.absolutePath)
                val settings = settingsStore.settings.first()
                val endWeek = repository.currentSemester()?.weekCount ?: 20
                val maxPeriods = repository.allPeriods().maxOfOrNull { it.periodIndex } ?: 16
                aiEnhancer.recognizeTimetableImage(file, settings, maxPeriods, endWeek)
                    .onSuccess { content ->
                        val candidates = parseAiTimetable(content, endWeek, maxPeriods)
                        if (candidates.isEmpty()) {
                            repository.failOcr(id, "AI 没有返回可导入课程，请换 HTML/PDF/Excel 或重新截图")
                            messages.emit("AI 没有返回可导入课程")
                        } else {
                            repository.finishOcr(id, content)
                            openTimetableEditor(id, content, candidates, endWeek, "AI 截图识别")
                            messages.emit("AI 识别出 ${candidates.size} 个上课时段，请核对")
                        }
                    }
                    .onFailure {
                        repository.failOcr(id, it.message ?: "AI 截图识别失败")
                        messages.emit(it.message ?: "AI 截图识别失败")
                    }
            }
        }
    }

    fun importTimetableFile(uri: Uri, format: String) {
        viewModelScope.launch {
            withWorking {
                runCatching {
                    val file = importer.copyToPrivateStorage(uri)
                    val endWeek = repository.currentSemester()?.weekCount ?: 20
                    val maxPeriods = repository.allPeriods().maxOfOrNull { it.periodIndex } ?: 12
                    val (sourceText, candidates) = when (format) {
                        "pdf" -> pdfTimetableImporter.parse(file, endWeek, maxPeriods)
                        "html" -> {
                            val rows = tabularTimetableImporter.parseHtml(file, endWeek)
                            file.readText() to rows
                        }
                        "excel" -> {
                            val rows = tabularTimetableImporter.parseSpreadsheet(file, endWeek)
                            rows.joinToString("\n") {
                                "${it.name} 周${"一二三四五六日"[it.dayOfWeek - 1]} " +
                                    "第${it.startPeriod}-${it.endPeriod}节 第${it.startWeek}-${it.endWeek}周"
                            } to rows
                        }
                        else -> error("未知的课表导入格式")
                    }
                    require(candidates.isNotEmpty()) {
                        if (format == "pdf") "未从 PDF 中识别到可确认的课程；请选择教务系统导出的原始 PDF、HTML 或 Excel，扫描件请逐项确认"
                        else "文件中没有识别到可导入的课程表格"
                    }
                    val id = repository.createTextInbox(sourceText.take(100_000), "timetable_$format")
                    openTimetableEditor(
                        id,
                        sourceText,
                        candidates,
                        endWeek,
                        when (format) {
                            "pdf" -> "PDF 原始课表"
                            "html" -> "HTML 课表"
                            else -> "Excel 课表"
                        },
                    )
                    messages.emit("已解析 ${candidates.size} 个上课时段，请核对后导入")
                }.onFailure { error ->
                    messages.emit(error.message ?: "课表文件导入失败")
                }
            }
        }
    }

    fun importTimetableCode(code: String) {
        viewModelScope.launch {
            withWorking {
                runCatching {
                    val rows = TimetableCodeCodec.decode(code)
                    val endWeek = repository.currentSemester()?.weekCount ?: 20
                    val id = repository.createTextInbox(code, "timetable_code")
                    openTimetableEditor(id, code, rows, endWeek, "课表口令")
                    messages.emit("口令包含 ${rows.size} 个上课时段，请确认导入")
                }.onFailure { error ->
                    messages.emit(error.message ?: "课表口令解析失败")
                }
            }
        }
    }

    private fun openTimetableEditor(
        inboxId: Long,
        sourceText: String,
        candidates: List<TimetableCandidate>,
        endWeek: Int,
        sourceLabel: String,
    ) {
        timetableEditor.value = TimetableEditorState(
            inboxId = inboxId,
            sourceText = sourceText,
            sourceLabel = sourceLabel,
            rows = candidates.map {
                TimetableRowState(
                    name = it.name,
                    teacher = it.teacher,
                    location = it.location,
                    day = it.dayOfWeek.toString(),
                    startPeriod = it.startPeriod.toString(),
                    endPeriod = it.endPeriod.toString(),
                    startWeek = it.startWeek.toString(),
                    endWeek = it.endWeek.toString(),
                    parity = it.parity,
                )
            }.ifEmpty { listOf(TimetableRowState(endWeek = endWeek.toString())) },
        )
    }

    private fun parseAiTimetable(content: String, endWeek: Int, maxPeriods: Int): List<TimetableCandidate> {
        val cleaned = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return runCatching { json.decodeFromString<List<AiTimetableItem>>(cleaned) }
            .getOrDefault(emptyList())
            .mapNotNull { item ->
                val day = item.dayOfWeek?.coerceIn(1, 7) ?: return@mapNotNull null
                val start = item.startPeriod?.coerceIn(1, maxPeriods) ?: return@mapNotNull null
                val end = (item.endPeriod ?: start).coerceIn(start, maxPeriods)
                if (item.name.isBlank()) return@mapNotNull null
                TimetableCandidate(
                    name = item.name.trim(),
                    teacher = item.teacher.trim(),
                    location = item.location.trim(),
                    dayOfWeek = day,
                    startPeriod = start,
                    endPeriod = end,
                    startWeek = (item.startWeek ?: 1).coerceIn(1, endWeek),
                    endWeek = (item.endWeek ?: endWeek).coerceIn(1, endWeek),
                    parity = runCatching { WeekParity.valueOf(item.weekParity) }.getOrDefault(WeekParity.ALL),
                )
            }
            .distinctBy { listOf(it.name, it.dayOfWeek, it.startPeriod, it.endPeriod, it.location) }
    }

    fun parseToolText(text: String, useAi: Boolean = false) {
        if (text.isBlank()) {
            messages.tryEmit("请先粘贴通知文字")
            return
        }
        viewModelScope.launch {
            withWorking {
                val id = repository.createTextInbox(text, "tool_text")
                repository.inboxById(id)?.let { openInboxInternal(it) }
                if (!useAi) {
                    messages.emit("已生成待确认草稿")
                    return@withWorking
                }
                val settings = settingsStore.settings.first()
                if (!settings.aiEnabled) {
                    messages.emit("已生成本地草稿；AI 未启用")
                    return@withWorking
                }
                val semester = repository.currentSemester()
                val context = semester?.let {
                    "${it.name}，开始日期 ${LocalDate.ofEpochDay(it.startDateEpochDay)}，共${it.weekCount}周"
                } ?: "未设置学期"
                aiEnhancer.enhance(text.take(2400), context, settings)
                    .onSuccess { enhanced ->
                        val current = editor.value
                        if (current != null && current.inboxId == id) {
                            editor.value = current.copy(
                                draft = enhanced,
                                title = enhanced.title.ifBlank { current.title },
                                dateTimeText = enhanced.dateTimeEpochMillis?.let(::formatDateTime)
                                    ?: current.dateTimeText,
                                courseName = enhanced.courseName ?: current.courseName,
                                location = enhanced.location ?: current.location,
                                teachingWeekText = enhanced.teachingWeek?.toString()
                                    ?: current.teachingWeekText,
                                dayOfWeekText = enhanced.dayOfWeek?.toString()
                                    ?: current.dayOfWeekText,
                                startPeriodText = enhanced.startPeriod?.toString()
                                    ?: current.startPeriodText,
                                endPeriodText = enhanced.endPeriod?.toString()
                                    ?: current.endPeriodText,
                                ambiguitiesAcknowledged = enhanced.ambiguities.isEmpty(),
                            )
                        }
                        repository.saveReparsedDraft(id, text, enhanced)
                        messages.emit("AI 已生成结构化草稿，请核对后保存")
                    }
                    .onFailure { messages.emit(it.message ?: "AI 增强失败，已保留本地草稿") }
            }
        }
    }

    fun openInbox(item: InboxItemEntity) {
        viewModelScope.launch { openInboxInternal(item) }
    }

    fun closeEditor() {
        editor.value = null
    }

    fun closeTimetableEditor() {
        timetableEditor.value = null
    }

    fun updateTimetableRow(index: Int, row: TimetableRowState) {
        timetableEditor.value = timetableEditor.value?.let { state ->
            if (index !in state.rows.indices) state
            else state.copy(rows = state.rows.toMutableList().also { it[index] = row })
        }
    }

    fun addTimetableRow() {
        timetableEditor.value = timetableEditor.value?.let { state ->
            state.copy(
                rows = state.rows + TimetableRowState(
                    endWeek = (uiState.value.semester?.weekCount ?: 20).toString(),
                ),
            )
        }
    }

    fun removeTimetableRow(index: Int) {
        timetableEditor.value = timetableEditor.value?.let { state ->
            state.copy(rows = state.rows.filterIndexed { rowIndex, _ -> rowIndex != index })
        }
    }

    fun confirmTimetable() {
        val value = timetableEditor.value ?: return
        val validRows = value.rows.filter { it.name.isNotBlank() }
        if (validRows.isEmpty()) {
            messages.tryEmit("请至少填写一门课程")
            return
        }
        val maxWeek = uiState.value.semester?.weekCount ?: 20
        val invalidCount = validRows.count { row ->
            val day = row.day.toIntOrNull()
            val startPeriod = row.startPeriod.toIntOrNull()
            val endPeriod = row.endPeriod.toIntOrNull()
            val startWeek = row.startWeek.toIntOrNull()
            val endWeek = row.endWeek.toIntOrNull()
            day !in 1..7 ||
                startPeriod == null || endPeriod == null || startPeriod !in 1..24 || endPeriod !in startPeriod..24 ||
                startWeek == null || endWeek == null || startWeek !in 1..maxWeek || endWeek !in startWeek..maxWeek
        }
        if (invalidCount > 0) {
            messages.tryEmit("有 $invalidCount 门课程的星期、节次或周次不正确，请先核对")
            return
        }
        viewModelScope.launch {
            withWorking {
                val reminderMinutes = settingsStore.settings.first().courseReminderMinutes
                val existingCourses = uiState.value.courses.associateBy { it.name.trim().lowercase() }
                val existingMeetings = uiState.value.meetings
                val plannedKeys = mutableSetOf<String>()
                var importedCount = 0
                var skippedCount = 0
                validRows.forEach { row ->
                    val course = existingCourses[row.name.trim().lowercase()]
                    val day = requireNotNull(row.day.toIntOrNull())
                    val startPeriod = requireNotNull(row.startPeriod.toIntOrNull())
                    val endPeriod = requireNotNull(row.endPeriod.toIntOrNull())
                    val startWeek = requireNotNull(row.startWeek.toIntOrNull())
                    val endWeek = requireNotNull(row.endWeek.toIntOrNull())
                    val duplicate = course != null && existingMeetings.any { meeting ->
                        meeting.courseId == course.id &&
                            meeting.dayOfWeek == day &&
                            meeting.startPeriod == startPeriod && meeting.endPeriod == endPeriod &&
                            meeting.startWeek == startWeek && meeting.endWeek == endWeek &&
                            meeting.weekParity == row.parity
                    }
                    val key = listOf(
                        row.name.trim().lowercase(), day, startPeriod, endPeriod, startWeek, endWeek, row.parity,
                    ).joinToString("|")
                    if (duplicate || !plannedKeys.add(key)) {
                        skippedCount++
                        return@forEach
                    }
                    val (_, meetingId) = repository.saveCourse(
                        courseId = null,
                        meetingId = null,
                        name = row.name,
                        teacher = row.teacher,
                        location = row.location,
                        dayOfWeek = day,
                        startPeriod = startPeriod,
                        endPeriod = endPeriod,
                        startWeek = startWeek,
                        endWeek = endWeek,
                        parity = row.parity,
                    )
                    scheduleCourse(meetingId, reminderMinutes)
                    importedCount++
                }
                repository.confirmInbox(value.inboxId)
                timetableEditor.value = null
                messages.emit(
                    if (skippedCount == 0) "$importedCount 个上课时段已写入课表"
                    else "$importedCount 个上课时段已写入，跳过 $skippedCount 个重复时段",
                )
            }
        }
    }

    fun updateEditorTitle(value: String) {
        editor.value = editor.value?.copy(title = value)
    }

    fun updateEditorSourceText(value: String) {
        editor.value = editor.value?.copy(sourceText = value)
    }

    fun updateEditorDateTime(value: String) {
        editor.value = editor.value?.copy(dateTimeText = value)
    }

    fun updateEditorCourseName(value: String) {
        editor.value = editor.value?.copy(courseName = value)
    }

    fun updateEditorLocation(value: String) {
        editor.value = editor.value?.copy(location = value)
    }

    fun updateEditorTeachingWeek(value: String) {
        editor.value = editor.value?.copy(teachingWeekText = value.filter(Char::isDigit))
    }

    fun updateEditorDayOfWeek(value: String) {
        editor.value = editor.value?.copy(dayOfWeekText = value.filter(Char::isDigit))
    }

    fun updateEditorStartPeriod(value: String) {
        editor.value = editor.value?.copy(startPeriodText = value.filter(Char::isDigit))
    }

    fun updateEditorEndPeriod(value: String) {
        editor.value = editor.value?.copy(endPeriodText = value.filter(Char::isDigit))
    }

    fun acknowledgeEditorAmbiguities(value: Boolean) {
        editor.value = editor.value?.copy(ambiguitiesAcknowledged = value)
    }

    fun updateEditorType(value: DraftType) {
        editor.value = editor.value?.let { state ->
            state.copy(draft = state.draft.copy(type = value))
        }
    }

    fun reparseEditorSource() {
        val value = editor.value ?: return
        if (value.sourceText.isBlank()) {
            messages.tryEmit("识别原文不能为空")
            return
        }
        viewModelScope.launch {
            withWorking {
                val reparsed = repository.parseText(value.sourceText)
                value.inboxId?.let { repository.saveReparsedDraft(it, value.sourceText, reparsed) }
                editor.value = DraftEditorState(
                    inboxId = value.inboxId,
                    sourceText = value.sourceText,
                    draft = reparsed,
                )
                messages.emit("已按修改后的文字重新解析")
            }
        }
    }

    fun confirmEditor() {
        val value = editor.value ?: return
        viewModelScope.launch {
            withWorking {
                val semesterWeekCount = repository.currentSemester()?.weekCount ?: 20
                DraftConfirmationValidator.error(
                    fields = value.confirmationFields(),
                    semesterWeekCount = semesterWeekCount,
                )?.let { error ->
                    messages.emit(error)
                    return@withWorking
                }
                val epoch = value.dateTimeText.takeIf(String::isNotBlank)?.let(::parseDateTime)
                val editedDraft = value.draft.copy(
                    title = value.title.trim(),
                    description = value.sourceText.trim(),
                    courseName = value.courseName.trim().takeIf(String::isNotBlank),
                    location = value.location.trim().takeIf(String::isNotBlank),
                    teachingWeek = value.teachingWeekText.toIntOrNull(),
                    dayOfWeek = value.dayOfWeekText.toIntOrNull(),
                    startPeriod = value.startPeriodText.toIntOrNull(),
                    endPeriod = value.endPeriodText.toIntOrNull(),
                )
                val id = repository.confirmDraft(
                    inboxId = value.inboxId,
                    draft = editedDraft,
                    editedTitle = value.title,
                    editedEpochMillis = epoch,
                )
                val settings = settingsStore.settings.first()
                when (editedDraft.type) {
                    DraftType.TASK, DraftType.NOTE -> repository.taskById(id)?.let {
                        reminderScheduler.scheduleTask(
                            it,
                            settings.taskReminderHoursFirst,
                            settings.taskReminderHoursSecond,
                        )
                    }
                    DraftType.COURSE_MEETING -> scheduleCourse(id, settings.courseReminderMinutes)
                    DraftType.EVENT -> repository.eventById(id)?.let {
                        reminderScheduler.scheduleEvent(
                            it,
                            settings.taskReminderHoursFirst,
                            settings.taskReminderHoursSecond,
                        )
                    }
                }
                editor.value = null
                messages.emit("已收进弦歌册")
            }
        }
    }

    fun enhanceEditorWithAi() {
        val value = editor.value ?: return
        viewModelScope.launch {
            withWorking {
                val settings = settingsStore.settings.first()
                if (!settings.aiEnabled) {
                    messages.emit("请先在设置中启用 AI")
                    return@withWorking
                }
                val semester = repository.currentSemester()
                val context = semester?.let {
                    "${it.name}，开始日期${LocalDate.ofEpochDay(it.startDateEpochDay)}，共${it.weekCount}周"
                } ?: "未设置学期"
                aiEnhancer.enhance(value.sourceText, context, settings)
                    .onSuccess { enhanced ->
                        editor.value = value.copy(
                            draft = enhanced,
                            title = enhanced.title.ifBlank { value.title },
                            dateTimeText = enhanced.dateTimeEpochMillis?.let(::formatDateTime)
                                ?: value.dateTimeText,
                            courseName = enhanced.courseName ?: value.courseName,
                            location = enhanced.location ?: value.location,
                            teachingWeekText = enhanced.teachingWeek?.toString()
                                ?: value.teachingWeekText,
                            dayOfWeekText = enhanced.dayOfWeek?.toString()
                                ?: value.dayOfWeekText,
                            startPeriodText = enhanced.startPeriod?.toString()
                                ?: value.startPeriodText,
                            endPeriodText = enhanced.endPeriod?.toString()
                                ?: value.endPeriodText,
                            ambiguitiesAcknowledged = enhanced.ambiguities.isEmpty(),
                        )
                        messages.emit("AI 增强完成，请核对后保存")
                    }
                    .onFailure { messages.emit(it.message ?: "AI 增强失败") }
            }
        }
    }

    fun completeOnboarding(name: String, startDateText: String, weekCountText: String) {
        val date = runCatching { LocalDate.parse(startDateText) }.getOrNull()
        val weeks = weekCountText.toIntOrNull()
        if (date == null || weeks == null) {
            messages.tryEmit("请检查学期日期和周数")
            return
        }
        viewModelScope.launch {
            repository.saveSemester(name, date, weeks)
            settingsStore.updateGeneral(onboardingComplete = true)
            rescheduleAll(settingsStore.settings.first())
            messages.emit("学期已经准备好")
        }
    }

    fun saveSemester(name: String, startDateText: String, weekCountText: String) =
        completeOnboarding(name, startDateText, weekCountText)

    fun saveGeneralSettings(
        darkMode: Boolean,
        courseReminder: String,
        firstTaskReminder: String,
        secondTaskReminder: String,
    ) {
        viewModelScope.launch {
            settingsStore.updateGeneral(
                darkMode = darkMode,
                courseReminderMinutes = courseReminder.toIntOrNull() ?: 30,
                taskReminderHoursFirst = firstTaskReminder.toIntOrNull() ?: 24,
                taskReminderHoursSecond = secondTaskReminder.toIntOrNull() ?: 2,
            )
            rescheduleAll(settingsStore.settings.first())
            messages.emit("设置已保存")
        }
    }

    fun markNotificationPermissionAsked() {
        viewModelScope.launch {
            settingsStore.updateGeneral(notificationPermissionAsked = true)
        }
    }

    fun setThemeSeed(themeSeed: String) {
        viewModelScope.launch {
            settingsStore.updateThemeSeed(themeSeed)
        }
    }

    fun setDarkMode(darkMode: Boolean) {
        viewModelScope.launch {
            settingsStore.updateGeneral(darkMode = darkMode)
        }
    }

    fun registerAccount(email: String, password: String) {
        viewModelScope.launch {
            val current = settingsStore.settings.first()
            if (current.accountEmail.isNotEmpty()) {
                messages.emit("本机已注册账号，请先退出登录")
                return@launch
            }
            val trimmedEmail = email.trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                messages.emit("邮箱格式不正确")
                return@launch
            }
            if (password.length < 6) {
                messages.emit("密码至少 6 位")
                return@launch
            }
            settingsStore.saveAccount(trimmedEmail, keyCipher.encrypt(password))
            messages.emit("注册成功，已自动登录")
        }
    }

    fun loginAccount(password: String) {
        viewModelScope.launch {
            val current = settingsStore.settings.first()
            if (current.accountEmail.isEmpty()) {
                messages.emit("尚未注册账号")
                return@launch
            }
            val stored = keyCipher.decrypt(current.accountPasswordEncrypted)
            if (stored == password) {
                settingsStore.setAccountLoggedIn(true)
                messages.emit("登录成功")
            } else {
                messages.emit("密码错误")
            }
        }
    }

    fun logoutAccount() {
        viewModelScope.launch {
            settingsStore.setAccountLoggedIn(false)
            messages.emit("已退出登录")
        }
    }

    fun savePeriod(index: Int, start: String, end: String) {
        val startMinutes = parseClock(start)
        val endMinutes = parseClock(end)
        if (startMinutes == null || endMinutes == null || endMinutes <= startMinutes) {
            messages.tryEmit("节次时间格式应为 HH:mm，结束时间需晚于开始时间")
            return
        }
        viewModelScope.launch {
            repository.savePeriod(index, startMinutes, endMinutes)
            messages.emit("第 $index 节时间已更新")
        }
    }

    fun saveTimetableLayout(columnWidth: String, rowHeight: String, periodCount: String) {
        val width = columnWidth.toIntOrNull()
        val height = rowHeight.toIntOrNull()
        val count = periodCount.toIntOrNull()
        if (width == null || height == null || count == null) {
            messages.tryEmit("课表设置请输入数字")
            return
        }
        viewModelScope.launch {
            settingsStore.updateTimetableLayout(width, height, count)
            repository.ensurePeriodCount(count)
            messages.emit("课表尺寸与节次数已保存")
        }
    }

    fun saveTimetableWallpaper(uri: Uri) {
        viewModelScope.launch {
            withWorking {
                runCatching {
                    val file = importer.copyToPrivateStorage(uri)
                    settingsStore.updateTimetableWallpaper(file.absolutePath)
                }.onSuccess {
                    messages.emit("课表壁纸已更新")
                }.onFailure {
                    messages.emit(it.message ?: "壁纸保存失败")
                }
            }
        }
    }

    fun clearTimetableWallpaper() {
        viewModelScope.launch {
            settingsStore.updateTimetableWallpaper("")
            messages.emit("已恢复默认课表背景")
        }
    }

    fun saveTimetableBackgroundOptions(alpha: Float, showEmptyCellsAlways: Boolean) {
        viewModelScope.launch {
            settingsStore.updateTimetableBackgroundOptions(alpha, showEmptyCellsAlways)
            messages.emit("背景显示设置已保存")
        }
    }

    fun saveAiSettings(
        enabled: Boolean,
        provider: String,
        baseUrl: String,
        model: String,
        visionModel: String,
        authHeader: String,
        supportsVision: Boolean,
        apiKey: String,
    ) {
        viewModelScope.launch {
            val current = settingsStore.settings.first()
            settingsStore.updateAi(
                enabled = enabled,
                provider = provider,
                baseUrl = baseUrl,
                model = model,
                visionModel = visionModel,
                authHeader = authHeader,
                supportsVision = supportsVision,
                encryptedApiKey = if (apiKey.isBlank()) current.encryptedApiKey else keyCipher.encrypt(apiKey),
            )
            messages.emit("AI 设置已安全保存")
        }
    }

    fun testAiConnection(
        enabled: Boolean,
        provider: String,
        baseUrl: String,
        model: String,
        visionModel: String,
        authHeader: String,
        supportsVision: Boolean,
        apiKey: String,
    ) {
        viewModelScope.launch {
            withWorking {
                val current = settingsStore.settings.first()
                val temporary = current.copy(
                    aiEnabled = enabled,
                    aiProvider = provider,
                    aiBaseUrl = baseUrl.trim().trimEnd('/'),
                    aiModel = model.trim(),
                    aiVisionModel = visionModel.trim(),
                    aiAuthHeader = authHeader.trim(),
                    aiSupportsVision = supportsVision,
                    encryptedApiKey = if (apiKey.isBlank()) current.encryptedApiKey else keyCipher.encrypt(apiKey),
                )
                aiEnhancer.testConnection(temporary)
                    .onSuccess { messages.emit("连接成功") }
                    .onFailure { messages.emit(it.message ?: "连接失败") }
            }
        }
    }

    fun addCourse(
        name: String,
        teacher: String,
        location: String,
        day: Int,
        startPeriod: Int,
        endPeriod: Int,
        startWeek: Int,
        endWeek: Int,
        parity: WeekParity,
    ) {
        viewModelScope.launch {
            val (_, meetingId) = repository.addCourse(
                name, teacher, location, day, startPeriod, endPeriod, startWeek, endWeek, parity,
            )
            scheduleCourse(meetingId, settingsStore.settings.first().courseReminderMinutes)
            messages.emit("课程已加入课表")
        }
    }

    fun createTimetable(name: String = "新课表") {
        viewModelScope.launch {
            repository.createTimetable(name)
            messages.emit("已新建并切换到$name")
        }
    }

    fun switchTimetable(id: Long) {
        viewModelScope.launch {
            repository.switchTimetable(id)
            messages.emit("已切换课表")
        }
    }

    fun renameTimetable(id: Long, name: String) {
        if (name.isBlank()) {
            messages.tryEmit("课表名称不能为空")
            return
        }
        viewModelScope.launch {
            repository.renameTimetable(id, name)
            messages.emit("课表名称已更新")
        }
    }

    fun saveCourse(
        courseId: Long?,
        meetingId: Long?,
        name: String,
        teacher: String,
        location: String,
        day: Int,
        startPeriod: Int,
        endPeriod: Int,
        startWeek: Int,
        endWeek: Int,
        parity: WeekParity,
        colorArgb: Long,
        note: String,
    ) {
        viewModelScope.launch {
            val candidate = CourseMeetingEntity(
                id = meetingId ?: 0,
                courseId = courseId ?: -1,
                dayOfWeek = day,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                startWeek = startWeek,
                endWeek = endWeek,
                weekParity = parity,
                location = location,
            )
            val conflictCount = CourseConflictDetector
                .conflicts(candidate, repository.allMeetings())
                .size
            val (_, savedMeetingId) = repository.saveCourse(
                courseId,
                meetingId,
                name,
                teacher,
                location,
                day,
                startPeriod,
                endPeriod,
                startWeek,
                endWeek,
                parity,
                colorArgb,
                note,
            )
            scheduleCourse(savedMeetingId, settingsStore.settings.first().courseReminderMinutes)
            messages.emit(
                when {
                    conflictCount > 0 -> "已保存，但与 $conflictCount 个课程时段冲突"
                    meetingId == null -> "课程已加入课表"
                    else -> "课程修改已保存"
                },
            )
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            when (repository.toggleTask(task)) {
                TaskStatus.DONE -> reminderScheduler.cancelTarget(ReminderTargets.TASK, task.id)
                TaskStatus.TODO -> repository.taskById(task.id)?.let {
                    val settings = settingsStore.settings.first()
                    reminderScheduler.scheduleTask(
                        it,
                        settings.taskReminderHoursFirst,
                        settings.taskReminderHoursSecond,
                    )
                }
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            reminderScheduler.cancelTarget(ReminderTargets.TASK, task.id)
            repository.deleteTask(task.id)
            pendingUndo.value = PendingUndo("撤销") {
                viewModelScope.launch {
                    repository.restoreTask(task)
                    if (task.status == TaskStatus.TODO) {
                        val settings = settingsStore.settings.first()
                        reminderScheduler.scheduleTask(
                            task,
                            settings.taskReminderHoursFirst,
                            settings.taskReminderHoursSecond,
                        )
                    }
                    pendingUndo.value = null
                    messages.emit("已恢复任务")
                }
            }
            messages.emit("任务已删除")
        }
    }

    fun deleteEvent(event: CampusEventEntity) {
        viewModelScope.launch {
            reminderScheduler.cancelTarget(ReminderTargets.EVENT, event.id)
            repository.deleteEvent(event.id)
            pendingUndo.value = PendingUndo("撤销") {
                viewModelScope.launch {
                    repository.restoreEvent(event)
                    val settings = settingsStore.settings.first()
                    reminderScheduler.scheduleEvent(
                        event,
                        settings.taskReminderHoursFirst,
                        settings.taskReminderHoursSecond,
                    )
                    pendingUndo.value = null
                    messages.emit("已恢复事件")
                }
            }
            messages.emit("事件已删除")
        }
    }

    fun updateTask(task: TaskEntity, title: String, dateTimeText: String) {
        val dueAt = dateTimeText.takeIf(String::isNotBlank)?.let(::parseDateTime)
        if (dateTimeText.isNotBlank() && dueAt == null) {
            messages.tryEmit("时间格式应为 2026-09-01 18:00")
            return
        }
        viewModelScope.launch {
            repository.updateTask(task, title, dueAt)
            repository.taskById(task.id)?.let {
                val settings = settingsStore.settings.first()
                if (it.status == TaskStatus.TODO) {
                    reminderScheduler.scheduleTask(
                        it,
                        settings.taskReminderHoursFirst,
                        settings.taskReminderHoursSecond,
                    )
                } else {
                    reminderScheduler.cancelTarget(ReminderTargets.TASK, it.id)
                }
            }
            messages.emit("任务修改已保存")
        }
    }

    fun updateEvent(event: CampusEventEntity, title: String, dateTimeText: String) {
        val startsAt = parseDateTime(dateTimeText)
        if (startsAt == null) {
            messages.tryEmit("时间格式应为 2026-09-01 18:00")
            return
        }
        viewModelScope.launch {
            repository.updateEvent(event, title, startsAt)
            repository.eventById(event.id)?.let {
                val settings = settingsStore.settings.first()
                reminderScheduler.scheduleEvent(
                    it,
                    settings.taskReminderHoursFirst,
                    settings.taskReminderHoursSecond,
                )
            }
            messages.emit("事件修改已保存")
        }
    }

    fun createTask(title: String, dateTimeText: String, note: String) {
        if (title.isBlank()) {
            messages.tryEmit("请填写任务标题")
            return
        }
        val dueAt = dateTimeText.takeIf(String::isNotBlank)?.let(::parseDateTime)
        if (dateTimeText.isNotBlank() && dueAt == null) {
            messages.tryEmit("时间格式应为 2026-09-01 18:00")
            return
        }
        viewModelScope.launch {
            val id = repository.createTask(title, dueAt, note)
            repository.taskById(id)?.let {
                val settings = settingsStore.settings.first()
                reminderScheduler.scheduleTask(
                    it,
                    settings.taskReminderHoursFirst,
                    settings.taskReminderHoursSecond,
                )
            }
            messages.emit("任务已加入今日")
        }
    }

    fun createEvent(title: String, dateTimeText: String, location: String, note: String) {
        if (title.isBlank()) {
            messages.tryEmit("请填写事件标题")
            return
        }
        val startsAt = parseDateTime(dateTimeText)
        if (startsAt == null) {
            messages.tryEmit("事件时间格式应为 2026-09-01 18:00")
            return
        }
        viewModelScope.launch {
            val id = repository.createEvent(title, startsAt, location, note)
            repository.eventById(id)?.let {
                val settings = settingsStore.settings.first()
                reminderScheduler.scheduleEvent(
                    it,
                    settings.taskReminderHoursFirst,
                    settings.taskReminderHoursSecond,
                )
            }
            messages.emit("事件已加入今日")
        }
    }

    fun deleteInbox(item: InboxItemEntity) {
        viewModelScope.launch {
            importer.deletePrivateCopy(item.imagePath)
            repository.deleteInbox(item.id)
            if (editor.value?.inboxId == item.id) editor.value = null
            messages.emit("收件项已删除")
        }
    }

    fun retryInboxOcr(item: InboxItemEntity) {
        val imagePath = item.imagePath
        if (imagePath.isNullOrBlank()) {
            messages.tryEmit("这条收件项没有可重新识别的图片")
            return
        }
        viewModelScope.launch {
            withWorking {
                val file = File(imagePath)
                if (!file.isFile) {
                    repository.failOcr(item.id, "原始图片副本已不存在，请重新导入")
                    messages.emit("原始图片副本已不存在，请重新导入")
                    return@withWorking
                }
                repository.failOcr(item.id, "本地 OCR 已移除；请在设置中配置支持视觉的 AI 后使用 AI 识别")
                messages.emit("本地 OCR 已移除，请使用 AI 识别")
            }
        }
    }

    fun setReminderEnabled(reminder: ReminderEntity, enabled: Boolean) {
        viewModelScope.launch {
            val updated = reminderScheduler.setEnabled(reminder, enabled)
            messages.emit(
                when {
                    enabled && !updated -> "提醒时间已经过去，无法重新启用"
                    enabled -> "提醒已启用"
                    else -> "提醒已暂停"
                },
            )
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderScheduler.delete(reminder)
            messages.emit("提醒已删除")
        }
    }

    fun sendTestNotification() {
        reminderScheduler.sendTestNotification()
        viewModelScope.launch {
            messages.emit("测试通知将在 3 秒后发送，请检查系统通知栏")
        }
    }

    fun deleteCourseMeeting(meeting: CourseMeetingEntity) {
        viewModelScope.launch {
            reminderScheduler.cancelTarget(ReminderTargets.COURSE, meeting.id)
            val removedCourse = repository.deleteCourseMeeting(meeting)
            messages.emit(if (removedCourse) "课程及最后一个上课时段已删除" else "上课时段已删除")
        }
    }

    fun addGrade(courseName: String, credit: String, score: String) {
        val creditNumber = credit.toDoubleOrNull()
        val scoreNumber = score.toDoubleOrNull()
        if (creditNumber == null || scoreNumber == null) {
            messages.tryEmit("请填写正确的学分和成绩")
            return
        }
        viewModelScope.launch {
            repository.addGrade(courseName, creditNumber, scoreNumber, uiState.value.semester?.name.orEmpty())
            messages.emit("成绩已加入计算")
        }
    }

    fun deleteGrade(id: Long) {
        viewModelScope.launch { repository.deleteGrade(id) }
    }

    fun saveGradePreferences(scheme: String, customRules: String) {
        if (scheme == "自定义" && win.iqwqi.xiangece.domain.grade.GradeCalculator.parseCustomRules(customRules).isEmpty()) {
            messages.tryEmit("自定义规则格式无效")
            return
        }
        viewModelScope.launch {
            settingsStore.updateGradePreferences(scheme, customRules)
            messages.emit("绩点规则已保存")
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            withWorking {
                runCatching { backupManager.exportTo(uri) }
                    .onSuccess { messages.emit("备份已导出") }
                    .onFailure { messages.emit(it.message ?: "导出失败") }
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            withWorking {
                val oldReminders = repository.daoForBackup().allReminders()
                reminderScheduler.cancelAlarms(oldReminders)
                runCatching { backupManager.restoreFrom(uri) }
                    .onSuccess {
                        reminderScheduler.restore(repository.daoForBackup().upcomingReminders())
                        messages.emit("备份恢复完成，AI 密钥需重新填写")
                    }
                    .onFailure {
                        reminderScheduler.restore(oldReminders)
                        messages.emit(it.message ?: "恢复失败")
                    }
            }
        }
    }

    fun createHabit(title: String, description: String, colorArgb: Long, frequency: HabitFrequency) {
        viewModelScope.launch {
            repository.createHabitTemplate(title, description, colorArgb, frequency)
            messages.emit("已加入厚积清单")
        }
    }

    fun updateHabit(
        id: Long,
        title: String,
        description: String,
        colorArgb: Long,
        frequency: HabitFrequency,
    ) {
        viewModelScope.launch {
            repository.updateHabitTemplate(id, title, description, colorArgb, frequency)
            messages.emit("已更新")
        }
    }

    fun deleteHabit(id: Long) {
        viewModelScope.launch {
            repository.deleteHabitTemplate(id)
            messages.emit("已删除")
        }
    }

    /**
     * 切换某条长期事项今天的打卡。返回值仅做调试/日志用，UI 通过 Flow 自动刷新。
     */
    fun toggleHabitCheckin(habitId: Long) {
        viewModelScope.launch {
            val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
            val checkedIn = repository.toggleHabitCheckin(habitId, today)
            if (checkedIn) {
                val allDone = uiState.value.habits.isNotEmpty() &&
                    uiState.value.habits.all { it.id in uiState.value.habitStats.todayCompletedHabitIds }
                if (allDone) {
                    lastCelebratedEpochDay.value = today
                    messages.emit("今日厚积已成")
                }
            }
        }
    }

    fun dismissCelebration() {
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        celebrationDismissedEpochDay.value = today
    }

    fun addCustomQuote(text: String, author: String) {
        if (text.isBlank()) {
            messages.tryEmit("箴言内容不能为空")
            return
        }
        viewModelScope.launch {
            repository.addCustomQuote(text, author)
            messages.emit("箴言已加入")
        }
    }

    fun updateCustomQuote(id: Long, text: String, author: String, orderIndex: Int) {
        viewModelScope.launch {
            repository.updateCustomQuote(id, text, author, orderIndex)
            messages.emit("已更新箴言")
        }
    }

    fun deleteCustomQuote(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomQuote(id)
            messages.emit("已删除箴言")
        }
    }

    private fun handleSharedInput(input: SharedInput) {
        viewModelScope.launch {
            withWorking {
                when (input) {
                    is SharedInput.Text -> {
                        val id = repository.createTextInbox(input.value)
                        repository.inboxById(id)?.let { openInboxInternal(it) }
                        messages.emit("分享文字已进入收件箱")
                    }
                    is SharedInput.Image -> {
                        val file = importer.copyToPrivateStorage(input.uri)
                        val id = repository.createImageInbox(file.absolutePath)
                        repository.failOcr(id, "本地 OCR 已移除；请在设置中配置支持视觉的 AI 后识别图片")
                        messages.emit("图片已保存；请配置 AI 后使用云端识别")
                    }
                }
            }
        }
    }

    private suspend fun openInboxInternal(item: InboxItemEntity) {
        val draft = repository.draftFor(item)
        if (draft == null) {
            messages.emit(item.errorMessage ?: "这条内容还没有可编辑草稿")
            return
        }
        editor.value = DraftEditorState(
            inboxId = item.id,
            sourceText = item.ocrText.ifBlank { item.originalText },
            draft = draft,
        )
    }

    private suspend fun scheduleCourse(meetingId: Long, minutesBefore: Int) {
        val meeting = repository.meetingById(meetingId) ?: return
        val course = repository.courseById(meeting.courseId) ?: return
        val semester = repository.currentSemester() ?: return
        reminderScheduler.scheduleCourse(
            course,
            meeting,
            semester,
            repository.allPeriods(),
            minutesBefore,
        )
    }

    private suspend fun rescheduleAll(settings: AppSettings) {
        repository.allTasks()
            .filter { it.status == TaskStatus.TODO }
            .forEach {
                reminderScheduler.scheduleTask(
                    it,
                    settings.taskReminderHoursFirst,
                    settings.taskReminderHoursSecond,
                )
            }
        repository.allEvents()
            .filter { it.startsAtEpochMillis > System.currentTimeMillis() }
            .forEach {
                reminderScheduler.scheduleEvent(
                    it,
                    settings.taskReminderHoursFirst,
                    settings.taskReminderHoursSecond,
                )
            }
        repository.allMeetings().forEach {
            scheduleCourse(it.id, settings.courseReminderMinutes)
        }
    }

    private suspend fun withWorking(block: suspend () -> Unit) {
        working.value = true
        try {
            block()
        } catch (error: Throwable) {
            messages.emit(error.message ?: "操作失败")
        } finally {
            working.value = false
        }
    }
}

fun DraftEditorState.confirmationFields(): DraftConfirmationFields =
    DraftConfirmationFields(
        type = draft.type,
        title = title,
        dateTimeText = dateTimeText,
        courseName = courseName,
        teachingWeekText = teachingWeekText,
        dayOfWeekText = dayOfWeekText,
        startPeriodText = startPeriodText,
        endPeriodText = endPeriodText,
        hasAmbiguities = draft.ambiguities.isNotEmpty(),
        ambiguitiesAcknowledged = ambiguitiesAcknowledged,
    )

private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatDateTime(epochMillis: Long): String =
    LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(epochMillis),
        ZoneId.systemDefault(),
    ).format(dateTimeFormatter)

private fun parseDateTime(value: String): Long? = runCatching {
    LocalDateTime.parse(value.trim(), dateTimeFormatter)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}.getOrNull()

private fun parseClock(value: String): Int? {
    val parts = value.trim().split(':', '：')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

/**
 * 基于打卡记录计算厚积页面所需的统计。
 *
 * - [HabitStats.totalDays]：从最早一次打卡到今天的天数差 +1，体现长期累积厚度。
 * - [HabitStats.currentStreak]：从今天（或昨天，给用户一天宽限）往回数不中断的天数。
 * - [HabitStats.monthCheckins]：本月（按系统时区）打卡次数。
 * - [HabitStats.todayCompletedHabitIds]：今天已打卡的 habitId 集合，用于驱动清单 UI 的完成态。
 */
private fun computeHabitStats(checkins: List<HabitCheckinEntity>): HabitStats {
    if (checkins.isEmpty()) return HabitStats()
    val today = LocalDate.now(ZoneId.systemDefault())
    val todayEpochDay = today.toEpochDay()
    val distinctDays = checkins.map { it.checkinDateEpochDay }.toSet()
    val totalDays = ((distinctDays.max() - distinctDays.min()) + 1).toInt().coerceAtLeast(1)
    // 连续天数：从今天起往前数；若今天未打卡，允许从昨天起算（给一天宽限，避免晚上 11 点就归零）。
    var streakCursor = todayEpochDay
    if (streakCursor !in distinctDays) {
        streakCursor -= 1
        if (streakCursor !in distinctDays) {
            streakCursor = todayEpochDay
        }
    }
    var currentStreak = 0
    while (streakCursor in distinctDays) {
        currentStreak += 1
        streakCursor -= 1
    }
    val monthStart = today.withDayOfMonth(1).toEpochDay()
    val monthCheckins = checkins.count { it.checkinDateEpochDay >= monthStart }
    val todayCompletedHabitIds = checkins
        .filter { it.checkinDateEpochDay == todayEpochDay }
        .map { it.habitId }
        .toSet()
    return HabitStats(
        totalDays = totalDays,
        currentStreak = currentStreak,
        totalCheckins = checkins.size,
        monthCheckins = monthCheckins,
        todayCompletedHabitIds = todayCompletedHabitIds,
    )
}

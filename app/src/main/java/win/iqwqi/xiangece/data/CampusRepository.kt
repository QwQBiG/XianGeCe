package win.iqwqi.xiangece.data

import androidx.room.withTransaction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import win.iqwqi.xiangece.data.local.CampusDao
import win.iqwqi.xiangece.data.local.CampusEventEntity
import win.iqwqi.xiangece.data.local.CourseEntity
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.CustomQuoteEntity
import win.iqwqi.xiangece.data.local.GradeRecordEntity
import win.iqwqi.xiangece.data.local.GradeRuleEntity
import win.iqwqi.xiangece.data.local.HabitCheckinEntity
import win.iqwqi.xiangece.data.local.HabitTemplateEntity
import win.iqwqi.xiangece.data.local.InboxItemEntity
import win.iqwqi.xiangece.data.local.OcrSnapshotEntity
import win.iqwqi.xiangece.data.local.PeriodTemplateEntity
import win.iqwqi.xiangece.data.local.ReminderEntity
import win.iqwqi.xiangece.data.local.SemesterEntity
import win.iqwqi.xiangece.data.local.TaskEntity
import win.iqwqi.xiangece.data.local.TimetableEntity
import win.iqwqi.xiangece.data.local.XiangeceDatabase
import win.iqwqi.xiangece.domain.model.DraftType
import win.iqwqi.xiangece.domain.model.HabitFrequency
import win.iqwqi.xiangece.domain.model.InboxStatus
import win.iqwqi.xiangece.domain.model.ParsedDraft
import win.iqwqi.xiangece.domain.model.TaskStatus
import win.iqwqi.xiangece.domain.model.WeekParity
import win.iqwqi.xiangece.domain.parser.CampusTextParser
import win.iqwqi.xiangece.domain.parser.ParserContext

@Singleton
class CampusRepository @Inject constructor(
    private val dao: CampusDao,
    private val database: XiangeceDatabase,
    private val parser: CampusTextParser,
    private val json: Json,
) {
    val semester: Flow<SemesterEntity?> = dao.observeCurrentSemester()
    val timetables: Flow<List<TimetableEntity>> = dao.observeTimetables()
    val periods: Flow<List<PeriodTemplateEntity>> = dao.observePeriods()
    val courses: Flow<List<CourseEntity>> = dao.observeCourses()
    val meetings: Flow<List<CourseMeetingEntity>> = dao.observeMeetings()
    val tasks: Flow<List<TaskEntity>> = dao.observeTasks()
    val events: Flow<List<CampusEventEntity>> = dao.observeEvents()
    val inbox: Flow<List<InboxItemEntity>> = dao.observeInbox()
    val grades: Flow<List<GradeRecordEntity>> = dao.observeGrades()
    val reminders: Flow<List<ReminderEntity>> = dao.observeReminders()
    val habitTemplates: Flow<List<HabitTemplateEntity>> = dao.observeHabitTemplates()
    val habitCheckins: Flow<List<HabitCheckinEntity>> = dao.observeHabitCheckins()
    val customQuotes: Flow<List<CustomQuoteEntity>> = dao.observeCustomQuotes()

    suspend fun saveSemester(name: String, startDate: LocalDate, weekCount: Int) {
        database.withTransaction {
            val current = dao.currentSemester()
            dao.clearCurrentSemester()
            dao.upsertSemester(
                SemesterEntity(
                    id = current?.id ?: 0,
                    name = name.ifBlank { "本学期" },
                    startDateEpochDay = startDate.toEpochDay(),
                    weekCount = weekCount.coerceIn(1, 30),
                    isCurrent = true,
                ),
            )
            if (dao.allPeriods().isEmpty()) dao.upsertPeriods(defaultPeriods())
            ensureCurrentTimetable()
        }
    }

    suspend fun createTimetable(name: String): Long = database.withTransaction {
        dao.clearCurrentTimetable()
        val finalName = name.ifBlank {
            val next = dao.allTimetables().count { it.name.startsWith("新课表") } + 1
            if (next == 1) "新课表" else "新课表 $next"
        }
        dao.upsertTimetable(TimetableEntity(name = finalName, isCurrent = true))
    }

    suspend fun switchTimetable(id: Long) = database.withTransaction {
        dao.clearCurrentTimetable()
        dao.upsertTimetable((dao.allTimetables().firstOrNull { it.id == id } ?: return@withTransaction).copy(isCurrent = true))
    }

    suspend fun renameTimetable(id: Long, name: String) = database.withTransaction {
        val timetable = dao.allTimetables().firstOrNull { it.id == id } ?: return@withTransaction
        dao.upsertTimetable(timetable.copy(name = name.trim().ifBlank { timetable.name }))
    }

    suspend fun parseText(text: String): ParsedDraft {
        val semester = dao.currentSemester()
        return parser.parse(
            text,
            ParserContext(
                semesterStartEpochDay = semester?.startDateEpochDay,
                semesterWeekCount = semester?.weekCount,
            ),
        )
    }

    suspend fun createTextInbox(text: String, sourceType: String = "shared_text"): Long {
        val draft = parseText(text)
        return dao.upsertInbox(
            InboxItemEntity(
                timetableId = currentTimetableId(),
                sourceType = sourceType,
                originalText = text,
                ocrText = text,
                parsedDraftJson = json.encodeToString(draft),
                status = InboxStatus.PENDING,
            ),
        )
    }

    suspend fun createImageInbox(imagePath: String): Long =
        dao.upsertInbox(
            InboxItemEntity(
                timetableId = currentTimetableId(),
                sourceType = "image",
                imagePath = imagePath,
                status = InboxStatus.PROCESSING,
            ),
        )

    suspend fun finishOcr(inboxId: Long, text: String): ParsedDraft {
        val draft = parseText(text)
        dao.updateInboxProcessing(
            id = inboxId,
            status = InboxStatus.PENDING.name,
            draftJson = json.encodeToString(draft),
            ocrText = text,
            error = null,
        )
        dao.upsertOcrSnapshot(OcrSnapshotEntity(inboxItemId = inboxId, recognizedText = text))
        return draft
    }

    suspend fun failOcr(inboxId: Long, message: String) {
        dao.updateInboxProcessing(
            id = inboxId,
            status = InboxStatus.FAILED.name,
            draftJson = null,
            ocrText = "",
            error = message,
        )
    }

    suspend fun markOcrProcessing(inboxId: Long) {
        dao.updateInboxProcessing(
            id = inboxId,
            status = InboxStatus.PROCESSING.name,
            draftJson = null,
            ocrText = "",
            error = null,
        )
    }

    suspend fun saveReparsedDraft(inboxId: Long, sourceText: String, draft: ParsedDraft) {
        dao.updateInboxProcessing(
            id = inboxId,
            status = InboxStatus.PENDING.name,
            draftJson = json.encodeToString(draft),
            ocrText = sourceText,
            error = null,
        )
    }

    suspend fun draftFor(item: InboxItemEntity): ParsedDraft? =
        item.parsedDraftJson?.let { runCatching { json.decodeFromString<ParsedDraft>(it) }.getOrNull() }

    suspend fun inboxById(id: Long): InboxItemEntity? = dao.inboxById(id)

    suspend fun confirmInbox(id: Long) {
        dao.inboxById(id)?.let { dao.upsertInbox(it.copy(status = InboxStatus.CONFIRMED)) }
    }

    suspend fun confirmDraft(
        inboxId: Long?,
        draft: ParsedDraft,
        editedTitle: String = draft.title,
        editedEpochMillis: Long? = draft.dateTimeEpochMillis,
    ): Long {
        val id = when (draft.type) {
            DraftType.EVENT -> dao.upsertEvent(
                CampusEventEntity(
                    title = editedTitle.ifBlank { "校园事件" },
                    startsAtEpochMillis = editedEpochMillis ?: System.currentTimeMillis(),
                    location = draft.location.orEmpty(),
                    note = draft.description,
                    sourceInboxId = inboxId,
                ),
            )
            DraftType.COURSE_MEETING -> {
                val courseId = findOrCreateCourse(draft.courseName ?: editedTitle)
                dao.upsertMeeting(
                    CourseMeetingEntity(
                        timetableId = currentTimetableId(),
                        courseId = courseId,
                        dayOfWeek = draft.dayOfWeek ?: 1,
                        startPeriod = draft.startPeriod ?: 1,
                        endPeriod = draft.endPeriod ?: draft.startPeriod ?: 1,
                        startWeek = draft.teachingWeek ?: 1,
                        endWeek = draft.teachingWeek ?: dao.currentSemester()?.weekCount ?: 20,
                        weekParity = draft.weekParity,
                        location = draft.location.orEmpty(),
                    ),
                )
            }
            DraftType.NOTE, DraftType.TASK -> dao.upsertTask(
                TaskEntity(
                    title = editedTitle.ifBlank { "待办事项" },
                    courseId = draft.courseName?.let { findOrCreateCourse(it) },
                    dueAtEpochMillis = editedEpochMillis,
                    note = draft.description,
                    sourceInboxId = inboxId,
                ),
            )
        }
        if (inboxId != null) {
            val item = dao.inboxById(inboxId)
            if (item != null) dao.upsertInbox(item.copy(status = InboxStatus.CONFIRMED))
        }
        return id
    }

    suspend fun addCourse(
        name: String,
        teacher: String,
        location: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        startWeek: Int,
        endWeek: Int,
        parity: WeekParity,
    ): Pair<Long, Long> {
        val courseId = dao.upsertCourse(
            CourseEntity(
                timetableId = currentTimetableId(),
                name = name.ifBlank { "未命名课程" },
                teacher = teacher,
                defaultLocation = location,
                colorArgb = courseColor(name),
            ),
        )
        val meetingId = dao.upsertMeeting(
            CourseMeetingEntity(
                timetableId = currentTimetableId(),
                courseId = courseId,
                dayOfWeek = dayOfWeek.coerceIn(1, 7),
                startPeriod = startPeriod.coerceIn(1, 24),
                endPeriod = endPeriod.coerceAtLeast(startPeriod).coerceAtMost(24),
                startWeek = startWeek.coerceAtLeast(1),
                endWeek = endWeek.coerceAtLeast(startWeek).coerceAtMost(30),
                weekParity = parity,
                location = location,
            ),
        )
        return courseId to meetingId
    }

    suspend fun saveCourse(
        courseId: Long?,
        meetingId: Long?,
        name: String,
        teacher: String,
        location: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        startWeek: Int,
        endWeek: Int,
        parity: WeekParity,
        colorArgb: Long? = null,
        note: String? = null,
    ): Pair<Long, Long> {
        // Imports describe one course through several weekly meetings. Reuse a
        // same-name course instead of creating disconnected duplicates for each
        // weekday, so its teacher, notes, colour and detail page stay coherent.
        val existingCourse = courseId?.let { dao.courseById(it) }
            ?: dao.allCourses().firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
        val timetableId = existingCourse?.timetableId ?: currentTimetableId()
        val savedCourseId = dao.upsertCourse(
            CourseEntity(
                id = existingCourse?.id ?: 0,
                timetableId = timetableId,
                name = name.ifBlank { "未命名课程" },
                teacher = teacher.ifBlank { existingCourse?.teacher.orEmpty() },
                defaultLocation = location.ifBlank { existingCourse?.defaultLocation.orEmpty() },
                colorArgb = colorArgb ?: existingCourse?.colorArgb ?: courseColor(name),
                note = note ?: existingCourse?.note.orEmpty(),
            ),
        )
        val savedMeetingId = dao.upsertMeeting(
            CourseMeetingEntity(
                id = meetingId ?: 0,
                timetableId = timetableId,
                courseId = savedCourseId,
                dayOfWeek = dayOfWeek.coerceIn(1, 7),
                startPeriod = startPeriod.coerceIn(1, 24),
                endPeriod = endPeriod.coerceAtLeast(startPeriod).coerceAtMost(24),
                startWeek = startWeek.coerceAtLeast(1),
                endWeek = endWeek.coerceAtLeast(startWeek).coerceAtMost(30),
                weekParity = parity,
                location = location,
            ),
        )
        return savedCourseId to savedMeetingId
    }

    suspend fun addGrade(courseName: String, credit: Double, score: Double, term: String) {
        dao.upsertGrade(
            GradeRecordEntity(
                courseName = courseName.ifBlank { "未命名课程" },
                credit = credit.coerceAtLeast(0.0),
                score = score.coerceIn(0.0, 100.0),
                term = term,
            ),
        )
    }

    suspend fun deleteGrade(id: Long) = dao.deleteGrade(id)

    suspend fun taskById(id: Long): TaskEntity? = dao.taskById(id)
    suspend fun eventById(id: Long): CampusEventEntity? = dao.eventById(id)
    suspend fun courseById(id: Long): CourseEntity? = dao.courseById(id)
    suspend fun meetingById(id: Long): CourseMeetingEntity? = dao.meetingById(id)
    suspend fun currentSemester(): SemesterEntity? = dao.currentSemester()
    suspend fun allPeriods(): List<PeriodTemplateEntity> = dao.allPeriods()
    suspend fun allTasks(): List<TaskEntity> = dao.allTasks()
    suspend fun allEvents(): List<CampusEventEntity> = dao.allEvents()
    suspend fun allMeetings(): List<CourseMeetingEntity> = dao.allMeetings()

    suspend fun savePeriod(index: Int, startMinutes: Int, endMinutes: Int) {
        dao.upsertPeriods(
            listOf(
                PeriodTemplateEntity(
                    periodIndex = index,
                    startMinutes = startMinutes.coerceIn(0, 1439),
                    endMinutes = endMinutes.coerceIn(startMinutes.coerceIn(0, 1439), 1439),
                ),
            ),
        )
    }

    suspend fun ensurePeriodCount(count: Int) {
        val existing = dao.allPeriods().associateBy { it.periodIndex }
        val values = (1..count.coerceIn(6, 24)).map { index ->
            existing[index] ?: PeriodTemplateEntity(
                periodIndex = index,
                startMinutes = (8 * 60 + (index - 1) * 55).coerceAtMost(23 * 60),
                endMinutes = (8 * 60 + (index - 1) * 55 + 45).coerceAtMost(23 * 60 + 59),
            )
        }
        dao.upsertPeriods(values)
    }

    suspend fun toggleTask(task: TaskEntity): TaskStatus {
        val newStatus = if (task.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE
        dao.setTaskStatus(
            task.id,
            newStatus.name,
        )
        return newStatus
    }

    suspend fun deleteTask(id: Long) = dao.deleteTask(id)

    suspend fun createHabitTemplate(
        title: String,
        description: String,
        colorArgb: Long,
        frequency: HabitFrequency,
    ): Long {
        val nextOrder = (dao.allHabitTemplates().maxOfOrNull { it.orderIndex } ?: 0) + 1
        return dao.upsertHabitTemplate(
            HabitTemplateEntity(
                title = title.ifBlank { "未命名长期事项" },
                description = description,
                colorArgb = colorArgb,
                frequency = frequency,
                orderIndex = nextOrder,
            ),
        )
    }

    suspend fun updateHabitTemplate(
        id: Long,
        title: String,
        description: String,
        colorArgb: Long,
        frequency: HabitFrequency,
    ) {
        val current = dao.habitTemplateById(id) ?: return
        dao.upsertHabitTemplate(
            current.copy(
                title = title.ifBlank { current.title },
                description = description,
                colorArgb = colorArgb,
                frequency = frequency,
            ),
        )
    }

    suspend fun deleteHabitTemplate(id: Long) = database.withTransaction {
        dao.deleteHabitCheckinsForHabit(id)
        dao.deleteHabitTemplate(id)
    }

    /**
     * 切换某条长期事项在 [day]（epoch-day）的打卡状态：
     * 已打卡则撤销，未打卡则补打。返回 true 表示本次产生打卡。
     */
    suspend fun toggleHabitCheckin(habitId: Long, day: Long): Boolean = database.withTransaction {
        val existing = dao.habitCheckinOnDay(habitId, day)
        if (existing == null) {
            dao.upsertHabitCheckin(
                HabitCheckinEntity(
                    habitId = habitId,
                    checkinDateEpochDay = day,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            true
        } else {
            dao.deleteHabitCheckin(habitId, day)
            false
        }
    }

    suspend fun allHabitTemplates(): List<HabitTemplateEntity> = dao.allHabitTemplates()
    suspend fun allHabitCheckins(): List<HabitCheckinEntity> = dao.allHabitCheckins()

    suspend fun addCustomQuote(text: String, author: String): Long {
        val nextOrder = (dao.userQuotes().maxOfOrNull { it.orderIndex } ?: 0) + 1
        return dao.upsertCustomQuote(
            CustomQuoteEntity(
                text = text.trim(),
                author = author.trim(),
                isBuiltIn = false,
                orderIndex = nextOrder,
            ),
        )
    }

    suspend fun updateCustomQuote(
        id: Long,
        text: String,
        author: String,
        orderIndex: Int,
    ) {
        val current = dao.allCustomQuotes().firstOrNull { it.id == id } ?: return
        dao.upsertCustomQuote(
            current.copy(
                text = text.trim(),
                author = author.trim(),
                orderIndex = orderIndex,
            ),
        )
    }

    suspend fun deleteCustomQuote(id: Long) = dao.deleteCustomQuote(id)

    suspend fun allCustomQuotes(): List<CustomQuoteEntity> = dao.allCustomQuotes()

    suspend fun seedBuiltInQuotesIfEmpty() {
        if (dao.allCustomQuotes().any { it.isBuiltIn }) return
        val builtIns = listOf(
            "不积跬步，无以至千里；不积小流，无以成江海。" to "荀子",
            "锲而不舍，金石可镂。" to "荀子",
            "千里之行，始于足下。" to "老子",
            "天行健，君子以自强不息。" to "周易",
            "博观而约取，厚积而薄发。" to "苏轼",
            "业精于勤，荒于嬉。" to "韩愈",
        )
        dao.upsertCustomQuotes(
            builtIns.mapIndexed { i, (text, author) ->
                CustomQuoteEntity(text = text, author = author, isBuiltIn = true, orderIndex = i)
            },
        )
    }

    suspend fun deleteEvent(id: Long) = dao.deleteEvent(id)

    suspend fun updateTask(task: TaskEntity, title: String, dueAtEpochMillis: Long?) {
        dao.upsertTask(
            task.copy(
                title = title.ifBlank { task.title },
                dueAtEpochMillis = dueAtEpochMillis,
            ),
        )
    }

    suspend fun updateEvent(event: CampusEventEntity, title: String, startsAtEpochMillis: Long) {
        dao.upsertEvent(
            event.copy(
                title = title.ifBlank { event.title },
                startsAtEpochMillis = startsAtEpochMillis,
            ),
        )
    }

    suspend fun createTask(title: String, dueAtEpochMillis: Long?, note: String): Long =
        dao.upsertTask(
            TaskEntity(
                title = title.ifBlank { "待办事项" },
                dueAtEpochMillis = dueAtEpochMillis,
                note = note,
            ),
        )

    suspend fun createEvent(
        title: String,
        startsAtEpochMillis: Long,
        location: String,
        note: String,
    ): Long = dao.upsertEvent(
        CampusEventEntity(
            title = title.ifBlank { "校园事件" },
            startsAtEpochMillis = startsAtEpochMillis,
            location = location,
            note = note,
        ),
    )

    /** 恢复刚被删除的任务（保留原 id）。用于删除撤销。 */
    suspend fun restoreTask(task: TaskEntity): Long = dao.upsertTask(task)

    /** 恢复刚被删除的事件（保留原 id）。用于删除撤销。 */
    suspend fun restoreEvent(event: CampusEventEntity): Long = dao.upsertEvent(event)

    suspend fun deleteInbox(id: Long) {
        database.withTransaction {
            dao.deleteOcrSnapshotsForInbox(id)
            dao.deleteInbox(id)
        }
    }

    suspend fun deleteCourseMeeting(meeting: CourseMeetingEntity): Boolean =
        database.withTransaction {
            dao.deleteMeeting(meeting.id)
            if (dao.meetingCountForCourse(meeting.courseId) == 0) {
                dao.detachTasksFromCourse(meeting.courseId)
                dao.deleteCourse(meeting.courseId)
                true
            } else {
                false
            }
        }

    suspend fun seedDemo() {
        if (dao.courseCount() > 0) return
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        saveSemester("2026 秋季学期", monday.minusWeeks(2), 20)
        val timetableId = currentTimetableId()
        val math = dao.upsertCourse(
            CourseEntity(timetableId = timetableId, name = "高等数学", teacher = "陈老师", defaultLocation = "综A302", colorArgb = 0xFF56766A),
        )
        val english = dao.upsertCourse(
            CourseEntity(timetableId = timetableId, name = "大学英语", teacher = "林老师", defaultLocation = "文210", colorArgb = 0xFF8A6659),
        )
        dao.upsertMeetings(
            listOf(
                CourseMeetingEntity(timetableId = timetableId, courseId = math, dayOfWeek = 1, startPeriod = 1, endPeriod = 2, startWeek = 1, endWeek = 20, location = "综A302"),
                CourseMeetingEntity(timetableId = timetableId, courseId = english, dayOfWeek = 3, startPeriod = 3, endPeriod = 4, startWeek = 1, endWeek = 20, location = "文210"),
                CourseMeetingEntity(timetableId = timetableId, courseId = math, dayOfWeek = 5, startPeriod = 5, endPeriod = 6, startWeek = 1, endWeek = 20, location = "综A302"),
            ),
        )
        dao.upsertTask(
            TaskEntity(
                title = "提交高数习题册第六章",
                courseId = math,
                dueAtEpochMillis = System.currentTimeMillis() + 86_400_000L * 2,
                note = "演示任务，可在今日页勾选完成。",
            ),
        )
        dao.upsertEvent(
            CampusEventEntity(
                title = "创新实验室开放日",
                startsAtEpochMillis = System.currentTimeMillis() + 86_400_000L * 3,
                location = "科创楼一层",
                note = "演示校园事件",
            ),
        )
        dao.upsertGradeRules(defaultGradeRules())
    }

    suspend fun daoForBackup(): CampusDao = dao
    fun databaseForBackup(): XiangeceDatabase = database

    private suspend fun findOrCreateCourse(name: String): Long {
        dao.allCourses().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.let { return it.id }
        return dao.upsertCourse(CourseEntity(timetableId = currentTimetableId(), name = name.trim(), colorArgb = courseColor(name)))
    }

    private suspend fun currentTimetableId(): Long = ensureCurrentTimetable().id

    private suspend fun ensureCurrentTimetable(): TimetableEntity {
        dao.currentTimetable()?.let { return it }
        val id = dao.upsertTimetable(TimetableEntity(id = 1, name = "默认课表", isCurrent = true))
        return TimetableEntity(id = id, name = "默认课表", isCurrent = true)
    }

    private fun courseColor(value: String): Long {
        val colors = listOf(0xFF56766A, 0xFF8A6659, 0xFF5F7088, 0xFF8A7A4D, 0xFF7B617B)
        return colors[(value.hashCode() and Int.MAX_VALUE) % colors.size]
    }

    private fun defaultPeriods() = listOf(
        1 to (8 * 60 to 8 * 60 + 45),
        2 to (8 * 60 + 55 to 9 * 60 + 40),
        3 to (10 * 60 to 10 * 60 + 45),
        4 to (10 * 60 + 55 to 11 * 60 + 40),
        5 to (14 * 60 to 14 * 60 + 45),
        6 to (14 * 60 + 55 to 15 * 60 + 40),
        7 to (16 * 60 to 16 * 60 + 45),
        8 to (16 * 60 + 55 to 17 * 60 + 40),
        9 to (19 * 60 to 19 * 60 + 45),
        10 to (19 * 60 + 55 to 20 * 60 + 40),
    ).map { (index, range) ->
        PeriodTemplateEntity(index, range.first, range.second)
    }

    private fun defaultGradeRules(): List<GradeRuleEntity> {
        val schemes = mapOf(
            "4.0" to listOf(90.0 to 4.0, 85.0 to 3.7, 82.0 to 3.3, 78.0 to 3.0, 75.0 to 2.7, 72.0 to 2.3, 68.0 to 2.0, 64.0 to 1.5, 60.0 to 1.0, 0.0 to 0.0),
            "4.3" to listOf(90.0 to 4.3, 85.0 to 4.0, 80.0 to 3.7, 75.0 to 3.3, 70.0 to 3.0, 65.0 to 2.3, 60.0 to 1.7, 0.0 to 0.0),
            "5.0" to listOf(90.0 to 5.0, 85.0 to 4.5, 80.0 to 4.0, 75.0 to 3.5, 70.0 to 3.0, 65.0 to 2.5, 60.0 to 2.0, 0.0 to 0.0),
        )
        return schemes.flatMap { (name, values) ->
            values.map { (min, point) -> GradeRuleEntity(schemeName = name, minScore = min, gradePoint = point) }
        }
    }
}

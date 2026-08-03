package win.iqwqi.xiangece.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CampusDao {
    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    fun observeCurrentSemester(): Flow<SemesterEntity?>

    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    suspend fun currentSemester(): SemesterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSemester(value: SemesterEntity): Long

    @Query("UPDATE semesters SET isCurrent = 0")
    suspend fun clearCurrentSemester()

    @Query("SELECT * FROM timetables ORDER BY isCurrent DESC, createdAtEpochMillis")
    fun observeTimetables(): Flow<List<TimetableEntity>>

    @Query("SELECT * FROM timetables ORDER BY isCurrent DESC, createdAtEpochMillis")
    suspend fun allTimetables(): List<TimetableEntity>

    @Query("SELECT * FROM timetables WHERE isCurrent = 1 LIMIT 1")
    suspend fun currentTimetable(): TimetableEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimetable(value: TimetableEntity): Long

    @Query("UPDATE timetables SET isCurrent = 0")
    suspend fun clearCurrentTimetable()

    @Query("SELECT * FROM period_templates ORDER BY periodIndex")
    fun observePeriods(): Flow<List<PeriodTemplateEntity>>

    @Query("SELECT * FROM period_templates ORDER BY periodIndex")
    suspend fun allPeriods(): List<PeriodTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeriods(values: List<PeriodTemplateEntity>)

    @Query("SELECT * FROM courses WHERE timetableId = (SELECT id FROM timetables WHERE isCurrent = 1 LIMIT 1) ORDER BY name")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE timetableId = (SELECT id FROM timetables WHERE isCurrent = 1 LIMIT 1) ORDER BY name")
    suspend fun allCourses(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun courseById(id: Long): CourseEntity?

    @Query("SELECT COUNT(*) FROM courses WHERE timetableId = (SELECT id FROM timetables WHERE isCurrent = 1 LIMIT 1)")
    suspend fun courseCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourse(value: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourses(values: List<CourseEntity>)

    @Query("SELECT * FROM course_meetings WHERE timetableId = (SELECT id FROM timetables WHERE isCurrent = 1 LIMIT 1) ORDER BY dayOfWeek, startPeriod")
    fun observeMeetings(): Flow<List<CourseMeetingEntity>>

    @Query("SELECT * FROM course_meetings WHERE timetableId = (SELECT id FROM timetables WHERE isCurrent = 1 LIMIT 1) ORDER BY dayOfWeek, startPeriod")
    suspend fun allMeetings(): List<CourseMeetingEntity>

    @Query("SELECT * FROM course_meetings WHERE id = :id LIMIT 1")
    suspend fun meetingById(id: Long): CourseMeetingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeeting(value: CourseMeetingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeetings(values: List<CourseMeetingEntity>)

    @Query("SELECT * FROM tasks ORDER BY status, dueAtEpochMillis IS NULL, dueAtEpochMillis")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun allTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun taskById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(value: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(values: List<TaskEntity>)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun setTaskStatus(id: Long, status: String)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("UPDATE tasks SET courseId = NULL WHERE courseId = :courseId")
    suspend fun detachTasksFromCourse(courseId: Long)

    @Query("SELECT * FROM campus_events ORDER BY startsAtEpochMillis")
    fun observeEvents(): Flow<List<CampusEventEntity>>

    @Query("SELECT * FROM campus_events")
    suspend fun allEvents(): List<CampusEventEntity>

    @Query("SELECT * FROM campus_events WHERE id = :id LIMIT 1")
    suspend fun eventById(id: Long): CampusEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(value: CampusEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(values: List<CampusEventEntity>)

    @Query("DELETE FROM campus_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query("SELECT * FROM inbox_items WHERE timetableId = (SELECT id FROM timetables WHERE isCurrent = 1 LIMIT 1) ORDER BY createdAtEpochMillis DESC")
    fun observeInbox(): Flow<List<InboxItemEntity>>

    @Query("SELECT * FROM inbox_items")
    suspend fun allInbox(): List<InboxItemEntity>

    @Query("SELECT * FROM inbox_items WHERE id = :id LIMIT 1")
    suspend fun inboxById(id: Long): InboxItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInbox(value: InboxItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInbox(values: List<InboxItemEntity>)

    @Query("DELETE FROM ocr_snapshots WHERE inboxItemId = :inboxId")
    suspend fun deleteOcrSnapshotsForInbox(inboxId: Long)

    @Query("DELETE FROM inbox_items WHERE id = :id")
    suspend fun deleteInbox(id: Long)

    @Query("UPDATE inbox_items SET status = :status, parsedDraftJson = :draftJson, ocrText = :ocrText, errorMessage = :error WHERE id = :id")
    suspend fun updateInboxProcessing(
        id: Long,
        status: String,
        draftJson: String?,
        ocrText: String,
        error: String?,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOcrSnapshot(value: OcrSnapshotEntity): Long

    @Query("SELECT * FROM ocr_snapshots")
    suspend fun allOcrSnapshots(): List<OcrSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOcrSnapshots(values: List<OcrSnapshotEntity>)

    @Query("SELECT * FROM grade_records ORDER BY id DESC")
    fun observeGrades(): Flow<List<GradeRecordEntity>>

    @Query("SELECT * FROM grade_records")
    suspend fun allGrades(): List<GradeRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGrade(value: GradeRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGrades(values: List<GradeRecordEntity>)

    @Query("DELETE FROM grade_records WHERE id = :id")
    suspend fun deleteGrade(id: Long)

    @Query("SELECT * FROM grade_rules ORDER BY schemeName, minScore DESC")
    suspend fun allGradeRules(): List<GradeRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGradeRules(values: List<GradeRuleEntity>)

    @Query("SELECT * FROM reminders WHERE enabled = 1 AND triggerAtEpochMillis > :now")
    suspend fun upcomingReminders(now: Long = System.currentTimeMillis()): List<ReminderEntity>

    @Query("SELECT * FROM reminders ORDER BY triggerAtEpochMillis")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun allReminders(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(value: ReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminders(values: List<ReminderEntity>)

    @Query("SELECT * FROM reminders WHERE targetType = :targetType AND targetId = :targetId")
    suspend fun remindersForTarget(targetType: String, targetId: Long): List<ReminderEntity>

    @Query("DELETE FROM reminders WHERE targetType = :targetType AND targetId = :targetId")
    suspend fun deleteRemindersForTarget(targetType: String, targetId: Long)

    @Query("DELETE FROM reminders WHERE triggerAtEpochMillis <= :now")
    suspend fun deleteExpiredReminders(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    @Query("DELETE FROM course_meetings WHERE id = :id")
    suspend fun deleteMeeting(id: Long)

    @Query("SELECT COUNT(*) FROM course_meetings WHERE courseId = :courseId")
    suspend fun meetingCountForCourse(courseId: Long): Int

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourse(id: Long)

    @Query("DELETE FROM semesters")
    suspend fun clearSemesters()
    @Query("DELETE FROM timetables")
    suspend fun clearTimetables()
    @Query("DELETE FROM period_templates")
    suspend fun clearPeriods()
    @Query("DELETE FROM courses")
    suspend fun clearCourses()
    @Query("DELETE FROM course_meetings")
    suspend fun clearMeetings()
    @Query("DELETE FROM tasks")
    suspend fun clearTasks()
    @Query("DELETE FROM campus_events")
    suspend fun clearEvents()
    @Query("DELETE FROM inbox_items")
    suspend fun clearInbox()
    @Query("DELETE FROM ocr_snapshots")
    suspend fun clearOcrSnapshots()
    @Query("DELETE FROM grade_records")
    suspend fun clearGrades()
    @Query("DELETE FROM grade_rules")
    suspend fun clearGradeRules()
    @Query("DELETE FROM reminders")
    suspend fun clearReminders()

    @Query("SELECT * FROM habit_templates ORDER BY orderIndex, createdAtEpochMillis")
    fun observeHabitTemplates(): Flow<List<HabitTemplateEntity>>

    @Query("SELECT * FROM habit_templates ORDER BY orderIndex, createdAtEpochMillis")
    suspend fun allHabitTemplates(): List<HabitTemplateEntity>

    @Query("SELECT * FROM habit_templates WHERE id = :id LIMIT 1")
    suspend fun habitTemplateById(id: Long): HabitTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabitTemplate(value: HabitTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabitTemplates(values: List<HabitTemplateEntity>)

    @Query("DELETE FROM habit_templates WHERE id = :id")
    suspend fun deleteHabitTemplate(id: Long)

    @Query("SELECT * FROM habit_checkins ORDER BY checkinDateEpochDay")
    fun observeHabitCheckins(): Flow<List<HabitCheckinEntity>>

    @Query("SELECT * FROM habit_checkins")
    suspend fun allHabitCheckins(): List<HabitCheckinEntity>

    @Query("SELECT * FROM habit_checkins WHERE habitId = :habitId AND checkinDateEpochDay = :day LIMIT 1")
    suspend fun habitCheckinOnDay(habitId: Long, day: Long): HabitCheckinEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabitCheckin(value: HabitCheckinEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabitCheckins(values: List<HabitCheckinEntity>)

    @Query("DELETE FROM habit_checkins WHERE habitId = :habitId AND checkinDateEpochDay = :day")
    suspend fun deleteHabitCheckin(habitId: Long, day: Long)

    @Query("DELETE FROM habit_checkins WHERE habitId = :habitId")
    suspend fun deleteHabitCheckinsForHabit(habitId: Long)

    @Query("DELETE FROM habit_templates")
    suspend fun clearHabitTemplates()

    @Query("DELETE FROM habit_checkins")
    suspend fun clearHabitCheckins()

    // --- Custom Quotes ---

    @Query("SELECT * FROM custom_quotes ORDER BY isBuiltIn DESC, orderIndex, createdAtEpochMillis")
    fun observeCustomQuotes(): Flow<List<CustomQuoteEntity>>

    @Query("SELECT * FROM custom_quotes WHERE isBuiltIn = 0 ORDER BY orderIndex, createdAtEpochMillis")
    suspend fun userQuotes(): List<CustomQuoteEntity>

    @Query("SELECT * FROM custom_quotes ORDER BY isBuiltIn DESC, orderIndex, createdAtEpochMillis")
    suspend fun allCustomQuotes(): List<CustomQuoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomQuote(value: CustomQuoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomQuotes(values: List<CustomQuoteEntity>)

    @Query("DELETE FROM custom_quotes WHERE id = :id")
    suspend fun deleteCustomQuote(id: Long)

    @Query("DELETE FROM custom_quotes WHERE isBuiltIn = 0")
    suspend fun clearUserQuotes()
}

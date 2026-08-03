package win.iqwqi.xiangece.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import win.iqwqi.xiangece.domain.model.HabitFrequency
import win.iqwqi.xiangece.domain.model.InboxStatus
import win.iqwqi.xiangece.domain.model.TaskStatus
import win.iqwqi.xiangece.domain.model.WeekParity

@Serializable
@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDateEpochDay: Long,
    val weekCount: Int,
    val isCurrent: Boolean = true,
)

@Serializable
@Entity(tableName = "timetables")
data class TimetableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val semesterId: Long? = null,
    val isCurrent: Boolean = true,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "period_templates")
data class PeriodTemplateEntity(
    @PrimaryKey val periodIndex: Int,
    val startMinutes: Int,
    val endMinutes: Int,
)

@Serializable
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timetableId: Long = 1,
    val name: String,
    val teacher: String = "",
    val defaultLocation: String = "",
    val colorArgb: Long = 0xFF6B8577,
    val note: String = "",
)

@Serializable
@Entity(tableName = "course_meetings")
data class CourseMeetingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timetableId: Long = 1,
    val courseId: Long,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekParity: WeekParity = WeekParity.ALL,
    val location: String = "",
)

@Serializable
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val courseId: Long? = null,
    val dueAtEpochMillis: Long? = null,
    val status: TaskStatus = TaskStatus.TODO,
    val note: String = "",
    val sourceInboxId: Long? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "campus_events")
data class CampusEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long? = null,
    val location: String = "",
    val note: String = "",
    val sourceInboxId: Long? = null,
)

@Serializable
@Entity(tableName = "inbox_items")
data class InboxItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timetableId: Long = 1,
    val sourceType: String,
    val originalText: String = "",
    val imagePath: String? = null,
    val ocrText: String = "",
    val parsedDraftJson: String? = null,
    val status: InboxStatus = InboxStatus.PROCESSING,
    val errorMessage: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "ocr_snapshots")
data class OcrSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inboxItemId: Long,
    val recognizedText: String,
    val engine: String = "AI or file import",
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "grade_records")
data class GradeRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseName: String,
    val credit: Double,
    val score: Double,
    val term: String = "",
)

@Serializable
@Entity(tableName = "grade_rules")
data class GradeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val schemeName: String,
    val minScore: Double,
    val gradePoint: Double,
)

@Serializable
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: String,
    val targetId: Long,
    val triggerAtEpochMillis: Long,
    val title: String,
    val body: String,
    val channel: String,
    val enabled: Boolean = true,
)

@Serializable
@Entity(tableName = "habit_templates")
data class HabitTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val colorArgb: Long = 0xFF6B8577,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val orderIndex: Int = 0,
)

@Serializable
@Entity(tableName = "habit_checkins")
data class HabitCheckinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val checkinDateEpochDay: Long,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "custom_quotes")
data class CustomQuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val author: String = "",
    val isBuiltIn: Boolean = false,
    val orderIndex: Int = 0,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

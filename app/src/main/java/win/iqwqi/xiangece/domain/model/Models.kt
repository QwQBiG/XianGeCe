package win.iqwqi.xiangece.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class WeekParity { ALL, ODD, EVEN }

@Serializable
enum class HabitFrequency { DAILY, WEEKLY, X_TIMES_PER_WEEK }

@Serializable
enum class TaskStatus { TODO, DONE }

@Serializable
enum class InboxStatus { PROCESSING, PENDING, CONFIRMED, FAILED }

@Serializable
enum class DraftType { TASK, EVENT, NOTE, COURSE_MEETING }

@Serializable
data class ParsedDraft(
    val type: DraftType = DraftType.TASK,
    val title: String = "",
    val description: String = "",
    val dateTimeEpochMillis: Long? = null,
    val courseName: String? = null,
    val location: String? = null,
    val teachingWeek: Int? = null,
    val dayOfWeek: Int? = null,
    val startPeriod: Int? = null,
    val endPeriod: Int? = null,
    val weekParity: WeekParity = WeekParity.ALL,
    val confidence: Float = 0f,
    val ambiguities: List<String> = emptyList(),
)

@Serializable
data class PeriodTime(
    val index: Int,
    val start: String,
    val end: String,
)


package win.iqwqi.xiangece.core.importing

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import win.iqwqi.xiangece.data.local.CourseEntity
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.domain.model.WeekParity
import win.iqwqi.xiangece.domain.parser.TimetableCandidate

@Serializable
private data class SharedTimetable(
    val version: Int = 1,
    val rows: List<SharedCourseRow>,
)

@Serializable
private data class SharedCourseRow(
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startWeek: Int,
    val endWeek: Int,
    val parity: WeekParity = WeekParity.ALL,
)

object TimetableCodeCodec {
    private const val PREFIX = "XGC1-"
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(
        courses: List<CourseEntity>,
        meetings: List<CourseMeetingEntity>,
    ): String {
        val coursesById = courses.associateBy { it.id }
        val payload = SharedTimetable(
            rows = meetings.mapNotNull { meeting ->
                val course = coursesById[meeting.courseId] ?: return@mapNotNull null
                SharedCourseRow(
                    name = course.name,
                    teacher = course.teacher,
                    location = meeting.location.ifBlank { course.defaultLocation },
                    day = meeting.dayOfWeek,
                    startPeriod = meeting.startPeriod,
                    endPeriod = meeting.endPeriod,
                    startWeek = meeting.startWeek,
                    endWeek = meeting.endWeek,
                    parity = meeting.weekParity,
                )
            },
        )
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.encodeToString(payload).encodeToByteArray())
        return PREFIX + encoded
    }

    fun decode(value: String): List<TimetableCandidate> {
        val compact = value.trim().replace(Regex("\\s+"), "")
        require(compact.startsWith(PREFIX)) { "这不是弦歌册课表口令（应以 XGC1- 开头）" }
        val raw = Base64.getUrlDecoder().decode(compact.removePrefix(PREFIX))
        val payload = json.decodeFromString<SharedTimetable>(raw.decodeToString())
        require(payload.version == 1) { "暂不支持这个版本的课表口令" }
        require(payload.rows.isNotEmpty()) { "课表口令中没有课程" }
        return payload.rows.map {
            require(it.day in 1..7 && it.startPeriod in 1..24 && it.endPeriod in it.startPeriod..24) {
                "课表口令包含无效节次"
            }
            TimetableCandidate(
                name = it.name,
                teacher = it.teacher,
                location = it.location,
                dayOfWeek = it.day,
                startPeriod = it.startPeriod,
                endPeriod = it.endPeriod,
                startWeek = it.startWeek,
                endWeek = it.endWeek,
                parity = it.parity,
            )
        }
    }
}

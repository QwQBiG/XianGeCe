package win.iqwqi.xiangece.domain.parser

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import win.iqwqi.xiangece.domain.model.DraftType

data class DraftConfirmationFields(
    val type: DraftType,
    val title: String,
    val dateTimeText: String,
    val courseName: String,
    val teachingWeekText: String,
    val dayOfWeekText: String,
    val startPeriodText: String,
    val endPeriodText: String,
    val hasAmbiguities: Boolean,
    val ambiguitiesAcknowledged: Boolean,
)

object DraftConfirmationValidator {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun error(fields: DraftConfirmationFields, semesterWeekCount: Int): String? {
        if (fields.title.isBlank()) return "请填写标题"
        if (fields.hasAmbiguities && !fields.ambiguitiesAcknowledged) {
            return "请核对并确认识别歧义"
        }
        if (fields.dateTimeText.isNotBlank() && !isValidDateTime(fields.dateTimeText)) {
            return "时间格式应为 2026-09-01 18:00"
        }
        if (fields.type == DraftType.EVENT && fields.dateTimeText.isBlank()) {
            return "事件必须填写日期与时间"
        }
        if (fields.type != DraftType.COURSE_MEETING) return null
        if (fields.courseName.isBlank()) return "请填写课程名称"

        val day = fields.dayOfWeekText.toIntOrNull()
        if (day !in 1..7) return "星期应为 1–7"
        val start = fields.startPeriodText.toIntOrNull()
        val end = fields.endPeriodText.toIntOrNull()
        if (start == null || end == null || start !in 1..24 || end !in start..24) {
            return "请检查课程开始、结束节次"
        }
        if (fields.teachingWeekText.isNotBlank()) {
            val week = fields.teachingWeekText.toIntOrNull()
            if (week == null || week !in 1..semesterWeekCount.coerceAtLeast(1)) {
                return "教学周超出当前学期范围"
            }
        }
        return null
    }

    private fun isValidDateTime(value: String): Boolean = runCatching {
        LocalDateTime.parse(value.trim(), dateTimeFormatter)
    }.isSuccess
}

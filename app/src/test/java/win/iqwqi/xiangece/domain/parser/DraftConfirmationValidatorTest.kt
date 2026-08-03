package win.iqwqi.xiangece.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import win.iqwqi.xiangece.domain.model.DraftType

class DraftConfirmationValidatorTest {
    @Test
    fun ambiguityMustBeExplicitlyAcknowledged() {
        val fields = validTask().copy(
            hasAmbiguities = true,
            ambiguitiesAcknowledged = false,
        )

        assertEquals("请核对并确认识别歧义", DraftConfirmationValidator.error(fields, 20))
        assertNull(
            DraftConfirmationValidator.error(
                fields.copy(ambiguitiesAcknowledged = true),
                20,
            ),
        )
    }

    @Test
    fun eventRequiresValidDateTime() {
        assertEquals(
            "事件必须填写日期与时间",
            DraftConfirmationValidator.error(
                validTask().copy(type = DraftType.EVENT, dateTimeText = ""),
                20,
            ),
        )
        assertEquals(
            "时间格式应为 2026-09-01 18:00",
            DraftConfirmationValidator.error(
                validTask().copy(type = DraftType.EVENT, dateTimeText = "9月10日"),
                20,
            ),
        )
    }

    @Test
    fun courseRequiresValidFieldsWithinSemester() {
        val course = validTask().copy(
            type = DraftType.COURSE_MEETING,
            courseName = "数据结构",
            teachingWeekText = "21",
            dayOfWeekText = "3",
            startPeriodText = "3",
            endPeriodText = "4",
        )

        assertEquals("教学周超出当前学期范围", DraftConfirmationValidator.error(course, 20))
        assertNull(
            DraftConfirmationValidator.error(
                course.copy(teachingWeekText = "3"),
                20,
            ),
        )
    }

    private fun validTask() = DraftConfirmationFields(
        type = DraftType.TASK,
        title = "提交作业",
        dateTimeText = "2026-09-10 18:00",
        courseName = "",
        teachingWeekText = "",
        dayOfWeekText = "",
        startPeriodText = "",
        endPeriodText = "",
        hasAmbiguities = false,
        ambiguitiesAcknowledged = true,
    )
}

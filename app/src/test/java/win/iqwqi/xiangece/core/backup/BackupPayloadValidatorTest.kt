package win.iqwqi.xiangece.core.backup

import org.junit.Assert.assertThrows
import org.junit.Test
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.settings.AppSettings

class BackupPayloadValidatorTest {
    @Test
    fun acceptsEmptyValidBackup() {
        BackupPayloadValidator.validate(validPayload())
    }

    @Test
    fun rejectsForeignApplicationBackup() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupPayloadValidator.validate(validPayload().copy(applicationId = "example.foreign"))
        }
    }

    @Test
    fun rejectsDanglingCourseReference() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupPayloadValidator.validate(
                validPayload().copy(
                    meetings = listOf(
                        CourseMeetingEntity(
                            courseId = 99,
                            dayOfWeek = 1,
                            startPeriod = 1,
                            endPeriod = 2,
                            startWeek = 1,
                            endWeek = 16,
                        ),
                    ),
                ),
            )
        }
    }

    private fun validPayload() = BackupPayload(
        settings = AppSettings(),
        semesters = emptyList(),
        periods = emptyList(),
        courses = emptyList(),
        meetings = emptyList(),
        tasks = emptyList(),
        events = emptyList(),
        inbox = emptyList(),
        ocrSnapshots = emptyList(),
        grades = emptyList(),
        gradeRules = emptyList(),
        reminders = emptyList(),
    )
}

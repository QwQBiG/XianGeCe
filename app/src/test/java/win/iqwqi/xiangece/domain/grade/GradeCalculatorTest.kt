package win.iqwqi.xiangece.domain.grade

import org.junit.Assert.assertEquals
import org.junit.Test
import win.iqwqi.xiangece.data.local.GradeRecordEntity

class GradeCalculatorTest {
    private val records = listOf(
        GradeRecordEntity(courseName = "高数", credit = 4.0, score = 90.0),
        GradeRecordEntity(courseName = "英语", credit = 2.0, score = 80.0),
    )

    @Test
    fun weightedAverageUsesCredits() {
        val result = GradeCalculator.calculate(records, "4.0")
        assertEquals(86.67, result.weightedAverage, 0.01)
        assertEquals(6.0, result.totalCredits, 0.0)
    }

    @Test
    fun customRulesAreApplied() {
        val result = GradeCalculator.calculateCustom(records, "90=5.0,80=4.0,60=2.0,0=0")
        assertEquals(4.67, result.gpa, 0.01)
    }
}

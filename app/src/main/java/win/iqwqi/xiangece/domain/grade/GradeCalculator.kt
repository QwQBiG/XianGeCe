package win.iqwqi.xiangece.domain.grade

import win.iqwqi.xiangece.data.local.GradeRecordEntity

data class GradeSummary(
    val totalCredits: Double,
    val weightedAverage: Double,
    val gpa: Double,
)

object GradeCalculator {
    fun calculate(records: List<GradeRecordEntity>, scheme: String): GradeSummary {
        val credits = records.sumOf { it.credit }
        if (credits <= 0.0) return GradeSummary(0.0, 0.0, 0.0)
        val average = records.sumOf { it.score * it.credit } / credits
        val gpa = records.sumOf { gradePoint(it.score, scheme) * it.credit } / credits
        return GradeSummary(credits, average, gpa)
    }

    fun gradePoint(score: Double, scheme: String): Double {
        val rules = when (scheme) {
            "4.3" -> listOf(90.0 to 4.3, 85.0 to 4.0, 80.0 to 3.7, 75.0 to 3.3, 70.0 to 3.0, 65.0 to 2.3, 60.0 to 1.7, 0.0 to 0.0)
            "5.0" -> listOf(90.0 to 5.0, 85.0 to 4.5, 80.0 to 4.0, 75.0 to 3.5, 70.0 to 3.0, 65.0 to 2.5, 60.0 to 2.0, 0.0 to 0.0)
            else -> listOf(90.0 to 4.0, 85.0 to 3.7, 82.0 to 3.3, 78.0 to 3.0, 75.0 to 2.7, 72.0 to 2.3, 68.0 to 2.0, 64.0 to 1.5, 60.0 to 1.0, 0.0 to 0.0)
        }
        return rules.first { score >= it.first }.second
    }

    fun calculateCustom(records: List<GradeRecordEntity>, ruleText: String): GradeSummary {
        val rules = parseCustomRules(ruleText)
        if (rules.isEmpty()) return calculate(records, "4.0")
        val credits = records.sumOf { it.credit }
        if (credits <= 0.0) return GradeSummary(0.0, 0.0, 0.0)
        return GradeSummary(
            totalCredits = credits,
            weightedAverage = records.sumOf { it.score * it.credit } / credits,
            gpa = records.sumOf { record ->
                (rules.firstOrNull { record.score >= it.first }?.second ?: 0.0) * record.credit
            } / credits,
        )
    }

    fun parseCustomRules(ruleText: String): List<Pair<Double, Double>> =
        ruleText.split(',', '，', ';', '；', '\n')
            .mapNotNull { item ->
                val parts = item.trim().split('=', ':', '：')
                if (parts.size != 2) null
                else {
                    val minimum = parts[0].trim().toDoubleOrNull()
                    val point = parts[1].trim().toDoubleOrNull()
                    if (minimum == null || point == null) null else minimum to point
                }
            }
            .sortedByDescending { it.first }
}

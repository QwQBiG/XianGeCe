package win.iqwqi.xiangece.core.ai

import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DailyExtractionTest {
    @Test fun distinctActionsKeepTheirOwnDates() {
        val result = DailyExtractionDecoder.decode("""{"sourceText":"9月20日18:00报名截止，9月22日14:30参加讲座。","items":[
            {"type":"TASK","title":"报名讲座","sourceQuote":"9月20日18:00报名截止","dateTime":"2026-09-20T18:00","dateEvidence":"9月20日18:00"},
            {"type":"EVENT","title":"参加讲座","sourceQuote":"9月22日14:30参加讲座","dateTime":"2026-09-22T14:30","dateEvidence":"9月22日14:30"}]}""", zone = ZoneId.of("Asia/Shanghai"))
        assertEquals(2, result.drafts.size)
        assertNotEquals(result.drafts[0].dateTimeEpochMillis, result.drafts[1].dateTimeEpochMillis)
        assertTrue(result.drafts.all { it.ambiguities.any { message -> "年份" in message } })
    }

    @Test fun wrongDateAndMissingDateRemainUnconfirmed() {
        val wrong = DailyExtractionDecoder.decode("""{"sourceText":"2026年9月20日18:00提交报告","items":[
            {"type":"TASK","title":"提交报告","sourceQuote":"2026年9月20日18:00提交报告","dateTime":"2026-09-21T18:00","dateEvidence":"2026年9月20日18:00"}]}""")
        assertNull(wrong.drafts.single().dateTimeEpochMillis)
        assertTrue(wrong.drafts.single().ambiguities.isNotEmpty())
        val relative = DailyExtractionDecoder.decode("""{"sourceText":"明天提交报告","items":[
            {"type":"TASK","title":"提交报告","sourceQuote":"明天提交报告","dateTime":"2026-09-12T09:00","dateEvidence":"明天"}]}""")
        assertNull(relative.drafts.single().dateTimeEpochMillis)
    }

    @Test fun anotherItemsEvidenceCannotSupplyADate() {
        val result = DailyExtractionDecoder.decode("""{"sourceText":"报名截止另行通知；9月22日14:30参加讲座","items":[
            {"type":"TASK","title":"报名","sourceQuote":"报名截止另行通知","dateTime":"2026-09-22T14:30","dateEvidence":"9月22日14:30"}]}""")
        assertNull(result.drafts.single().dateTimeEpochMillis)
    }

    @Test(expected = IllegalArgumentException::class) fun fabricatedQuotesAreRejected() {
        DailyExtractionDecoder.decode("""{"sourceText":"保持整洁","items":[{"title":"交钱","sourceQuote":"明天交费"}]}""")
    }

    @Test fun suppliedOriginalWinsOverModelRewriting() {
        val result = DailyExtractionDecoder.decode("""{"sourceText":"模型改写","items":[{"type":"NOTE","title":"清理教室","sourceQuote":"保持整洁"}]}""", "保持整洁")
        assertEquals("保持整洁", result.sourceText)
    }
}

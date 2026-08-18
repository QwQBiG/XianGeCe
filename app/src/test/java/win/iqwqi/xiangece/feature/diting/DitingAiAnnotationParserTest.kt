package win.iqwqi.xiangece.feature.diting

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.feature.diting.data.DitingSegmentEntity
import win.iqwqi.xiangece.feature.diting.domain.DitingMarkerType
import win.iqwqi.xiangece.feature.diting.transcription.DitingAiAnnotationParser

class DitingAiAnnotationParserTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val segments = listOf(
        DitingSegmentEntity(
            id = 11,
            sessionId = 7,
            sequence = 3,
            startMillis = 24_000,
            endMillis = 32_000,
            text = "这部分是考试重点，傅里叶变换一定要会。",
            isFinal = true,
            status = "completed",
        ),
        DitingSegmentEntity(
            id = 12,
            sessionId = 7,
            sequence = 4,
            startMillis = 32_000,
            endMillis = 40_000,
            text = "大家还有没有问题？",
            isFinal = true,
            status = "completed",
        ),
    )

    @Test
    fun acceptsOnlyHighConfidenceExactEvidence() {
        val result = DitingAiAnnotationParser.parse(
            """
            [
              {"type":"HIGHLIGHT","sequence":3,"title":"考试重点","note":"需要掌握","evidence":"考试重点","confidence":0.91},
              {"type":"QUESTION","sequence":4,"title":"课堂提问","note":"","evidence":"有没有问题","confidence":0.80}
            ]
            """.trimIndent(),
            segments,
            json,
        )

        assertEquals(2, result.size)
        assertEquals(DitingMarkerType.AUTO_HIGHLIGHT, result[0].type)
        assertEquals(DitingMarkerType.AUTO_QUESTION, result[1].type)
    }

    @Test
    fun rejectsLowConfidenceUnknownSequenceAndNonExactEvidence() {
        val result = DitingAiAnnotationParser.parse(
            """
            [
              {"type":"HIGHLIGHT","sequence":3,"title":"低置信度","evidence":"考试重点","confidence":0.79},
              {"type":"QUESTION","sequence":99,"title":"不存在","evidence":"有没有问题","confidence":0.99},
              {"type":"QUESTION","sequence":4,"title":"改写证据","evidence":"课堂存在问题","confidence":0.99}
            ]
            """.trimIndent(),
            segments,
            json,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun deDuplicatesSameTypeAndSequence() {
        val result = DitingAiAnnotationParser.parse(
            """
            [
              {"type":"HIGHLIGHT","sequence":3,"title":"先出现","evidence":"考试重点","confidence":0.90},
              {"type":"HIGHLIGHT","sequence":3,"title":"重复项","evidence":"考试重点","confidence":0.99}
            ]
            """.trimIndent(),
            segments,
            json,
        )

        assertEquals(1, result.size)
        assertEquals("重复项", result.single().title)
    }
    @Test
    fun acceptsEvidenceWhenOnlyAsrWhitespaceDiffers() {
        val spacedSegment = segments.first().copy(
            text = "这部分是考试重点，\n傅里叶变换一定要会。",
        )
        val result = DitingAiAnnotationParser.parse(
            """[{"type":"HIGHLIGHT","sequence":3,"title":"考试重点","evidence":"考试重点， 傅里叶变换","confidence":0.91}]""",
            listOf(spacedSegment),
            json,
        )

        assertEquals(1, result.size)
    }

    @Test
    fun rejectsEvidenceLongerThanThirtyCharacters() {
        val longText = "这是一个超过三十个字符的课堂原文证据片段，用来验证长度保护规则。"
        val longSegment = segments.first().copy(text = longText)
        val result = DitingAiAnnotationParser.parse(
            """[{"type":"HIGHLIGHT","sequence":3,"title":"超长证据","evidence":"$longText","confidence":0.99}]""",
            listOf(longSegment),
            json,
        )

        assertTrue(result.isEmpty())
    }
}

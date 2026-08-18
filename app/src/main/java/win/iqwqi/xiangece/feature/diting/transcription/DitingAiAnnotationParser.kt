package win.iqwqi.xiangece.feature.diting.transcription

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import win.iqwqi.xiangece.feature.diting.data.DitingSegmentEntity
import win.iqwqi.xiangece.feature.diting.domain.DitingMarkerType

@Serializable
private data class AiAnnotationPayload(
    val type: String = "",
    val sequence: Int = -1,
    val title: String = "",
    val note: String = "",
    val evidence: String = "",
    val confidence: Float = 0f,
)

internal object DitingAiAnnotationParser {
    fun parse(
        content: String,
        segments: List<DitingSegmentEntity>,
        json: Json,
    ): List<DitingAiAnnotation> {
        val start = content.indexOf('[')
        val end = content.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val payload = runCatching {
            json.decodeFromString<List<AiAnnotationPayload>>(content.substring(start, end + 1))
        }.getOrNull() ?: return emptyList()
        val segmentBySequence = segments.associateBy { it.sequence }
        return payload.mapNotNull { item ->
            val segment = segmentBySequence[item.sequence] ?: return@mapNotNull null
            val type = when (item.type.uppercase()) {
                "HIGHLIGHT" -> DitingMarkerType.AUTO_HIGHLIGHT
                "QUESTION" -> DitingMarkerType.AUTO_QUESTION
                else -> return@mapNotNull null
            }
            val evidence = item.evidence.trim()
            if (
                !item.confidence.isFinite() ||
                item.confidence < MIN_CONFIDENCE ||
                evidence.isBlank() ||
                evidence.length > MAX_EVIDENCE_CHARS ||
                !containsEvidence(segment.text, evidence)
            ) {
                return@mapNotNull null
            }
            val title = item.title.trim().take(MAX_TITLE_CHARS).ifBlank {
                if (type == DitingMarkerType.AUTO_QUESTION) "AI提问" else "AI重点"
            }
            DitingAiAnnotation(
                sequence = item.sequence,
                type = type,
                title = title,
                note = item.note.trim().take(MAX_NOTE_CHARS),
                evidence = evidence,
                confidence = item.confidence.coerceIn(0f, 1f),
            )
        }.groupBy { it.type to it.sequence }
            .values
            .mapNotNull { candidates -> candidates.maxByOrNull { it.confidence } }
            .sortedBy { it.sequence }
            .take(MAX_RESULTS)
    }

    private fun containsEvidence(text: String, evidence: String): Boolean {
        if (text.contains(evidence)) return true
        val compactText = text.replace(Regex("\\s+"), "")
        val compactEvidence = evidence.replace(Regex("\\s+"), "")
        return compactEvidence.isNotBlank() && compactText.contains(compactEvidence)
    }
    private const val MAX_TITLE_CHARS = 10
    private const val MAX_NOTE_CHARS = 48
    private const val MAX_EVIDENCE_CHARS = 30
    private const val MAX_RESULTS = 4
    private const val MIN_CONFIDENCE = 0.80f
}

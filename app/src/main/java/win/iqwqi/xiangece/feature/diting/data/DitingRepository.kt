package win.iqwqi.xiangece.feature.diting.data

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import win.iqwqi.xiangece.data.local.XiangeceDatabase
import win.iqwqi.xiangece.feature.diting.audio.DitingAudioStore
import win.iqwqi.xiangece.feature.diting.domain.DitingSessionStatus
import win.iqwqi.xiangece.feature.diting.transcription.DitingAiAnnotationParser

@Singleton
class DitingRepository @Inject constructor(
    private val dao: DitingDao,
    private val database: XiangeceDatabase,
    private val audioStore: DitingAudioStore,
) {
    val sessions: Flow<List<DitingSessionEntity>> = dao.observeSessions()

    fun session(id: Long): Flow<DitingSessionEntity?> = dao.observeSession(id)

    suspend fun sessionSnapshot(id: Long): DitingSessionEntity? = dao.sessionById(id)

    suspend fun hasActiveSession(): Boolean = dao.hasActiveSession()

    fun segments(sessionId: Long): Flow<List<DitingSegmentEntity>> = dao.observeSegments(sessionId)

    suspend fun segmentsSnapshot(sessionId: Long): List<DitingSegmentEntity> = dao.allSegments(sessionId)

    fun markers(sessionId: Long): Flow<List<DitingMarkerEntity>> = dao.observeMarkers(sessionId)

    suspend fun recoverInterruptedSessions() {
        val now = System.currentTimeMillis()
        val cutoff = now - 5 * 60_000L
        val staleIds = dao.staleActiveSessionIds(cutoff)
        dao.markActiveSessionsFailed(cutoff, now)
        for (id in staleIds) {
            audioStore.repairSessionWavFiles(id)
            recoverSavedChunks(id)
            dao.markTranscriptionUnavailable(id, "应用异常中断，未完成的分段未能转写")
        }
    }

    suspend fun createSession(
        title: String,
        courseId: Long? = null,
        meetingId: Long? = null,
        mode: String = "professional",
        languageMode: String = "auto",
        glossary: String = "",
        cloudTranscriptionEnabled: Boolean = false,
        aiAnnotationEnabled: Boolean = false,
    ): Long = dao.upsertSession(
        DitingSessionEntity(
            title = title.trim().ifBlank { "未命名课堂" },
            courseId = courseId,
            meetingId = meetingId,
            mode = mode,
            languageMode = languageMode,
            glossary = glossary.trim(),
            cloudTranscriptionEnabled = cloudTranscriptionEnabled,
            aiAnnotationEnabled = aiAnnotationEnabled,
            updatedAtEpochMillis = System.currentTimeMillis(),
        ),
    )

    suspend fun updateSession(value: DitingSessionEntity) {
        dao.upsertSession(value.copy(updatedAtEpochMillis = System.currentTimeMillis()))
    }

    suspend fun markRecordingStarted(id: Long, audioDirectory: String, audioPath: String) {
        val current = dao.sessionById(id) ?: return
        val now = System.currentTimeMillis()
        dao.upsertSession(
            current.copy(
                status = DitingSessionStatus.RECORDING.key,
                audioDirectory = audioDirectory,
                audioPath = audioPath,
                startedAtEpochMillis = current.startedAtEpochMillis ?: now,
                errorMessage = null,
                updatedAtEpochMillis = now,
            ),
        )
    }

    suspend fun updateTranscriptionEngine(id: Long, engine: String) {
        val current = dao.sessionById(id) ?: return
        dao.upsertSession(
            current.copy(
                transcriptionEngine = engine,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateDuration(id: Long, durationMillis: Long) {
        dao.updateSessionDuration(id, durationMillis.coerceAtLeast(0), System.currentTimeMillis())
    }

    suspend fun updateSessionMessage(id: Long, message: String?) {
        val current = dao.sessionById(id) ?: return
        dao.upsertSession(
            current.copy(
                errorMessage = message?.takeIf(String::isNotBlank),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateStatus(id: Long, status: DitingSessionStatus, errorMessage: String? = null) {
        val current = dao.sessionById(id) ?: return
        dao.upsertSession(
            current.copy(
                status = status.key,
                errorMessage = errorMessage,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun finishSession(id: Long, durationMillis: Long, audioBytes: Long) {
        val current = dao.sessionById(id) ?: return
        val now = System.currentTimeMillis()
        dao.upsertSession(
            current.copy(
                status = DitingSessionStatus.COMPLETED.key,
                endedAtEpochMillis = now,
                durationMillis = durationMillis.coerceAtLeast(0),
                audioBytes = audioBytes.coerceAtLeast(0),
                updatedAtEpochMillis = now,
            ),
        )
    }

    suspend fun findSegment(sessionId: Long, sequence: Int): DitingSegmentEntity? = dao.segmentBySequence(sessionId, sequence)

    suspend fun nextAwaitingTranscription(sessionId: Long): DitingSegmentEntity? =
        dao.nextAwaitingTranscription(sessionId)

    suspend fun awaitingTranscriptionCount(sessionId: Long): Int =
        dao.awaitingTranscriptionCount(sessionId)

    /** Rebuild missing callback rows from sequential WAVs without scheduling uploads. */
    suspend fun recoverSavedChunks(sessionId: Long): Int = withContext(Dispatchers.IO) {
        if (dao.sessionById(sessionId) == null) return@withContext 0
        var sequence = 0
        var totalBytes = 0L
        var recovered = 0
        while (true) {
            val file = audioStore.segmentFile(sessionId, sequence)
            if (!file.isFile) break
            val bytes = (file.length() - 44).coerceAtLeast(0)
            if (bytes > 0) {
                database.withTransaction {
                    if (dao.segmentBySequence(sessionId, sequence) == null) {
                        dao.upsertSegment(DitingSegmentEntity(
                            sessionId = sessionId,
                            sequence = sequence,
                            startMillis = totalBytes * 1_000L / 32_000L,
                            endMillis = (totalBytes + bytes) * 1_000L / 32_000L,
                            audioPath = file.absolutePath,
                            status = "local_audio_only",
                            errorMessage = "已找回原音分段，可重试文字识别。",
                        ))
                        recovered++
                    }
                }
            }
            totalBytes += bytes
            sequence++
        }
        recovered
    }

    suspend fun upsertSegment(segment: DitingSegmentEntity): Long = dao.upsertSegment(segment)

    suspend fun updateSegmentTranscript(
        sessionId: Long,
        sequence: Int,
        text: String,
        rawText: String,
        isFinal: Boolean,
        confidence: Float?,
        language: String,
        status: String,
    ): DitingSegmentEntity? = database.withTransaction {
        val current = dao.segmentBySequence(sessionId, sequence) ?: return@withTransaction null
        // A delayed inference result must not undo an intervening manual edit.
        if (current.isFinal && current.text != current.rawText && current.status != "transcribing") {
            return@withTransaction null
        }
        val updated = current.copy(
            text = text,
            rawText = rawText,
            isFinal = isFinal,
            confidence = confidence,
            language = language.ifBlank { current.language },
            status = status,
            errorMessage = null,
        )
        dao.upsertSegment(updated)
        return@withTransaction updated
    }

    suspend fun markTranscriptionUnavailable(sessionId: Long, reason: String = "转写服务不可用，音频已保留") {
        dao.markTranscriptionUnavailable(sessionId, reason)
    }

    suspend fun markSegmentAudioOnly(sessionId: Long, sequence: Int, reason: String = "转写失败，音频已保留") {
        database.withTransaction {
            val current = dao.segmentBySequence(sessionId, sequence) ?: return@withTransaction
            if (current.status in PENDING_TRANSCRIPTION_STATUSES) dao.markSegmentAudioOnly(sessionId, sequence, reason)
        }
    }

    suspend fun markSegmentTranscribing(sessionId: Long, sequence: Int) {
        val current = dao.segmentBySequence(sessionId, sequence) ?: return
        dao.upsertSegment(current.copy(status = "transcribing", errorMessage = null))
    }

    suspend fun markersSnapshot(sessionId: Long): List<DitingMarkerEntity> = dao.markersBySession(sessionId)

    suspend fun deleteLocalRuleMarkers(sessionId: Long) = dao.deleteLocalRuleMarkers(sessionId)

    suspend fun replaceLocalRuleMarkers(
        sessionId: Long,
        markers: List<DitingMarkerEntity>,
        expectedTexts: Map<Int, String>? = null,
    ) {
        database.withTransaction {
            if (expectedTexts != null) {
                val current = dao.allSegments(sessionId).associate { it.sequence to it.text }
                if (expectedTexts.any { (sequence, text) -> current[sequence] != text }) return@withTransaction
            }
            dao.deleteLocalRuleMarkers(sessionId)
            val existing = dao.markersBySession(sessionId).map { it.segmentId to it.type }.toSet()
            val unique = markers.filter { (it.segmentId to it.type) !in existing }
            if (unique.isNotEmpty()) dao.upsertMarkers(unique)
        }
    }

    suspend fun addMarker(marker: DitingMarkerEntity): Long = dao.upsertMarker(marker)

    /** Atomically validates source snapshots and stores one automatic event; true means a new event. */
    suspend fun storeAutomaticMarker(
        marker: DitingMarkerEntity,
        expectedTexts: Map<Int, String>,
        evidence: String? = null,
    ): Boolean = database.withTransaction {
        if (marker.source !in AUTOMATIC_SOURCES || expectedTexts.isEmpty()) return@withTransaction false
        if (dao.sessionById(marker.sessionId) == null) return@withTransaction false
        val segments = dao.allSegments(marker.sessionId).associateBy { it.sequence }
        if (expectedTexts.any { (sequence, text) -> segments[sequence]?.text != text }) return@withTransaction false
        val segment = segments.values.firstOrNull { it.id == marker.segmentId } ?: return@withTransaction false
        if (!segment.isFinal || (evidence != null && !DitingAiAnnotationParser.containsEvidence(segment.text, evidence))) {
            return@withTransaction false
        }
        val existing = dao.markersBySession(marker.sessionId).firstOrNull {
            it.segmentId == marker.segmentId && it.type == marker.type
        }
        if (existing != null) {
            if (existing.source !in AUTOMATIC_SOURCES || (existing.source == "ai" && marker.source == "local_rule")) {
                return@withTransaction false
            }
            if (existing.title == marker.title && existing.note == marker.note && existing.source == marker.source) {
                return@withTransaction false
            }
        }
        dao.upsertMarker(marker.copy(id = existing?.id ?: 0))
        existing == null
    }

    suspend fun updateSessionDetails(session: DitingSessionEntity, title: String, courseId: Long?) {
        dao.upsertSession(
            session.copy(
                title = title.trim().ifBlank { session.title },
                courseId = courseId,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateSegmentText(segment: DitingSegmentEntity, text: String) = database.withTransaction {
        val current = dao.segmentBySequence(segment.sessionId, segment.sequence) ?: return@withTransaction
        val cleaned = text.trim()
        if (current.text == cleaned) return@withTransaction
        dao.upsertSegment(current.copy(text = cleaned, isFinal = true, status = "completed", errorMessage = null))
        // Semantic batches may depend on any earlier text in that batch. Without persisted
        // dependencies, conservatively clear semantic markers for this session on an edit.
        // Local rules only depend on the preceding two segments.
        val affected = dao.allSegments(current.sessionId)
            .filter { it.sequence in current.sequence..current.sequence + 2 }.map { it.id }.toSet()
        dao.markersBySession(current.sessionId)
            .filter { it.source == "ai" || (it.segmentId in affected && it.source == "local_rule") }
            .forEach { dao.deleteMarker(it.id) }
        updateSessionMessage(current.sessionId, "文字已修改，已清理自动重点，可重新整理。手动标记和录音已保留。")
    }

    suspend fun updateMarker(marker: DitingMarkerEntity, title: String, note: String) {
        dao.upsertMarker(
            marker.copy(
                title = title.trim(),
                note = note.trim(),
                source = "manual",
            ),
        )
    }

    suspend fun deleteMarker(id: Long) = dao.deleteMarker(id)

    suspend fun deleteSession(id: Long) {
        val current = dao.sessionById(id) ?: return
        if (current.status in setOf(DitingSessionStatus.RECORDING.key, DitingSessionStatus.PAUSED.key, DitingSessionStatus.PROCESSING.key)) return
        database.withTransaction {
            dao.deleteMarkers(id)
            dao.deleteSegments(id)
            dao.deleteSession(id)
        }
        audioStore.deleteSessionFiles(id)
    }

    private companion object {
        val PENDING_TRANSCRIPTION_STATUSES = setOf("waiting_for_transcription", "transcribing")
        val AUTOMATIC_SOURCES = setOf("ai", "local_rule")
    }
}

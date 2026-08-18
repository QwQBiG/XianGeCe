package win.iqwqi.xiangece.feature.diting.data

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import win.iqwqi.xiangece.data.local.XiangeceDatabase
import win.iqwqi.xiangece.feature.diting.audio.DitingAudioStore
import win.iqwqi.xiangece.feature.diting.domain.DitingSessionStatus

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
    ): DitingSegmentEntity? {
        val current = dao.segmentBySequence(sessionId, sequence) ?: return null
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
        return updated
    }

    suspend fun markTranscriptionUnavailable(sessionId: Long, reason: String = "转写服务不可用，音频已保留") {
        dao.markTranscriptionUnavailable(sessionId, reason)
    }

    suspend fun markSegmentAudioOnly(sessionId: Long, sequence: Int, reason: String = "转写失败，音频已保留") {
        dao.markSegmentAudioOnly(sessionId, sequence, reason)
    }

    suspend fun markSegmentTranscribing(sessionId: Long, sequence: Int) {
        val current = dao.segmentBySequence(sessionId, sequence) ?: return
        dao.upsertSegment(current.copy(status = "transcribing", errorMessage = null))
    }

    suspend fun markersSnapshot(sessionId: Long): List<DitingMarkerEntity> = dao.markersBySession(sessionId)

    suspend fun deleteLocalRuleMarkers(sessionId: Long) = dao.deleteLocalRuleMarkers(sessionId)

    suspend fun replaceLocalRuleMarkers(sessionId: Long, markers: List<DitingMarkerEntity>) {
        database.withTransaction {
            dao.deleteLocalRuleMarkers(sessionId)
            if (markers.isNotEmpty()) dao.upsertMarkers(markers)
        }
    }

    suspend fun addMarker(marker: DitingMarkerEntity): Long = dao.upsertMarker(marker)

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
}

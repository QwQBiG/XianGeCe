package win.iqwqi.xiangece.feature.diting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DitingDao {
    @Query("SELECT * FROM diting_sessions ORDER BY COALESCE(startedAtEpochMillis, createdAtEpochMillis) DESC")
    fun observeSessions(): Flow<List<DitingSessionEntity>>

    @Query("SELECT * FROM diting_sessions WHERE id = :id LIMIT 1")
    fun observeSession(id: Long): Flow<DitingSessionEntity?>

    @Query("SELECT * FROM diting_sessions WHERE id = :id LIMIT 1")
    suspend fun sessionById(id: Long): DitingSessionEntity?

    @Query("SELECT * FROM diting_sessions")
    suspend fun allSessions(): List<DitingSessionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM diting_sessions WHERE status IN ('recording', 'paused', 'processing'))")
    suspend fun hasActiveSession(): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(value: DitingSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(values: List<DitingSessionEntity>)

    @Query("DELETE FROM diting_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT id FROM diting_sessions WHERE status IN ('recording', 'paused', 'processing') AND updatedAtEpochMillis < :cutoff")
    suspend fun staleActiveSessionIds(cutoff: Long): List<Long>

    @Query("UPDATE diting_sessions SET status = 'failed', errorMessage = '录音进程中断，未正常结束', updatedAtEpochMillis = :now WHERE status IN ('recording', 'paused', 'processing') AND updatedAtEpochMillis < :cutoff")
    suspend fun markActiveSessionsFailed(cutoff: Long, now: Long): Int

    @Query("UPDATE diting_sessions SET durationMillis = :durationMillis, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun updateSessionDuration(id: Long, durationMillis: Long, updatedAt: Long)

    @Query("DELETE FROM diting_sessions")
    suspend fun deleteSessionsForBackup()

    @Query("SELECT * FROM diting_segments WHERE sessionId = :sessionId ORDER BY sequence")
    fun observeSegments(sessionId: Long): Flow<List<DitingSegmentEntity>>

    @Query("SELECT * FROM diting_segments WHERE sessionId = :sessionId ORDER BY sequence")
    suspend fun allSegments(sessionId: Long): List<DitingSegmentEntity>

    @Query("SELECT * FROM diting_segments WHERE sessionId = :sessionId AND sequence = :sequence LIMIT 1")
    suspend fun segmentBySequence(sessionId: Long, sequence: Int): DitingSegmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSegment(value: DitingSegmentEntity): Long


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSegments(values: List<DitingSegmentEntity>)

    @Query("UPDATE diting_segments SET status = 'local_audio_only', errorMessage = :reason WHERE sessionId = :sessionId AND status IN ('waiting_for_transcription', 'transcribing')")
    suspend fun markTranscriptionUnavailable(sessionId: Long, reason: String)

    @Query("UPDATE diting_segments SET status = 'local_audio_only', errorMessage = :reason WHERE sessionId = :sessionId AND sequence = :sequence")
    suspend fun markSegmentAudioOnly(sessionId: Long, sequence: Int, reason: String)

    @Query("DELETE FROM diting_segments WHERE sessionId = :sessionId")
    suspend fun deleteSegments(sessionId: Long)

    @Query("DELETE FROM diting_segments")
    suspend fun deleteSegmentsForBackup()

    @Query("SELECT * FROM diting_markers WHERE sessionId = :sessionId ORDER BY positionMillis")
    fun observeMarkers(sessionId: Long): Flow<List<DitingMarkerEntity>>

    @Query("SELECT * FROM diting_markers")
    suspend fun allMarkers(): List<DitingMarkerEntity>

    @Query("SELECT * FROM diting_markers WHERE sessionId = :sessionId")
    suspend fun markersBySession(sessionId: Long): List<DitingMarkerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMarker(value: DitingMarkerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMarkers(values: List<DitingMarkerEntity>)

    @Query("DELETE FROM diting_markers WHERE id = :id")
    suspend fun deleteMarker(id: Long)

    @Query("DELETE FROM diting_markers WHERE sessionId = :sessionId AND source = 'local_rule'")
    suspend fun deleteLocalRuleMarkers(sessionId: Long)

    @Query("DELETE FROM diting_markers WHERE sessionId = :sessionId")
    suspend fun deleteMarkers(sessionId: Long)

    @Query("DELETE FROM diting_markers")
    suspend fun deleteMarkersForBackup()
}

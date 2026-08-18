package win.iqwqi.xiangece.feature.diting.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "diting_sessions",
    indices = [Index("startedAtEpochMillis"), Index("courseId")],
)
data class DitingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val courseId: Long? = null,
    val meetingId: Long? = null,
    val mode: String = "professional",
    val languageMode: String = "auto",
    val glossary: String = "",
    val status: String = "draft",
    val audioDirectory: String = "",
    val audioPath: String = "",
    val startedAtEpochMillis: Long? = null,
    val endedAtEpochMillis: Long? = null,
    val durationMillis: Long = 0,
    val audioBytes: Long = 0,
    val cloudTranscriptionEnabled: Boolean = false,
    val aiAnnotationEnabled: Boolean = false,
    val transcriptionEngine: String = "none",
    val errorMessage: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "diting_segments",
    indices = [Index("sessionId"), Index(value = ["sessionId", "sequence"], unique = true)],
)
data class DitingSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sequence: Int,
    val startMillis: Long,
    val endMillis: Long,
    val audioPath: String = "",
    val text: String = "",
    val rawText: String = "",
    val isFinal: Boolean = false,
    val confidence: Float? = null,
    val language: String = "",
    val status: String = "pending",
    val errorMessage: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "diting_markers",
    indices = [Index("sessionId"), Index("segmentId")],
)
data class DitingMarkerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val segmentId: Long? = null,
    val positionMillis: Long,
    val type: String,
    val title: String = "",
    val note: String = "",
    val confidence: Float? = null,
    val source: String = "manual",
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)





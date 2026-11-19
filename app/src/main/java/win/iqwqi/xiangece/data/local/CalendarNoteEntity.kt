package win.iqwqi.xiangece.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "calendar_notes")
data class CalendarNoteEntity(
    @PrimaryKey val dateEpochDay: Long,
    val text: String,
)

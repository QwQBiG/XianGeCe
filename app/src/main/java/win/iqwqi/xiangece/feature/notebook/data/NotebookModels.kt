package win.iqwqi.xiangece.feature.notebook.data

import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class NoteRecord(
    val id: String,
    val path: String,
    val pinned: Boolean = false,
    val deletedAt: Long? = null,
    val aliases: List<String> = emptyList(),
    val sources: List<NoteSource> = emptyList(),
    val collectionId: String? = null,
) {
    val title get() = path.substringAfterLast('/').substringBeforeLast('.')
    val folder get() = path.substringBeforeLast('/', "")
}

data class NoteDocument(val record: NoteRecord, val text: String, val revision: String, val modifiedAt: Long)
@Serializable
internal data class NotebookManifest(val version: Int = 1, val notes: List<NoteRecord> = emptyList())
@Serializable
data class NoteDraft(val noteId: String, val text: String, val baseRevision: String, val savedAt: Long)
data class NoteHistory(val name: String, val timestamp: Long, val text: String)
class NoteConflict : IllegalStateException("这篇笔记已有新的修改。你的内容已保留，请另存一份或重新打开核对。")

internal fun textRevision(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

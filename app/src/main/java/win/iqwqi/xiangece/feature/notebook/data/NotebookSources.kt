package win.iqwqi.xiangece.feature.notebook.data

import kotlinx.serialization.Serializable

@Serializable
data class NoteSourceBlock(
    val id: String,
    val text: String,
    val segmentId: Long? = null,
    val sequence: Int? = null,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val transcriptRevision: String? = null,
    val noteId: String? = null,
    val noteRevision: String? = null,
    val noteTitle: String? = null,
)

/** Immutable input snapshot. Editing a note never rewrites what its source said. */
@Serializable
data class NoteSource(
    val id: String,
    val title: String,
    val kind: String,
    val blocks: List<NoteSourceBlock>,
    val capturedAt: Long = System.currentTimeMillis(),
    val imagePath: String? = null,
    val sessionId: Long? = null,
    val sessionCreatedAt: Long? = null,
    /** Opaque immutable original; imagePath remains the display/recognition-compatible preview. */
    val originalImagePath: String? = null,
) {
    val text get() = blocks.joinToString("\n\n") { it.text }
    fun validate() {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && title.length <= 160)
        originalImagePath?.let { path ->
            require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path && ':' !in path && '\u0000' !in path && path.length <= 2048)
            require(path.split('/').all { it.isNotBlank() && it != "." && it != ".." && !it.startsWith('.') }) { "来源原件路径无效" }
        }
        require(blocks.size <= 2000 && text.length <= 100_000) { "材料较长，请分段收集" }
        require(blocks.map { it.id }.distinct().size == blocks.size)
        require(blocks.all { it.id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && it.text.length <= 4000 })
    }
}

data class SourceStatus(val source: NoteSource, val changedBlocks: Set<String>, val message: String)

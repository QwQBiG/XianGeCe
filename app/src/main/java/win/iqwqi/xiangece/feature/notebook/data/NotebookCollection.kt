package win.iqwqi.xiangece.feature.notebook.data

import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import win.iqwqi.xiangece.feature.diting.data.DitingRepository
import win.iqwqi.xiangece.feature.notebook.domain.*

@Serializable
data class CollectionDraft(
    val id: String = UUID.randomUUID().toString(),
    val source: NoteSource,
    val title: String = source.title,
    val points: List<GroundedPoint> = emptyList(),
    val selected: Set<String> = emptySet(),
)

@Singleton
class NotebookCollection @Inject constructor(val repository: NotebookRepository, val diting: DitingRepository) {
    private val json = Json { ignoreUnknownKeys = true }
    private val pending get() = File(repository.store.root, "state/collection.json")
    suspend fun restoreDraft(): CollectionDraft? = withContext(Dispatchers.IO) {
        if (!pending.isFile) null else json.decodeFromString<CollectionDraft>(pending.readText(Charsets.UTF_8))
    }
    suspend fun persist(draft: CollectionDraft?) = withContext(Dispatchers.IO) {
        if (draft == null) { if (pending.exists()) check(pending.delete()) }
        else {
            draft.source.validate()
            NotebookFileStore.atomicWrite(pending, json.encodeToString(draft).toByteArray(Charsets.UTF_8))
        }
    }
    fun fromText(text: String, title: String = "随手收集", imagePath: String? = null): CollectionDraft {
        val source = NoteSource(UUID.randomUUID().toString(), title.take(160), if (imagePath == null) "text" else "image",
            NotebookEvidence.collectText(text), imagePath = imagePath)
        return CollectionDraft(source = source, selected = defaultSelection(source))
    }
    suspend fun fromClass(id: Long): CollectionDraft {
        val session = requireNotNull(diting.sessionSnapshot(id)) { "这节课堂已不存在" }
        check(session.status !in setOf("draft", "recording", "paused", "processing")) { "请先结束录音并等待保存完成" }
        val segments = diting.segmentsSnapshot(id).filter { it.isFinal && it.text.isNotBlank() }
        check(segments.isNotEmpty()) { "这节课堂还没有可整理的转写文字" }
        val blocks = segments.flatMap { segment -> segment.text.chunked(1800).mapIndexed { index, part ->
            NoteSourceBlock("s${segment.id}-$index", part, segment.id, segment.sequence,
                segment.startMillis, segment.endMillis, textRevision(segment.text))
        } }
        val source = NoteSource(UUID.randomUUID().toString(), session.title.take(160), "class", blocks,
            sessionId = id, sessionCreatedAt = session.createdAtEpochMillis)
        source.validate()
        return CollectionDraft(source = source, selected = defaultSelection(source))
    }
    private fun defaultSelection(source: NoteSource): Set<String> {
        var length = 0
        return source.blocks.takeWhile { length += it.text.length; length <= 12_000 }.map { it.id }.toSet()
    }
    suspend fun status(source: NoteSource): SourceStatus {
        if (source.kind == "notes") {
            val changed = withContext(Dispatchers.IO) { source.blocks.filter { block ->
                val current = block.noteId?.let { runCatching { repository.store.read(it) }.getOrNull() }
                current == null || current.record.deletedAt != null || current.revision != block.noteRevision
            }.map { it.id }.toSet() }
            return SourceStatus(source, changed, if (changed.isEmpty()) "引用与当前笔记一致" else "部分原笔记已有变化或无法关联，以下保留回答时的来源快照")
        }
        if (source.kind != "class") return SourceStatus(source, emptySet(), "保留收集时的原始材料")
        val session = source.sessionId?.let { diting.sessionSnapshot(it) }
        if (session == null || session.createdAtEpochMillis != source.sessionCreatedAt) return SourceStatus(source,
            source.blocks.map { it.id }.toSet(), "原课堂不可用，已保留文字快照；录音需要在谛听中重新核对")
        val current = diting.segmentsSnapshot(session.id).associateBy { it.id }
        val changed = source.blocks.filter { block -> current[block.segmentId]?.let {
            it.isFinal && textRevision(it.text) == block.transcriptRevision
        } != true }.map { it.id }.toSet()
        return SourceStatus(source, changed, if (changed.isEmpty()) "与当前课堂文字一致；仍可回听核对识别" else "课堂文字已有变化，${changed.size} 段依据需要复核；下方保留收集时的文字")
    }
    suspend fun save(draft: CollectionDraft): NoteDocument = repository.change {
        create(draft.title, "收集", NotebookEvidence.markdown(draft.title, draft.source, draft.points),
            sources = listOf(draft.source), collectionId = draft.id)
    }
}

package win.iqwqi.xiangece.feature.notebook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import win.iqwqi.xiangece.data.settings.*
import win.iqwqi.xiangece.core.ocr.OfflineOcrService
import win.iqwqi.xiangece.feature.notebook.data.*
import win.iqwqi.xiangece.feature.notebook.domain.*

data class NotebookConsent(val kind: String, val settings: AppSettings, val question: String,
    val material: Map<String, String> = emptyMap(), val imagePath: String? = null)
data class NotebookResearchState(
    val page: String? = null, val draft: CollectionDraft? = null, val busy: Boolean = false, val committing: Boolean = false,
    val message: String? = null, val question: String = "", val expansions: List<String> = emptyList(),
    val passages: List<NotePassage> = emptyList(), val selected: Set<String> = emptySet(),
    val answer: CheckedResponse? = null, val consent: NotebookConsent? = null,
    val busyLabel: String = "正在处理", val retryKind: String? = null,
)

@HiltViewModel
class NotebookResearchModel @Inject constructor(
    val collection: NotebookCollection, val cloud: NotebookCloud,
    private val settings: AppSettingsStore, private val ocr: OfflineOcrService,
) : ViewModel() {
    private val mutable = MutableStateFlow(NotebookResearchState())
    val state = mutable.asStateFlow()
    val sessions = collection.diting.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val drafts = Channel<Unit>(Channel.CONFLATED)
    private val disk = Mutex()
    private var job: Job? = null
    private var generation = 0L
    private var networkActive = false
    private var activeNetworkKind: String? = null
    private val ready = CompletableDeferred<Unit>()
    init {
        viewModelScope.launch {
            try { mutable.update { it.copy(draft = collection.restoreDraft()) } }
            catch (e: Exception) { message("收集草稿读取失败，原文件已保留") }
            finally { ready.complete(Unit) }
            for (signal in drafts) try { persist() } catch (e: Exception) { message("草稿尚未保存，请重试") }
        }
    }
    private suspend fun persist() = disk.withLock { withContext(NonCancellable) { collection.persist(mutable.value.draft) } }
    fun message(text: String?) { mutable.update { it.copy(message = text) } }
    fun page(name: String?) { if (mutable.value.committing) return; cancel(); mutable.update { it.copy(page = name, message = null, consent = null) } }
    fun cancel() {
        if (mutable.value.committing) return
        generation++; job?.cancel(); networkActive = false; activeNetworkKind = null
        mutable.update { it.copy(busy = false, consent = null, retryKind = null) }
    }
    fun stop() {
        if (mutable.value.committing) return
        val retry = activeNetworkKind
        cancel()
        mutable.update { it.copy(message = "已停止处理，材料和编辑内容已保留。", retryKind = retry) }
    }
    fun stopNetwork() { if (networkActive) stop() }
    fun retry() { mutable.value.retryKind?.let(::prepare) }
    private fun run(label: String = "正在处理", retryKind: String? = null, block: suspend () -> Unit) {
        if (mutable.value.busy) return
        val operation = ++generation
        mutable.update { it.copy(busy = true, message = null, busyLabel = label, retryKind = null) }
        job = viewModelScope.launch {
            try { ready.await(); block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == operation) mutable.update { it.copy(retryKind = retryKind,
                    message = if (e is kotlinx.serialization.SerializationException) "服务返回的内容格式不正确，材料已保留。可以重试或继续本机操作。"
                        else e.message ?: "暂时无法完成，材料已保留") }
            }
            finally { if (generation == operation) { networkActive = false; activeNetworkKind = null; mutable.update { it.copy(busy = false, committing = false) } } }
        }
    }
    fun editDraft(transform: (CollectionDraft) -> CollectionDraft) {
        if (mutable.value.busy) return
        mutable.value.draft?.let { draft -> mutable.update { it.copy(draft = transform(draft), message = null, consent = null, retryKind = null) }; drafts.trySend(Unit) }
    }
    fun beginText(text: String = "", title: String = "随手收集", imagePath: String? = null) = run {
        mutable.update { it.copy(page = "collect") }
        check(mutable.value.draft == null) { "已有未完成的收集，请先保存或放弃，再收集新材料" }
        mutable.update { it.copy(page = "collect", draft = collection.fromText(text, title, imagePath)) }; persist()
    }
    fun beginClass(id: Long) = run {
        mutable.update { it.copy(page = "collect") }
        check(mutable.value.draft == null) { "已有未完成的收集，请先保存或放弃" }
        val draft = collection.fromClass(id)
        mutable.update { it.copy(page = "collect", draft = draft) }; persist()
    }
    fun discard() = run { mutable.update { it.copy(draft = null, page = null) }; persist() }
    fun save(onSaved: (String) -> Unit) = run("正在保存笔记") {
        val draft = requireNotNull(mutable.value.draft)
        require(draft.source.blocks.any { it.text.isNotBlank() } || draft.source.imagePath != null) { "请先写入或选择材料" }
        persist()
        mutable.update { it.copy(committing = true) }
        withContext(NonCancellable) {
            val note = collection.save(draft)
            mutable.update { it.copy(draft = null, page = null) }; persist(); onSaved(note.record.id)
        }
    }
    fun localOcr() = run("正在本机识别图片") {
        val draft = requireNotNull(mutable.value.draft)
        val file = requireNotNull(draft.source.imagePath?.let(collection.repository.store::attachment)) { "原图不可用" }
        val text = withContext(Dispatchers.IO) { ocr.recognize(file).page.text }
        replaceText(draft, text); message("识别完成，请对照原图核对文字")
    }
    private suspend fun replaceText(draft: CollectionDraft, text: String) {
        val source = draft.source.copy(blocks = NotebookEvidence.collectText(text))
        mutable.update { it.copy(draft = draft.copy(source = source, points = emptyList(), selected = source.blocks.take(6).map { b -> b.id }.toSet())) }
        persist()
    }
    fun question(text: String) { if (!mutable.value.busy) mutable.update { it.copy(question = text.take(800), answer = null, consent = null, retryKind = null) } }
    fun selectPassage(id: String) { if (!mutable.value.busy) mutable.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id, answer = null, consent = null, retryKind = null) } }
    fun findLocal() = run("正在查找笔记") { search(emptyList()) }
    private suspend fun search(expansions: List<String>) {
        collection.repository.refresh()
        val matches = withContext(Dispatchers.Default) { NotebookEvidence.passages(collection.repository.notes.value.filter { it.record.deletedAt == null }, mutable.value.question, expansions) }
        mutable.update { it.copy(expansions = expansions, passages = matches, selected = matches.take(4).map { p -> p.id }.toSet(), answer = null) }
        if (matches.isEmpty()) message("没有找到相关片段，可以换个描述，或从下面选择笔记")
    }
    fun includeNote(note: NoteDocument) {
        if (mutable.value.busy) return
        val passages = note.text.chunked(1200).mapIndexed { i, text -> NotePassage("${note.record.id}-$i", note.record.id, note.record.title, note.revision, i * 1200, text) }
        mutable.update { it.copy(passages = (it.passages + passages).distinctBy { p -> p.id }, answer = null, consent = null, retryKind = null) }
    }
    fun keepAnswer() = run {
        check(mutable.value.draft == null) { "请先完成或放弃当前收集，再保存回答" }
        val state = mutable.value
        val answer = requireNotNull(state.answer)
        check(answer.points.isNotEmpty())
        collection.repository.refresh()
        val used = answer.points.flatMap { it.evidence }.map { it.id }.toSet()
        val passages = state.passages.filter { it.id in used }
        check(passages.all { p -> NotebookEvidence.stillCurrent(p, collection.repository.notes.value.firstOrNull { it.record.id == p.noteId }) }) {
            "来源已有修改，请重新查找核对后保存"
        }
        val source = NoteSource(java.util.UUID.randomUUID().toString(), "回答所依据的笔记", "notes", passages.map { p ->
            NoteSourceBlock(p.id, "${p.title}\n${p.text}", noteId = p.noteId, noteRevision = p.revision, noteTitle = p.title)
        })
        val title = state.question.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").trim().take(60).ifBlank { "资料整理" }
        mutable.update { it.copy(page = "collect", draft = CollectionDraft(source = source, title = title, points = answer.points)) }
        persist()
    }
    fun prepare(kind: String) = run("正在准备发送内容") {
        val config = settings.settings.first()
        check(cloud.configured(config)) { "请先在我的 → 智能服务中配置并开启联网服务；本地收集与搜索仍可使用" }
        val state = mutable.value
        val material = when (kind) {
            "organize" -> requireNotNull(state.draft).source.blocks.filter { it.id in state.draft.selected }.associate { it.id to it.text }
            "answer" -> state.passages.filter { it.id in state.selected }.associate { it.id to "${it.title}\n${it.text}" }
            else -> emptyMap()
        }
        if (kind in setOf("organize", "answer")) {
            require(material.isNotEmpty()) { "请先选择要使用的片段" }
            require(material.size <= 30 && material.values.sumOf { it.length } <= 16_000) { "本次材料较多，请减少勾选的片段（最多约1.6万字）" }
        }
        if (kind in setOf("expand", "answer")) require(state.question.isNotBlank()) { "先写下想找什么" }
        if (kind == "image") check(config.aiSupportsVision && config.aiVisionModel.isNotBlank()) { "请先配置支持图片的服务" }
        val question = if (kind == "organize") "将所选材料整理为清楚的笔记要点，保留原意和重要条件，不加入资料以外的解释。" else state.question
        mutable.update { it.copy(consent = NotebookConsent(kind, config, question, material,
            state.draft?.source?.imagePath.takeIf { kind == "image" })) }
    }
    fun dismissConsent() { mutable.update { it.copy(consent = null) } }
    fun confirm() {
        val consent = mutable.value.consent ?: return
        mutable.update { it.copy(consent = null) }
        val label = when (consent.kind) {
            "expand" -> "正在理解描述并查找笔记"
            "image" -> "正在识别图片文字"
            "organize" -> "正在整理所选内容"
            else -> "正在根据资料回答"
        }
        run(label, consent.kind) {
            networkActive = true
            activeNetworkKind = consent.kind
            check(cloud.sameService(consent.settings, settings.settings.first())) { "服务配置已变化，请重新确认" }
            when (consent.kind) {
                "expand" -> search(cloud.expand(consent.question, consent.settings))
                "image" -> {
                    val draft = requireNotNull(mutable.value.draft)
                    check(draft.source.imagePath == consent.imagePath)
                    val file = requireNotNull(consent.imagePath?.let(collection.repository.store::attachment))
                    replaceText(draft, cloud.readImage(file, consent.settings)); message("识别完成，请核对原图")
                }
                "organize" -> {
                    val draft = requireNotNull(mutable.value.draft)
                    val result = cloud.grounded(consent.question, consent.material, consent.settings)
                    mutable.update { it.copy(draft = draft.copy(points = result.points)) }; persist()
                    val status = collection.status(draft.source)
                    message(listOfNotNull(result.missing.takeIf { it.isNotBlank() },
                        "${result.points.size} 个要点可核对；${result.rejected} 个无有效引用的要点未采用",
                        status.message.takeIf { status.changedBlocks.isNotEmpty() }).joinToString("\n"))
                }
                "answer" -> {
                    collection.repository.refresh()
                    val supplied = mutable.value.passages.filter { it.id in mutable.value.selected }
                    fun current() = supplied.all { passage -> NotebookEvidence.stillCurrent(passage,
                        collection.repository.notes.value.firstOrNull { it.record.id == passage.noteId }) }
                    check(current()) { "所选笔记已有修改，请重新查找后再提问" }
                    val answer = cloud.grounded(consent.question, consent.material, consent.settings)
                    collection.repository.refresh()
                    check(current()) { "回答期间笔记发生变化，请重新查找核对" }
                    mutable.update { it.copy(answer = answer) }
                    if (answer.points.isEmpty()) message("没有得到有有效引用的答案。可以补充材料后重试。")
                }
            }
        }
    }
}

package win.iqwqi.xiangece.feature.notebook.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import win.iqwqi.xiangece.feature.notebook.data.*
import win.iqwqi.xiangece.feature.notebook.domain.*
import win.iqwqi.xiangece.data.settings.AppSettingsStore

data class NoteEditorState(
    val document: NoteDocument? = null,
    val text: String = "",
    val saving: Boolean = false,
    val message: String? = null,
    val conflict: Boolean = false,
)

@HiltViewModel
class NotebookViewModel @Inject constructor(
    val repository: NotebookRepository,
    val settingsStore: AppSettingsStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val notes = repository.notes
    private val _editor = MutableStateFlow(NoteEditorState())
    val editor = _editor.asStateFlow()
    private val saveSignals = Channel<Unit>(Channel.CONFLATED)
    private val saveMutex = Mutex()
    val notices = Channel<String>(Channel.BUFFERED)

    init {
        viewModelScope.launch { runCatching { repository.refresh(); savedState.get<String>("noteId")?.let { load(it) } }.onFailure(::report) }
        viewModelScope.launch { for (signal in saveSignals) { delay(450); flush() } }
    }
    fun edit(text: String) {
        _editor.update { it.copy(text = text, message = null) }
        saveSignals.trySend(Unit)
    }
    fun saveSoon() { viewModelScope.launch { flush() } }
    suspend fun flush(): Boolean = saveMutex.withLock {
        val state = _editor.value
        val doc = state.document ?: return@withLock true
        if (state.conflict) return@withLock false
        if (state.text == doc.text) return@withLock true
        _editor.update { it.copy(saving = true) }
        withContext(NonCancellable) {
            runCatching {
                repository.change {
                    writeDraft(NoteDraft(doc.record.id, state.text, doc.revision, System.currentTimeMillis()))
                    save(doc.record.id, state.text, doc.revision)
                }
            }.fold(onSuccess = { saved ->
                _editor.update { it.copy(document = saved, saving = false, message = null, conflict = false) }
                true
            }, onFailure = { error ->
                _editor.update { it.copy(saving = false, message = error.message ?: "保存失败，请重试", conflict = error is NoteConflict) }
                false
            })
        }
    }
    private suspend fun load(id: String) {
        val (doc, draft) = withContext(Dispatchers.IO) { repository.store.read(id) to repository.store.draft(id) }
        _editor.value = NoteEditorState(doc, draft?.text ?: doc.text,
            message = if (draft != null && draft.text != doc.text) "已找回未完成的编辑" else null,
            conflict = draft != null && draft.baseRevision != doc.revision && draft.text != doc.text)
        savedState["noteId"] = id
    }
    fun open(id: String) = action { if (flush()) load(id) }
    fun close(onClosed: () -> Unit = {}) = action {
        if (flush()) { _editor.value = NoteEditorState(); savedState["noteId"] = null; onClosed() }
    }
    fun create(title: String, folder: String, text: String = "") = action {
        if (flush()) load(repository.change { create(title, folder, text) }.record.id)
    }
    fun rename(title: String, folder: String) = action {
        if (flush()) _editor.value.document?.let { doc -> load(repository.change { rename(doc.record.id, title, folder) }.record.id) }
    }
    fun pin(id: String) = action { repository.change { pin(id) } }
    fun trash(id: String) = action {
        if (flush()) { repository.change { trash(id) }; _editor.value = NoteEditorState(); savedState["noteId"] = null }
    }
    fun restore(id: String) = action { load(repository.change { restore(id) }.record.id) }
    fun createFolder(name: String) = action { repository.change { createFolder(name) }; notices.send("文件夹已创建") }
    fun saveCopy() = action {
        val state = _editor.value
        val note = state.document ?: return@action
        val copy = repository.change { create(note.record.title + " 副本", note.record.folder, state.text) }
        load(copy.record.id)
    }
    fun action(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { report(e) } } }
    private fun report(error: Throwable) { notices.trySend(error.message ?: "暂时无法完成，请重试") }
}

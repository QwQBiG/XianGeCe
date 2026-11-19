package win.iqwqi.xiangece.feature.notebook.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class NotebookRepository @Inject constructor(@ApplicationContext context: Context) {
    val store by lazy { NotebookFileStore(File(context.filesDir, "notebook")) }
    private val _notes = MutableStateFlow<List<NoteDocument>>(emptyList())
    val notes = _notes.asStateFlow()
    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders = _folders.asStateFlow()
    suspend fun refresh() = withContext(Dispatchers.IO) { _notes.value = store.list(true); _folders.value = store.folders() }
    suspend fun <T> change(action: NotebookFileStore.() -> T): T = withContext(Dispatchers.IO) {
        // Keep draft creation and revision-checked save in one critical section.
        val result = synchronized(store) { store.action() }
        _notes.value = store.list(true)
        _folders.value = store.folders()
        result
    }
}

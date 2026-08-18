package win.iqwqi.xiangece.feature.diting.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import win.iqwqi.xiangece.feature.diting.data.DitingRepository
import win.iqwqi.xiangece.feature.diting.data.DitingMarkerEntity
import win.iqwqi.xiangece.feature.diting.data.DitingSegmentEntity
import win.iqwqi.xiangece.feature.diting.data.DitingSessionEntity
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflineTranscriber
import win.iqwqi.xiangece.feature.diting.transcription.DitingTranscriptionCoordinator

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DitingViewModel @Inject constructor(
    private val repository: DitingRepository,
    private val offlineTranscriber: DitingOfflineTranscriber,
    private val transcriptionCoordinator: DitingTranscriptionCoordinator,
) : ViewModel() {
    private val createMutex = Mutex()
    private val _creatingSession = MutableStateFlow(false)
    val creatingSession: StateFlow<Boolean> = _creatingSession.asStateFlow()

    val sessions: StateFlow<List<DitingSessionEntity>> = repository.sessions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val selectedSessionId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            repository.recoverInterruptedSessions()
            selectedSessionId.value = repository.sessions.first()
                .firstOrNull { it.status in ACTIVE_STATUSES }
                ?.id
        }
    }
    val selectedSession: StateFlow<DitingSessionEntity?> = selectedSessionId
        .flatMapLatest { id -> id?.let(repository::session) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedSegments: StateFlow<List<DitingSegmentEntity>> = selectedSessionId
        .flatMapLatest { id -> id?.let(repository::segments) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedMarkers: StateFlow<List<DitingMarkerEntity>> = selectedSessionId
        .flatMapLatest { id -> id?.let(repository::markers) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val retryMutex = Mutex()
    private val _retryingOfflineSessionId = MutableStateFlow<Long?>(null)

    private val reanalyzeMutex = Mutex()
    private val _reanalyzingSessionId = MutableStateFlow<Long?>(null)
    val reanalyzingSessionId: StateFlow<Long?> = _reanalyzingSessionId

    val retryingOfflineSessionId: StateFlow<Long?> = _retryingOfflineSessionId

    fun retryOfflineTranscription(sessionId: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val acquired = retryMutex.withLock {
                if (_retryingOfflineSessionId.value != null) false
                else {
                    _retryingOfflineSessionId.value = sessionId
                    true
                }
            }
            if (!acquired) return@launch
            try {
                val session = repository.sessionSnapshot(sessionId) ?: return@launch
                val candidates = repository.segmentsSnapshot(sessionId)
                    .filter { it.text.isBlank() && File(it.audioPath).isFile }
                if (candidates.isEmpty()) {
                    repository.updateSessionMessage(sessionId, "没有可重新转写的音频分段")
                    return@launch
                }
                val prepared = offlineTranscriber.prepare()
                if (prepared.isFailure) {
                    repository.updateSessionMessage(sessionId, "文字暂时无法重新识别，请确认离线资源可用后再试。")
                    return@launch
                }
                var completed = 0
                candidates.forEach { segment ->
                    repository.markSegmentTranscribing(sessionId, segment.sequence)
                    val result = offlineTranscriber.transcribe(File(segment.audioPath), session.glossary)
                    if (result.isSuccess) {
                        val transcript = result.getOrThrow()
                        transcriptionCoordinator.onTranscriptReady(
                            sessionId = sessionId,
                            sequence = segment.sequence,
                            text = transcript.text,
                            rawText = transcript.text,
                            language = transcript.language,
                            confidence = transcript.confidence,
                        )
                        completed += 1
                    } else {
                        repository.markSegmentAudioOnly(sessionId, segment.sequence, "这一段文字暂未识别出来，音频仍可回听。")
                    }
                }
                runCatching { transcriptionCoordinator.flushAi(sessionId) }
                repository.updateSessionMessage(
                    sessionId,
                    when {
                        completed == candidates.size -> null
                        completed > 0 -> "已重新转写 $completed 段，${candidates.size - completed} 段仍未完成"
                        else -> "部分文字暂未识别出来，音频仍可回听；可以稍后重试。"
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                repository.updateSessionMessage(sessionId, "重新识别暂时没有完成，原有录音不会受影响。")
            } finally {
                retryMutex.withLock { _retryingOfflineSessionId.value = null }
            }
        }
    }

    fun reanalyzeSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val acquired = reanalyzeMutex.withLock {
                if (_reanalyzingSessionId.value != null) false
                else {
                    _reanalyzingSessionId.value = sessionId
                    true
                }
            }
            if (!acquired) return@launch
            try {
                transcriptionCoordinator.reanalyzeLocalSignals(sessionId)
                repository.updateSessionMessage(sessionId, null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                repository.updateSessionMessage(sessionId, "重点和提问暂时没有重新整理完成，请稍后再试。")
            } finally {
                reanalyzeMutex.withLock { _reanalyzingSessionId.value = null }
            }
        }
    }

    fun createSession(
        title: String,
        mode: String,
        languageMode: String,
        glossary: String,
        cloudTranscriptionEnabled: Boolean,
        aiAnnotationEnabled: Boolean,
        onCreated: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            createMutex.withLock {
                if (_creatingSession.value) return@withLock
                _creatingSession.value = true
                try {
                    if (repository.hasActiveSession()) {
                        selectedSessionId.value = repository.sessions.first()
                            .firstOrNull { it.status in ACTIVE_STATUSES }
                            ?.id
                        return@withLock
                    }
                    val id = repository.createSession(title, mode = mode, languageMode = languageMode, glossary = glossary, cloudTranscriptionEnabled = cloudTranscriptionEnabled, aiAnnotationEnabled = aiAnnotationEnabled)
                    selectedSessionId.value = id
                    onCreated(id)
                } finally {
                    _creatingSession.value = false
                }
            }
        }
    }

    fun selectSession(id: Long) {
        selectedSessionId.value = id
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { repository.deleteSession(id) }
    }

    private companion object {
        val ACTIVE_STATUSES = setOf("recording", "paused", "processing")
    }
}

package win.iqwqi.xiangece.feature.diting.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
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
import win.iqwqi.xiangece.feature.diting.transcription.DitingCloudTranscriber
import win.iqwqi.xiangece.feature.diting.transcription.DitingCloudTranscript
import win.iqwqi.xiangece.feature.diting.transcription.transcribeCloudFirst

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DitingViewModel @Inject constructor(
    private val repository: DitingRepository,
    private val offlineTranscriber: DitingOfflineTranscriber,
    private val transcriptionCoordinator: DitingTranscriptionCoordinator,
    private val cloudTranscriber: DitingCloudTranscriber,
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
            val activeId = repository.sessions.first().firstOrNull { it.status in ACTIVE_STATUSES }?.id
            selectedSessionId.update { existing -> existing ?: activeId }
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
            var processingStarted = false
            try {
                val session = repository.sessionSnapshot(sessionId) ?: return@launch
                if (repository.hasActiveSession()) {
                    repository.updateSessionMessage(sessionId, "请在录音结束后重新识别，避免与正在录音的课堂争用资源。")
                    return@launch
                }
                val candidates = repository.segmentsSnapshot(sessionId)
                    .filter { it.text.isBlank() && File(it.audioPath).isFile }
                if (candidates.isEmpty()) {
                    repository.updateSessionMessage(sessionId, "没有可重新转写的音频分段")
                    return@launch
                }
                val cloudAllowed = session.cloudTranscriptionEnabled && cloudTranscriber.isConfigured()
                if (!cloudAllowed && !offlineTranscriber.isConfigured()) {
                    repository.updateSessionMessage(sessionId, "请配置云端转写或安装本机资源后再试。")
                    return@launch
                }
                var completed = 0
                processingStarted = true
                candidates.forEach { segment ->
                    if (repository.findSegment(sessionId, segment.sequence)?.text?.isNotBlank() != false) return@forEach
                    repository.markSegmentTranscribing(sessionId, segment.sequence)
                    val result = transcribeCloudFirst(
                        isSessionValid = { repository.sessionSnapshot(sessionId) != null && !repository.hasActiveSession() },
                        cloudConfigured = { cloudAllowed && cloudTranscriber.isConfigured() },
                        cloud = { cloudTranscriber.transcribe(File(segment.audioPath), session.languageMode, session.glossary) },
                        localConfigured = { offlineTranscriber.isConfigured() },
                        local = { offlineTranscriber.transcribe(File(segment.audioPath), session.glossary)
                            .map { DitingCloudTranscript(it.text, it.language, it.confidence) } },
                    )
                    if (result.isSuccess) {
                        val transcript = result.getOrThrow().value
                        repository.updateTranscriptionEngine(sessionId, result.getOrThrow().engine)
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
                repository.updateSessionMessage(sessionId, "重新识别暂时没有完成，录音已保留，可稍后重试。")
            } finally {
                withContext(NonCancellable) {
                    if (processingStarted && repository.sessionSnapshot(sessionId)?.status !in ACTIVE_STATUSES) {
                        repository.markTranscriptionUnavailable(sessionId, "识别已暂停，原音仍可回听或重试。")
                        transcriptionCoordinator.clearSession(sessionId)
                        offlineTranscriber.release()
                    }
                    retryMutex.withLock { _retryingOfflineSessionId.value = null }
                }
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
                _retryingOfflineSessionId.value?.let {
                    repository.updateSessionMessage(it, "请等待本次文字整理结束后再开始新的录音。")
                    return@withLock
                }
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

    fun updateSessionDetails(session: DitingSessionEntity, title: String, courseId: Long?) {
        viewModelScope.launch {
            repository.updateSessionDetails(session, title, courseId)
        }
    }

    fun updateSegmentText(segment: DitingSegmentEntity, text: String) {
        viewModelScope.launch { repository.updateSegmentText(segment, text) }
    }

    fun updateMarker(marker: DitingMarkerEntity, title: String, note: String) {
        viewModelScope.launch { repository.updateMarker(marker, title, note) }
    }

    fun deleteMarker(id: Long) {
        viewModelScope.launch { repository.deleteMarker(id) }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { repository.deleteSession(id) }
    }

    private companion object {
        val ACTIVE_STATUSES = setOf("recording", "paused", "processing")
    }
}

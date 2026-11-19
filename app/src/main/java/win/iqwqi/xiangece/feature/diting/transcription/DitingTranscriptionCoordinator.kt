package win.iqwqi.xiangece.feature.diting.transcription

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import win.iqwqi.xiangece.feature.diting.audio.DitingAudioChunk
import win.iqwqi.xiangece.feature.diting.data.DitingMarkerEntity
import win.iqwqi.xiangece.feature.diting.data.DitingRepository
import win.iqwqi.xiangece.feature.diting.data.DitingSegmentEntity
import win.iqwqi.xiangece.feature.diting.data.DitingSessionEntity
import win.iqwqi.xiangece.feature.diting.domain.DitingMarkerType

/** Binds asynchronous recognizer results to the ordered local audio chunks. */
data class DitingAsyncSignalBatch(
    val sessionId: Long,
    val signals: List<DitingSignal>,
)
@Singleton
class DitingTranscriptionCoordinator @Inject constructor(
    private val repository: DitingRepository,
    private val signalAnalyzer: DitingSignalAnalyzer,
    private val aiAnnotator: DitingAiAnnotator? = null,
) {
    private data class TranscriptEvent(
        val text: String,
        val rawText: String,
        val language: String,
        val confidence: Float?,
    )

    private val _asyncSignals = MutableSharedFlow<DitingAsyncSignalBatch>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val asyncSignals: SharedFlow<DitingAsyncSignalBatch> = _asyncSignals.asSharedFlow()
    private val pendingMutex = Mutex()
    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private class AiWorker(val session: DitingSessionEntity, firstSequence: Int) {
        val wake = Channel<Unit>(Channel.CONFLATED)
        var nextSequence = firstSequence
        @Volatile var latestSequence = firstSequence
        var job: Job? = null
    }
    private val aiWorkers = mutableMapOf<Long, AiWorker>()
    private val aiPersistenceMutex = Mutex()
    private val pendingSequences = mutableMapOf<Long, ArrayDeque<Int>>()
    private val pendingEvents = mutableMapOf<Long, ArrayDeque<TranscriptEvent>>()
    private val pendingPartials = mutableMapOf<Long, String>()

    suspend fun onChunkReady(
        sessionId: Long,
        chunk: DitingAudioChunk,
        languageMode: String,
        cloudTranscriptionEnabled: Boolean,
        transcriptionEnabled: Boolean = cloudTranscriptionEnabled,
        expectsRecognizerEvents: Boolean = true,
    ): List<DitingSignal> {
        val previous = repository.findSegment(sessionId, chunk.sequence)
        repository.upsertSegment(
            DitingSegmentEntity(
                id = previous?.id ?: 0,
                sessionId = sessionId,
                sequence = chunk.sequence,
                startMillis = chunk.startMillis,
                endMillis = chunk.endMillis,
                audioPath = chunk.file.absolutePath,
                status = if (transcriptionEnabled) {
                    "waiting_for_transcription"
                } else {
                    "local_audio_only"
                },
                language = languageMode,
            ),
        )
        if (!transcriptionEnabled || !expectsRecognizerEvents) return emptyList()

        val eventAndPartial = pendingMutex.withLock {
            val queue = pendingSequences.getOrPut(sessionId) { ArrayDeque() }
            queue.addLast(chunk.sequence)
            val event = pendingEvents[sessionId]?.removeFirstOrNull()
            if (event != null) queue.removeFirstOrNull()
            val partial = pendingPartials.remove(sessionId)
            event to partial
        }
        val signals = eventAndPartial.first?.let { event ->
            applyTranscript(sessionId, chunk.sequence, event, final = true)
        }.orEmpty()
        if (eventAndPartial.first == null && !eventAndPartial.second.isNullOrBlank()) {
            repository.updateSegmentTranscript(
                sessionId = sessionId,
                sequence = chunk.sequence,
                text = eventAndPartial.second.orEmpty(),
                rawText = eventAndPartial.second.orEmpty(),
                isFinal = false,
                confidence = null,
                language = "",
                status = "transcribing",
            )
        }
        return signals
    }

    suspend fun onTranscriptPartial(sessionId: Long, text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        val sequence = pendingMutex.withLock {
            pendingSequences[sessionId]?.firstOrNull()
                ?: run {
                    pendingPartials[sessionId] = cleanText
                    null
                }
        } ?: return
        repository.updateSegmentTranscript(
            sessionId = sessionId,
            sequence = sequence,
            text = cleanText,
            rawText = cleanText,
            isFinal = false,
            confidence = null,
            language = "",
            status = "transcribing",
        )
    }

    suspend fun onRecognizerTranscript(
        sessionId: Long,
        text: String,
        rawText: String = text,
        language: String = "",
        confidence: Float? = null,
    ): List<DitingSignal> {
        val event = TranscriptEvent(text.trim(), rawText, language, confidence)
        if (event.text.isBlank()) return emptyList()
        val sequence = pendingMutex.withLock {
            pendingPartials.remove(sessionId)
            pendingSequences[sessionId]?.removeFirstOrNull()
                ?: run {
                    pendingEvents.getOrPut(sessionId) { ArrayDeque() }.addLast(event)
                    null
                }
        } ?: return emptyList()
        return applyTranscript(sessionId, sequence, event, final = true)
    }

    suspend fun onTranscriptReady(
        sessionId: Long,
        sequence: Int,
        text: String,
        rawText: String = text,
        language: String = "",
        confidence: Float? = null,
    ): List<DitingSignal> {
        val event = TranscriptEvent(text.trim(), rawText, language, confidence)
        if (event.text.isBlank()) return emptyList()
        pendingMutex.withLock {
            pendingPartials.remove(sessionId)
            // 离线/云端结果带有明确序号，不再让该分段残留在实时识别队列中。
            pendingSequences[sessionId]?.remove(sequence)
        }
        return applyTranscript(sessionId, sequence, event, final = true)
    }

    private suspend fun applyTranscript(
        sessionId: Long,
        sequence: Int,
        event: TranscriptEvent,
        final: Boolean,
    ): List<DitingSignal> {
        val segment = repository.updateSegmentTranscript(
            sessionId = sessionId,
            sequence = sequence,
            text = event.text,
            rawText = event.rawText,
            isFinal = final,
            confidence = event.confidence,
            language = event.language,
            status = if (final) "completed" else "transcribing",
        ) ?: return emptyList()
        if (!final) return emptyList()
        val session = repository.sessionSnapshot(sessionId) ?: return emptyList()

        val previousSegments = precedingSegments(sessionId, segment)
        val previousText = previousSegments.joinToString(" ") { it.text }
        val signals = signalAnalyzer.analyze(segment.text, session.mode, previousText)
        val existingMarkerTypes = repository.markersSnapshot(sessionId)
            .asSequence()
            .filter { it.segmentId == segment.id }
            .map { it.type }
            .toSet()
        signals.forEach { signal ->
            if (signal.type.key in existingMarkerTypes) return@forEach
            repository.storeAutomaticMarker(
                DitingMarkerEntity(
                    sessionId = sessionId,
                    segmentId = segment.id,
                    positionMillis = segment.endMillis,
                    type = signal.type.key,
                    title = signal.title,
                    note = markerNote(segment.text, signal.type, previousText),
                    confidence = signal.confidence,
                    source = "local_rule",
                ),
                expectedTexts = (previousSegments + segment).associate { it.sequence to it.text },
            )
        }

        scheduleAiAnnotation(session, segment)
        return signals

    }

    private suspend fun precedingSegments(sessionId: Long, segment: DitingSegmentEntity): List<DitingSegmentEntity> {
        if (segment.sequence <= 0) return emptyList()
        // Keep original segment identities so edits can invalidate context-dependent results.
        return (2 downTo 1)
            .mapNotNull { offset ->
                val sequence = segment.sequence - offset
                if (sequence < 0) null else repository.findSegment(sessionId, sequence)?.takeIf { it.isFinal && it.text.isNotBlank() }
            }
    }

    private fun markerNote(currentText: String, type: win.iqwqi.xiangece.feature.diting.domain.DitingMarkerType, previousText: String): String {
        if (type != win.iqwqi.xiangece.feature.diting.domain.DitingMarkerType.AUTO_QUESTION || previousText.isBlank()) {
            return currentText.take(240)
        }
        val context = previousText.takeLast(180)
        return "上下文：$context\n提问内容：${currentText.take(240)}"
    }

    private fun scheduleAiAnnotation(session: DitingSessionEntity, segment: DitingSegmentEntity) {
        if (!session.aiAnnotationEnabled || aiAnnotator == null) return
        synchronized(aiWorkers) {
            val worker = aiWorkers.getOrPut(session.id) {
                AiWorker(session, segment.sequence).also { value ->
                    value.job = aiScope.launch { runAiWorker(value) }
                }
            }
            worker.latestSequence = maxOf(worker.latestSequence, segment.sequence)
            worker.wake.trySend(Unit)
        }
    }

    private suspend fun runAiWorker(worker: AiWorker) {
        val annotator = aiAnnotator ?: return
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            // A quiet classroom still flushes the pending semantic batch within a bounded wait.
            val wake = withTimeoutOrNull(35_000L) { worker.wake.receiveCatching() }
            while (worker.nextSequence <= worker.latestSequence) {
                val sequence = worker.nextSequence++
                val segment = repository.findSegment(worker.session.id, sequence) ?: continue
                if (!segment.isFinal || segment.text.isBlank()) continue
                val annotations = safelyAnnotate {
                    annotator.onTranscript(worker.session, segment, precedingSegments(worker.session.id, segment))
                }
                publishAiAnnotations(worker, annotations)
            }
            if (wake == null || wake.isClosed) {
                publishAiAnnotations(worker, safelyAnnotate { annotator.flush(worker.session) })
            }
            if (wake?.isClosed == true) break
        }
    }

    private suspend fun safelyAnnotate(block: suspend () -> List<DitingAiAnnotation>): List<DitingAiAnnotation> =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }

    private suspend fun publishAiAnnotations(worker: AiWorker, annotations: List<DitingAiAnnotation>) {
        val signals = persistAiAnnotations(worker, annotations)
        if (signals.isNotEmpty()) _asyncSignals.emit(DitingAsyncSignalBatch(worker.session.id, signals))
    }
    private suspend fun persistAiAnnotations(
        worker: AiWorker,
        annotations: List<DitingAiAnnotation>,
    ): List<DitingSignal> = aiPersistenceMutex.withLock {
        if (annotations.isEmpty()) return emptyList()
        val sessionId = worker.session.id
        if (synchronized(aiWorkers) { aiWorkers[sessionId] !== worker } || worker.job?.isActive != true) return emptyList()
        if (repository.sessionSnapshot(sessionId)?.aiAnnotationEnabled != true) return emptyList()
        return annotations.mapNotNull { annotation ->
            val segment = repository.findSegment(sessionId, annotation.sequence) ?: return@mapNotNull null
            val note = buildString {
                if (annotation.note.isNotBlank()) append(annotation.note)
                if (annotation.evidence.isNotBlank()) {
                    if (isNotEmpty()) append("；")
                    append("原文：").append(annotation.evidence)
                }
            }
            val marker = DitingMarkerEntity(
                sessionId = sessionId,
                segmentId = segment.id,
                positionMillis = segment.endMillis,
                type = annotation.type.key,
                title = annotation.title,
                note = note,
                confidence = annotation.confidence,
                source = "ai",
            )
            val inserted = repository.storeAutomaticMarker(marker, annotation.expectedTexts, annotation.evidence)
            if (inserted) DitingSignal(annotation.type, annotation.title, annotation.confidence) else null
        }
    }

    suspend fun flushAi(sessionId: Long): List<DitingSignal> {
        val worker = synchronized(aiWorkers) { aiWorkers[sessionId] } ?: return emptyList()
        worker.wake.close()
        val completed = withTimeoutOrNull(AI_FLUSH_TIMEOUT_MILLIS) { worker.job?.join(); true } ?: false
        if (!completed) worker.job?.cancel()
        aiPersistenceMutex.withLock {
            synchronized(aiWorkers) {
                if (aiWorkers[sessionId] === worker) aiWorkers.remove(sessionId)
            }
            aiAnnotator?.clear(sessionId)
        }
        return emptyList() // New events are emitted once by the worker.
    }

    /** Rebuilds only local-rule markers from saved text; manual and AI markers remain untouched. */
    suspend fun reanalyzeLocalSignals(sessionId: Long): List<DitingSignal> {
        val session = repository.sessionSnapshot(sessionId) ?: return emptyList()
        val segments = repository.segmentsSnapshot(sessionId)
        val markers = mutableListOf<DitingMarkerEntity>()
        val signals = mutableListOf<DitingSignal>()
        segments.filter { it.text.isNotBlank() }.forEach { segment ->
            val previousText = segments.filter { it.sequence in segment.sequence - 2 until segment.sequence }
                .joinToString(" ") { it.text }
            signalAnalyzer.analyze(segment.text, session.mode, previousText).forEach { signal ->
                markers += DitingMarkerEntity(
                    sessionId = sessionId,
                    segmentId = segment.id,
                    positionMillis = segment.endMillis,
                    type = signal.type.key,
                    title = signal.title,
                    note = markerNote(segment.text, signal.type, previousText),
                    confidence = signal.confidence,
                    source = "local_rule",
                )
                signals += signal
            }
        }
        repository.replaceLocalRuleMarkers(sessionId, markers, segments.associate { it.sequence to it.text })
        return signals
    }

    suspend fun markTranscriptionUnavailable(sessionId: Long) {
        repository.markTranscriptionUnavailable(sessionId)
    }
    suspend fun clearSession(sessionId: Long) {
        pendingMutex.withLock {
            pendingSequences.remove(sessionId)
            pendingEvents.remove(sessionId)
            pendingPartials.remove(sessionId)
        }
        aiPersistenceMutex.withLock {
            val worker = synchronized(aiWorkers) { aiWorkers.remove(sessionId) }
            worker?.wake?.close()
            worker?.job?.cancel()
            aiAnnotator?.clear(sessionId)
        }
    }

    private companion object {
        const val AI_FLUSH_TIMEOUT_MILLIS = 15_000L
    }

    suspend fun addManualMarker(sessionId: Long, positionMillis: Long, type: DitingMarkerType) {
        repository.addMarker(
            DitingMarkerEntity(
                sessionId = sessionId,
                positionMillis = positionMillis.coerceAtLeast(0),
                type = type.key,
                title = type.label,
            ),
        )
    }
}


package win.iqwqi.xiangece.feature.diting.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.collect
import win.iqwqi.xiangece.R
import java.util.concurrent.atomic.AtomicBoolean
import win.iqwqi.xiangece.feature.diting.data.DitingMarkerEntity
import win.iqwqi.xiangece.feature.diting.data.DitingRepository
import win.iqwqi.xiangece.feature.diting.domain.DitingMarkerType
import win.iqwqi.xiangece.feature.diting.domain.prefersOfflineDitingTranscription
import win.iqwqi.xiangece.feature.diting.domain.DitingSessionStatus
import win.iqwqi.xiangece.feature.diting.transcription.DitingSignal
import win.iqwqi.xiangece.feature.diting.transcription.DitingCloudTranscriber
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflineTranscriber
import win.iqwqi.xiangece.feature.diting.transcription.DitingSpeechRecognizer
import win.iqwqi.xiangece.feature.diting.transcription.DitingTranscriptionCoordinator
import win.iqwqi.xiangece.feature.diting.ui.DitingActivity

@AndroidEntryPoint
class DitingRecorderService : Service() {
    @Inject lateinit var repository: DitingRepository
    @Inject lateinit var audioStore: DitingAudioStore
    @Inject lateinit var transcriptionCoordinator: DitingTranscriptionCoordinator
    @Inject lateinit var speechRecognizer: DitingSpeechRecognizer
    @Inject lateinit var cloudTranscriber: DitingCloudTranscriber
    @Inject lateinit var offlineTranscriber: DitingOfflineTranscriber

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commandMutex = Mutex()
    private var recorder: DitingPcmRecorder? = null
    private var durationJob: Job? = null
    private var startInProgress = false
    private var sessionId: Long = 0
    private var isPaused = false
    private var lastQuestionAlertAtMillis = 0L
    private val cloudTranscriptionJobs = mutableSetOf<Job>()
    private val localTranscriptionJobs = mutableSetOf<Job>()
    private val cloudTranscriptionLimiter = Semaphore(2)
    private val transcriptionResultMutex = Mutex()
    @Volatile private var acceptingTranscriptionResults = false
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var audioRouteMonitorJob: Job? = null
    private var lastInputRouteLabel: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            transcriptionCoordinator.asyncSignals.collect { batch ->
                if (batch.sessionId == sessionId && recorder != null && acceptingTranscriptionResults) {
                    notifyQuestionIfNeeded(batch.signals)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.getLongExtra(EXTRA_SESSION_ID, 0L).let { id ->
                if (id > 0) launchCommand { startRecording(id) }
            }
            ACTION_PAUSE -> launchCommand { pauseRecording() }
            ACTION_RESUME -> launchCommand { resumeRecording() }
            ACTION_STOP -> launchCommand { stopRecording() }
            ACTION_MARK_HIGHLIGHT -> launchCommand { addManualMarker(DitingMarkerType.MANUAL_HIGHLIGHT) }
            ACTION_MARK_QUESTION -> launchCommand { addManualMarker(DitingMarkerType.MANUAL_QUESTION) }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun launchCommand(block: suspend () -> Unit) {
        serviceScope.launch { commandMutex.withLock { block() } }
    }

    private suspend fun startRecording(id: Long) {
        if (recorder != null || startInProgress) {
            if (id != sessionId) {
                repository.updateStatus(id, DitingSessionStatus.FAILED, "已有另一节课堂正在录音")
            }
            return
        }
        startInProgress = true
        acceptingTranscriptionResults = false
        val session = repository.sessionSnapshot(id) ?: run {
            startInProgress = false
            stopSelf()
            return
        }
        sessionId = id
        isPaused = false
        lastQuestionAlertAtMillis = 0L
        if (session.status != DitingSessionStatus.DRAFT.key) {
            startInProgress = false
            stopSelf()
            return
        }
        val files = audioStore.filesFor(id)
        try {
            startForegroundCompat(buildNotification("正在准备录音"))
            val localTranscriptionEnabled = AtomicBoolean(false)
            val cloudTranscriptionConfigured = session.cloudTranscriptionEnabled && cloudTranscriber.isConfigured()
            var offlineTranscriptionConfigured = session.cloudTranscriptionEnabled && offlineTranscriber.isConfigured()
            var offlinePreparationError: String? = null
            if (offlineTranscriptionConfigured) {
                updateNotification("正在加载本机中英离线识别")
                val prepared = offlineTranscriber.prepare()
                if (prepared.isFailure) {
                    offlineTranscriptionConfigured = false
                    offlinePreparationError = prepared.exceptionOrNull().toDiagnosticReason()
                    Log.e("DitingRecorderService", "Offline recognizer preparation failed: $offlinePreparationError", prepared.exceptionOrNull())
                }
            }
            val preferOfflineTranscription = prefersOfflineDitingTranscription(session.languageMode, offlineTranscriptionConfigured)
            if (!preferOfflineTranscription && session.cloudTranscriptionEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                localTranscriptionEnabled.set(speechRecognizer.start(
                    languageMode = session.languageMode,
                    biasingStrings = session.glossary
                        .split(",", "\n", "\r")
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .plus(if (session.mode == "water_class") {
                            listOf("问题", "提问", "有没有问题", "any questions", "question")
                        } else {
                            listOf("重点", "考点", "必考", "定义", "定理", "important", "key point", "exam")
                        }),
                    onPartial = { text ->
                        launchTranscriptionCallback(id) {
                            transcriptionCoordinator.onTranscriptPartial(id, text)
                        }
                    },
                    onFinal = { text, language, confidence ->
                        launchTranscriptionCallback(id) {
                            val signals = transcriptionCoordinator.onRecognizerTranscript(id, text, text, language, confidence)
                            notifyQuestionIfNeeded(signals)
                        }
                    },
                    onError = {
                        speechRecognizer.stop()
                        localTranscriptionEnabled.set(false)
                        if (sessionId == id && recorder != null) {
                            serviceScope.launch {
                                if (offlineTranscriptionConfigured) {
                                    repository.updateTranscriptionEngine(id, "offline_onnx")
                                } else if (cloudTranscriptionConfigured) {
                                    repository.updateTranscriptionEngine(id, "cloud")
                                } else {
                                    repository.updateTranscriptionEngine(id, "audio_only")
                                    repository.markTranscriptionUnavailable(id)
                                }
                            }
                            updateNotification(if (cloudTranscriptionConfigured) "录音继续，文字会稍后整理" else "录音继续，文字记录暂时不可用")
                        }
                    },
                    onAudioGap = {
                        speechRecognizer.stop()
                        localTranscriptionEnabled.set(false)
                        if (sessionId == id && recorder != null) {
                            serviceScope.launch {
                                if (offlineTranscriptionConfigured) {
                                    repository.updateTranscriptionEngine(id, "offline_onnx")
                                } else if (cloudTranscriptionConfigured) {
                                    repository.updateTranscriptionEngine(id, "cloud")
                                } else {
                                    repository.updateTranscriptionEngine(id, "audio_only")
                                    repository.markTranscriptionUnavailable(id)
                                }
                            }
                            updateNotification(if (cloudTranscriptionConfigured) "录音继续，文字会稍后整理" else "录音继续，实时文字暂时关闭")
                        }
                    },
                ))
            }
            repository.updateTranscriptionEngine(id, when {
                localTranscriptionEnabled.get() -> "android_on_device"
                offlineTranscriptionConfigured -> "offline_onnx"
                cloudTranscriptionConfigured -> "cloud"
                else -> "audio_only"
            })
            val value = DitingPcmRecorder(
                files = files,
                segmentFile = { sequence -> audioStore.segmentFile(id, sequence) },
                onPcmRead = speechRecognizer::offerPcm,
                preferredInputDevice = preferredBuiltInMicrophone(),
                onChunkReady = { chunk ->
                    val offlineEnabledForChunk = offlineTranscriptionConfigured && !localTranscriptionEnabled.get()
                    val cloudEnabledForChunk = cloudTranscriptionConfigured && !localTranscriptionEnabled.get() && !offlineEnabledForChunk
                    val signals = transcriptionCoordinator.onChunkReady(
                        sessionId = id,
                        chunk = chunk,
                        languageMode = session.languageMode,
                        cloudTranscriptionEnabled = session.cloudTranscriptionEnabled,
                        transcriptionEnabled = localTranscriptionEnabled.get() || offlineEnabledForChunk || cloudEnabledForChunk,
                    )
                    notifyQuestionIfNeeded(signals)
                    if (offlineEnabledForChunk) {
                        scheduleOfflineTranscription(id, chunk, session.glossary)
                    } else if (cloudEnabledForChunk) {
                        scheduleCloudTranscription(id, chunk, session.languageMode, session.glossary)
                    }
                },
            )
            value.start().getOrThrow()
            recorder = value
            registerAudioRouteMonitoring()
            acceptingTranscriptionResults = true
            repository.markRecordingStarted(id, files.directory.absolutePath, files.recording.absolutePath)
            offlinePreparationError?.let { reason ->
                repository.updateSessionMessage(id, "文字记录暂时无法启动，录音仍会保存；结束后可以重试。")
            }
            durationJob = serviceScope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(2_000)
                    recorder?.let { repository.updateDuration(id, it.currentDurationMillis()) }
                }
            }
            startInProgress = false
            val inputRoute = value.currentInputRouteLabel()
            val transcriptionMessage = offlinePreparationError?.let { "文字记录暂时不可用，录音仍会保存；结束后可以重试。" }
                ?: when {
                    localTranscriptionEnabled.get() -> "正在录音 · 文字记录已开启"
                    offlineTranscriptionConfigured -> "正在录音 · 文字记录已开启"
                    cloudTranscriptionConfigured -> "正在录音 · 文字记录已开启"
                    else -> "正在录音 · 只保存声音"
                }
            updateNotification("$transcriptionMessage · 输入：$inputRoute")
        } catch (error: Throwable) {
            startInProgress = false
            acceptingTranscriptionResults = false
            unregisterAudioRouteMonitoring()
            val value = recorder
            recorder = null
            speechRecognizer.stop()
            repository.markTranscriptionUnavailable(id)
            transcriptionCoordinator.clearSession(id)
            cancelPendingTranscriptions()
            if (value != null) {
                try {
                    value.stop()
                } catch (_: Throwable) {
                    // The session is already being marked failed; preserve the audio that was written.
                }
            }
            Log.e("DitingRecorderService", "Recording start failed", error)
            repository.updateStatus(id, DitingSessionStatus.FAILED, "录音没有正常启动，请重新尝试。")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    private fun scheduleOfflineTranscription(
        id: Long,
        chunk: DitingAudioChunk,
        glossary: String,
    ) {
        val job = serviceScope.launch(Dispatchers.Default) {
            cloudTranscriptionLimiter.withPermit {
                val result = offlineTranscriber.transcribe(chunk.file, glossary)
                if (result.isSuccess) {
                    val transcript = result.getOrThrow()
                    withAcceptedTranscription(id) {
                        val signals = transcriptionCoordinator.onTranscriptReady(
                            sessionId = id,
                            sequence = chunk.sequence,
                            text = transcript.text,
                            rawText = transcript.text,
                            language = transcript.language,
                            confidence = transcript.confidence,
                        )
                        notifyQuestionIfNeeded(signals)
                    }
                } else {
                    val reason = result.exceptionOrNull().toDiagnosticReason()
                    Log.e("DitingRecorderService", "Offline transcription failed: session=$id sequence=${chunk.sequence}; $reason", result.exceptionOrNull())
                    withAcceptedTranscription(id) {
                        repository.markSegmentAudioOnly(id, chunk.sequence, "这一段文字暂未识别出来，音频仍可回听。")
                        updateNotification("录音继续，这一小段文字暂未识别，音频已保留")
                    }
                }
            }
        }
        synchronized(cloudTranscriptionJobs) { cloudTranscriptionJobs += job }
        job.invokeOnCompletion { synchronized(cloudTranscriptionJobs) { cloudTranscriptionJobs -= job } }
    }

    private fun scheduleCloudTranscription(
        id: Long,
        chunk: DitingAudioChunk,
        languageMode: String,
        glossary: String,
    ) {
        val job = serviceScope.launch(Dispatchers.IO) {
            cloudTranscriptionLimiter.withPermit {
                val result = cloudTranscriber.transcribe(chunk.file, languageMode, glossary)
                if (result.isSuccess) {
                    val transcript = result.getOrThrow()
                    withAcceptedTranscription(id) {
                        val signals = transcriptionCoordinator.onTranscriptReady(
                            sessionId = id,
                            sequence = chunk.sequence,
                            text = transcript.text,
                            rawText = transcript.text,
                            language = transcript.language,
                            confidence = transcript.confidence,
                        )
                        notifyQuestionIfNeeded(signals)
                    }
                } else {
                    withAcceptedTranscription(id) {
                        repository.markSegmentAudioOnly(id, chunk.sequence)
                        updateNotification("这一小段文字识别暂时失败，声音已保留")
                    }
                }
            }
        }
        synchronized(cloudTranscriptionJobs) { cloudTranscriptionJobs += job }
        job.invokeOnCompletion { synchronized(cloudTranscriptionJobs) { cloudTranscriptionJobs -= job } }
    }
    private suspend fun awaitTranscriptions() {
        val jobs = buildList {
            addAll(synchronized(cloudTranscriptionJobs) { cloudTranscriptionJobs.toList() })
            addAll(synchronized(localTranscriptionJobs) { localTranscriptionJobs.toList() })
        }
        withTimeoutOrNull(TRANSCRIPTION_FLUSH_TIMEOUT_MILLIS) { jobs.joinAll() }
    }
    private suspend fun withAcceptedTranscription(id: Long, block: suspend () -> Unit) {
        transcriptionResultMutex.withLock {
            if (!acceptingTranscriptionResults || sessionId != id) return@withLock
            block()
        }
    }

    private fun launchTranscriptionCallback(id: Long, block: suspend () -> Unit) {
        val job = serviceScope.launch {
            withAcceptedTranscription(id, block)
        }
        synchronized(localTranscriptionJobs) { localTranscriptionJobs += job }
        job.invokeOnCompletion {
            synchronized(localTranscriptionJobs) { localTranscriptionJobs -= job }
        }
    }

    private suspend fun stopAcceptingTranscriptionResults() {
        transcriptionResultMutex.withLock {
            acceptingTranscriptionResults = false
        }
        cancelPendingTranscriptions()
    }

    private fun cancelPendingTranscriptions() {
        val jobs = buildList {
            addAll(synchronized(cloudTranscriptionJobs) { cloudTranscriptionJobs.toList() })
            addAll(synchronized(localTranscriptionJobs) { localTranscriptionJobs.toList() })
        }
        jobs.forEach { it.cancel() }
    }
    private suspend fun pauseRecording() {
        val current = recorder ?: return
        current.pause()
        isPaused = true
        repository.updateStatus(sessionId, DitingSessionStatus.PAUSED)
        updateNotification("录音已暂停")
    }

    private suspend fun resumeRecording() {
        val current = recorder ?: return
        try {
            current.resume()
            isPaused = false
            repository.updateStatus(sessionId, DitingSessionStatus.RECORDING)
            updateNotification("正在录音 · 输入：${current.currentInputRouteLabel()}")
        } catch (error: Throwable) {
            recorder = null
            durationJob?.cancel()
            durationJob = null
            runCatching { current.stop() }
            stopAcceptingTranscriptionResults()
            speechRecognizer.stop()
            repository.markTranscriptionUnavailable(sessionId)
            transcriptionCoordinator.clearSession(sessionId)
            Log.e("DitingRecorderService", "Recording resume failed", error)
            repository.updateStatus(sessionId, DitingSessionStatus.FAILED, "录音没有成功恢复，之前的内容仍会保留。")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun stopRecording() {
        val current = recorder ?: return
        repository.updateStatus(sessionId, DitingSessionStatus.PROCESSING)
        durationJob?.cancel()
        durationJob = null
        recorder = null
        unregisterAudioRouteMonitoring()
        isPaused = false
        val result = runCatching { current.stop() }.getOrElse { error ->
            DitingRecordingResult(
                audioBytes = 0L,
                durationMillis = current.currentDurationMillis(),
                failure = error,
            )
        }
        runCatching { speechRecognizer.finish() }
        awaitTranscriptions()
        stopAcceptingTranscriptionResults()
        val finalLocalSignals = runCatching {
            transcriptionCoordinator.reanalyzeLocalSignals(sessionId)
        }.onFailure { error ->
            Log.e("DitingRecorderService", "Final local signal reanalysis failed", error)
        }.getOrDefault(emptyList())
        val aiSignals = runCatching { transcriptionCoordinator.flushAi(sessionId) }.getOrDefault(emptyList())
        notifyQuestionIfNeeded(aiSignals + finalLocalSignals)
        speechRecognizer.stop()
        repository.markTranscriptionUnavailable(sessionId)
        transcriptionCoordinator.clearSession(sessionId)
        if (result.failure == null) {
            repository.finishSession(
                sessionId,
                result.durationMillis,
                audioStore.sizeOf(audioStore.filesFor(sessionId).recording.absolutePath),
            )
        } else {
            Log.e("DitingRecorderService", "Recording read failed", result.failure)
            repository.updateStatus(sessionId, DitingSessionStatus.FAILED, "录音没有正常结束，已保存此前录下的内容。")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerAudioRouteMonitoring() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || audioDeviceCallback != null) return
        val manager = getSystemService(AudioManager::class.java) ?: return
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                reportInputRoute()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                reportInputRoute()
            }
        }
        audioDeviceCallback = callback
        manager.registerAudioDeviceCallback(callback, null)
        reportInputRoute()
        audioRouteMonitorJob = serviceScope.launch {
            while (isActive) {
                delay(5_000)
                reportInputRoute()
            }
        }
    }

    private fun unregisterAudioRouteMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val manager = getSystemService(AudioManager::class.java)
            audioDeviceCallback?.let { callback ->
                runCatching { manager?.unregisterAudioDeviceCallback(callback) }
            }
        }
        audioRouteMonitorJob?.cancel()
        audioRouteMonitorJob = null
        audioDeviceCallback = null
        lastInputRouteLabel = ""
    }

    private fun reportInputRoute() {
        val current = recorder ?: return
        current.reassertPreferredInputRoute()
        val route = current.currentInputRouteLabel()
        if (route == lastInputRouteLabel) return
        lastInputRouteLabel = route
        val bluetooth = route.contains("蓝牙")
        updateNotification(
            if (bluetooth) {
                "当前收音：$route；可能影响录音清晰度"
            } else {
                "当前收音：$route；耳机可以继续播放"
            },
        )
    }
    private fun preferredBuiltInMicrophone(): AudioDeviceInfo? {
        return getSystemService(AudioManager::class.java)
            ?.getDevices(AudioManager.GET_DEVICES_INPUTS)
            ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
    }

    private suspend fun addManualMarker(type: DitingMarkerType) {
        val current = recorder ?: return
        transcriptionCoordinator.addManualMarker(
            sessionId = sessionId,
            positionMillis = current.currentDurationMillis(),
            type = type,
        )
    }
    @Synchronized
    private fun notifyQuestionIfNeeded(signals: List<DitingSignal>) {
        if (signals.none { it.type == DitingMarkerType.AUTO_QUESTION }) return
        val now = System.currentTimeMillis()
        if (now - lastQuestionAlertAtMillis < QUESTION_ALERT_DEBOUNCE_MILLIS) return
        lastQuestionAlertAtMillis = now
        getSystemService(NotificationManager::class.java).notify(
            questionNotificationId,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("谛听：可能有人提问")
                .setContentText("检测到课堂中可能正在提问，请留意问题内容")
                .setContentIntent(activityPendingIntent())
                .setAutoCancel(true)
                .setTimeoutAfter(10_000)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 160, 100, 160))
                .build(),
        )
    }
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(notificationId, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("谛听")
            .setContentText(text)
            .setContentIntent(activityPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_notification, if (isPaused) "继续" else "暂停", servicePendingIntent(if (isPaused) ACTION_RESUME else ACTION_PAUSE))
            .addAction(R.drawable.ic_notification, "标记重点", servicePendingIntent(ACTION_MARK_HIGHLIGHT))
            .addAction(R.drawable.ic_notification, "标记提问", servicePendingIntent(ACTION_MARK_QUESTION))
            .addAction(R.drawable.ic_notification, "结束", servicePendingIntent(ACTION_STOP))
            .build()

    private fun servicePendingIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, DitingRecorderService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun activityPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            notificationId + 1,
            Intent(this, DitingActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "课堂录音",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "谛听录音进行中的状态通知" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "课堂提问提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "谛听检测到课堂中可能出现提问时的轻微震动提醒"
                enableVibration(true)
                setSound(null, null)
            },
        )
    }

    override fun onDestroy() {
        unregisterAudioRouteMonitoring()
        val current = recorder
        if (current != null && sessionId > 0) {
            runCatching {
                runBlocking(Dispatchers.IO) {
                    repository.updateStatus(sessionId, DitingSessionStatus.PROCESSING)
                    val result = current.stop()
                    runCatching { speechRecognizer.finish() }
                    awaitTranscriptions()
                    stopAcceptingTranscriptionResults()
                    val finalLocalSignals = runCatching {
                        transcriptionCoordinator.reanalyzeLocalSignals(sessionId)
                    }.onFailure { error ->
                        Log.e("DitingRecorderService", "Final local signal reanalysis failed", error)
                    }.getOrDefault(emptyList())
                    val aiSignals = runCatching { transcriptionCoordinator.flushAi(sessionId) }.getOrDefault(emptyList())
                    notifyQuestionIfNeeded(aiSignals + finalLocalSignals)
                    if (result.failure == null) {
                        repository.finishSession(
                            sessionId,
                            result.durationMillis,
                            audioStore.sizeOf(audioStore.filesFor(sessionId).recording.absolutePath),
                        )
                    } else {
                        repository.updateStatus(
                            sessionId,
                            DitingSessionStatus.FAILED,
                            result.failure.message ?: "录音服务被终止",
                        )
                    }
                }
            }.onFailure {
                runCatching {
                    runBlocking(Dispatchers.IO) {
                        repository.updateStatus(sessionId, DitingSessionStatus.FAILED, "录音服务被终止，音频收尾失败")
                    }
                }
            }
        }
        runCatching { runBlocking(Dispatchers.IO) { stopAcceptingTranscriptionResults() } }
        speechRecognizer.stop()
        runCatching { runBlocking(Dispatchers.IO) { offlineTranscriber.release() } }
        if (sessionId > 0) runBlocking(Dispatchers.IO) {
            repository.markTranscriptionUnavailable(sessionId)
            transcriptionCoordinator.clearSession(sessionId)
        }
        durationJob?.cancel()
        durationJob = null
        recorder = null
        startInProgress = false
        isPaused = false
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "diting_recording"
        private const val ALERT_CHANNEL_ID = "diting_question_alert"
        private const val notificationId = 3101
        private const val questionNotificationId = 3102
        private const val QUESTION_ALERT_DEBOUNCE_MILLIS = 8_000L
        private const val TRANSCRIPTION_FLUSH_TIMEOUT_MILLIS = 15_000L
        const val ACTION_START = "win.iqwqi.xiangece.diting.START"
        const val ACTION_PAUSE = "win.iqwqi.xiangece.diting.PAUSE"
        const val ACTION_RESUME = "win.iqwqi.xiangece.diting.RESUME"
        const val ACTION_STOP = "win.iqwqi.xiangece.diting.STOP"
        const val ACTION_MARK_HIGHLIGHT = "win.iqwqi.xiangece.diting.MARK_HIGHLIGHT"
        const val ACTION_MARK_QUESTION = "win.iqwqi.xiangece.diting.MARK_QUESTION"
        const val EXTRA_SESSION_ID = "session_id"

        fun start(context: android.content.Context, sessionId: Long) {
            val intent = Intent(context, DitingRecorderService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION_ID, sessionId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun command(context: android.content.Context, action: String) {
            context.startService(Intent(context, DitingRecorderService::class.java).setAction(action))
        }
    }
}


private fun Throwable?.toDiagnosticReason(): String {
    val raw = generateSequence(this) { it.cause }
        .mapNotNull { it.message?.takeIf(String::isNotBlank) }
        .joinToString(" | ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (raw.isBlank()) return "未知错误"
    return when {
        raw.contains("OrtGetApiBase", ignoreCase = true) || raw.contains("cannot locate symbol", ignoreCase = true) ->
            "本机语音引擎版本不兼容，请更新基础 APK 后重试"
        raw.contains("No such file", ignoreCase = true) || raw.contains("does not exist", ignoreCase = true) ->
            "离线语音包文件不完整，请在“我的资源”重新导入或下载"
        raw.contains("out of memory", ignoreCase = true) || raw.contains("memory", ignoreCase = true) ->
            "本机内存不足，已保留录音；请关闭后台应用后重试"
        else -> raw.take(240)
    }
}

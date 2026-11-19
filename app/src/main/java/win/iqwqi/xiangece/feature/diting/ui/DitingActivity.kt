package win.iqwqi.xiangece.feature.diting.ui

import android.Manifest
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.speech.SpeechRecognizer
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import win.iqwqi.xiangece.ui.theme.setContentAfterAppearance
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.AndroidEntryPoint
import win.iqwqi.xiangece.feature.diting.audio.DitingRecorderService
import win.iqwqi.xiangece.feature.diting.data.DitingMarkerEntity
import win.iqwqi.xiangece.feature.diting.data.DitingSegmentEntity
import win.iqwqi.xiangece.feature.diting.data.DitingSessionEntity
import win.iqwqi.xiangece.feature.diting.domain.DitingLanguageMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import win.iqwqi.xiangece.data.CampusRepository
import win.iqwqi.xiangece.data.local.CourseEntity
import win.iqwqi.xiangece.data.settings.AppSettingsStore
import win.iqwqi.xiangece.feature.diting.domain.DitingMode
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackManager
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

@AndroidEntryPoint
class DitingActivity : ComponentActivity() {
    private val viewModel by viewModels<DitingViewModel>()
    @javax.inject.Inject lateinit var campusRepository: CampusRepository
    @javax.inject.Inject lateinit var settingsStore: AppSettingsStore
    @javax.inject.Inject lateinit var notebookCollection: win.iqwqi.xiangece.feature.notebook.data.NotebookCollection
    @javax.inject.Inject lateinit var offlinePackManager: DitingOfflinePackManager
    private var pendingStart: (() -> Unit)? = null
    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var playingSessionId by mutableStateOf<Long?>(null)
    private var isPlaying by mutableStateOf(false)
    private var localRecognitionAvailable by mutableStateOf(false)
    private var notificationsAvailable by mutableStateOf(false)
    private var playbackPositionMillis by mutableStateOf(0L)
    private var playbackDurationMillis by mutableStateOf(0L)
    private var playbackSpeed by mutableStateOf(1f)
    private var playbackProgressJob: Job? = null
    private var playbackStopAtMillis: Long? = null
    private var pendingSeekMillis: Int? = null
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        val action = pendingStart
        pendingStart = null
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        localRecognitionAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        notificationsAvailable = isNotificationPermissionAvailable()
        setContentAfterAppearance(settingsStore.settings) { initialSettings ->
            val sessions by viewModel.sessions.collectAsStateWithLifecycle()
            val courses by campusRepository.courses.collectAsStateWithLifecycle(initialValue = emptyList())
            val selected by viewModel.selectedSession.collectAsStateWithLifecycle()
            val segments by viewModel.selectedSegments.collectAsStateWithLifecycle()
            val markers by viewModel.selectedMarkers.collectAsStateWithLifecycle()
            val retryingOfflineSessionId by viewModel.retryingOfflineSessionId.collectAsStateWithLifecycle()
            val reanalyzingSessionId by viewModel.reanalyzingSessionId.collectAsStateWithLifecycle()
            val creatingSession by viewModel.creatingSession.collectAsStateWithLifecycle()
            val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = initialSettings)
            val offlinePackState by offlinePackManager.state.collectAsStateWithLifecycle()
            val useDarkTheme = if (settings.followSystemTheme) isSystemInDarkTheme() else settings.darkMode
            XiangeceTheme(darkTheme = useDarkTheme, themeSeedId = settings.themeSeed, companion = settings.companion) {
                val surface = MaterialTheme.colorScheme.surface.toArgb()
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background))
                    window.statusBarColor = background
                    window.navigationBarColor = surface
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !useDarkTheme
                        isAppearanceLightNavigationBars = !useDarkTheme
                    }
                }
                Surface(color = MaterialTheme.colorScheme.background) {
                    DitingScreen(
                    sessions = sessions,
                    onBack = { finish() },
                    selectedSession = selected,
                    segments = segments,
                    markers = markers,
                    onStart = ::startSession,
                    onPause = { DitingRecorderService.command(this, DitingRecorderService.ACTION_PAUSE) },
                    onResume = { DitingRecorderService.command(this, DitingRecorderService.ACTION_RESUME) },
                    onStop = { DitingRecorderService.command(this, DitingRecorderService.ACTION_STOP) },
                    onRetryOfflineTranscription = viewModel::retryOfflineTranscription,
                    onReanalyzeSession = viewModel::reanalyzeSession,
                    onMarkHighlight = { DitingRecorderService.command(this, DitingRecorderService.ACTION_MARK_HIGHLIGHT) },
                    onMarkQuestion = { DitingRecorderService.command(this, DitingRecorderService.ACTION_MARK_QUESTION) },
                    onTogglePlayback = { session -> togglePlayback(session) },
                    onSeekPlayback = ::seekPlayback,
                    onPlayClip = ::playClip,
                    onPlaySegment = ::playSegment,
                    isPlaying = isPlaying,
                    playbackPositionMillis = playbackPositionMillis,
                    playbackDurationMillis = playbackDurationMillis,
                    playbackSpeed = playbackSpeed,
                    onSetPlaybackSpeed = ::applyPlaybackSpeed,
                    courses = courses,
                    onUpdateSession = viewModel::updateSessionDetails,
                    onUpdateSegment = viewModel::updateSegmentText,
                    onUpdateMarker = viewModel::updateMarker,
                    onDeleteMarker = viewModel::deleteMarker,
                    isRetryingOfflineTranscription = retryingOfflineSessionId == selected?.id,
                    isReanalyzingSession = reanalyzingSessionId == selected?.id,
                    creatingSession = creatingSession,
                    localRecognitionAvailable = localRecognitionAvailable,
                    offlinePackState = offlinePackState,
                    onOpenResources = {
                        startActivity(Intent(this@DitingActivity, win.iqwqi.xiangece.MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("open_my_resources", true)
                        })
                        finish()
                    },
                    cloudTranscriptionConfigured = settings.aiEnabled && settings.encryptedApiKey.isNotBlank() && (settings.ditingTranscriptionEndpoint.isNotBlank() || settings.aiBaseUrl.isNotBlank()),
                    aiAnnotationEnabled = settings.ditingAiAnnotationEnabled,
                    aiAnnotationConfigured = settings.aiEnabled && settings.ditingAiAnnotationEnabled && settings.aiBaseUrl.isNotBlank() && settings.aiModel.isNotBlank() && settings.encryptedApiKey.isNotBlank(),
                    notificationsAvailable = notificationsAvailable,
                    onShareTranscript = ::shareTranscript,
                    onShareAudio = ::shareAudio,
                    onDelete = viewModel::deleteSession,
                    onSelect = viewModel::selectSession,
                    onCollectNotebook = { id -> startActivity(Intent(this, win.iqwqi.xiangece.feature.notebook.ui.NotebookActivity::class.java)
                        .putExtra("collect_session", id)) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationsAvailable = isNotificationPermissionAvailable()
        val sessionId = intent.getLongExtra("notebook_session", -1)
        if (sessionId > 0) {
            val created = intent.getLongExtra("notebook_session_created", -1)
            val segmentId = intent.getLongExtra("notebook_segment", -1)
            val revision = intent.getStringExtra("notebook_revision")
            intent.removeExtra("notebook_session")
            lifecycleScope.launch {
                val session = notebookCollection.diting.sessionSnapshot(sessionId)
                val segment = notebookCollection.diting.segmentsSnapshot(sessionId).firstOrNull { it.id == segmentId }
                if (session == null || session.createdAtEpochMillis != created || segment == null) {
                    android.widget.Toast.makeText(this@DitingActivity, "原课堂或片段不可用，笔记中的文字快照仍保留", android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                }
                viewModel.selectSession(sessionId)
                if (win.iqwqi.xiangece.feature.notebook.data.textRevision(segment.text) != revision) {
                    android.widget.Toast.makeText(this@DitingActivity, "文字已有变化，请结合当前转写回听核对", android.widget.Toast.LENGTH_LONG).show()
                }
                if (java.io.File(segment.audioPath).isFile) playSegment(segment)
                else if (java.io.File(session.audioPath).isFile) seekPlayback(session, segment.startMillis)
                else android.widget.Toast.makeText(this@DitingActivity, "录音文件不可用，文字仍保留", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isNotificationPermissionAvailable(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun togglePlayback(session: DitingSessionEntity, seekToMillis: Long? = null) {
        if (playingSessionId == session.id && mediaPlayer != null && seekToMillis == null) {
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
                stopPlaybackProgress()
            } else {
                mediaPlayer?.start()
                applyPlaybackSpeed(playbackSpeed)
                isPlaying = true
                startPlaybackProgress()
            }
            return
        }
        startPlayback(session.audioPath, session.id, seekToMillis)
    }

    private fun startPlayback(sourcePath: String, playbackKey: Long, seekToMillis: Long? = null) {
        releasePlayer()
        pendingSeekMillis = seekToMillis?.coerceAtLeast(0L)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(sourcePath)
                setOnPreparedListener { player ->
                    mediaPlayer = player
                    playingSessionId = playbackKey
                    playbackDurationMillis = player.duration.toLong().coerceAtLeast(0L)
                    pendingSeekMillis?.let(player::seekTo)
                    pendingSeekMillis = null
                    loudnessEnhancer?.release()
                    loudnessEnhancer = runCatching {
                        LoudnessEnhancer(player.audioSessionId).apply {
                            setTargetGain(PLAYBACK_BOOST_MILLIBELS)
                            enabled = true
                        }
                    }.getOrNull()
                    applyPlaybackSpeed(playbackSpeed)
                    player.start()
                    this@DitingActivity.isPlaying = true
                    startPlaybackProgress()
                }
                setOnCompletionListener {
                    this@DitingActivity.isPlaying = false
                    releasePlayer()
                }
                setOnErrorListener { _, _, _ ->
                    this@DitingActivity.isPlaying = false
                    releasePlayer()
                    true
                }
                prepareAsync()
            }.also { mediaPlayer = it }
        }.onFailure {
            pendingSeekMillis = null
            releasePlayer()
        }
    }

    private fun seekPlayback(session: DitingSessionEntity, positionMillis: Long) {
        playbackStopAtMillis = null
        if (playingSessionId != session.id || mediaPlayer == null) {
            togglePlayback(session, positionMillis)
            return
        }
        mediaPlayer?.let { player ->
            val duration = player.duration.toLong().coerceAtLeast(0L)
            player.seekTo(positionMillis.coerceIn(0L, duration).toInt())
            playbackPositionMillis = positionMillis.coerceIn(0L, duration)
            if (!isPlaying) {
                player.start()
                isPlaying = true
            }
        }
                startPlaybackProgress()
    }

    private fun shareTranscript(
        session: DitingSessionEntity,
        segments: List<DitingSegmentEntity>,
        markers: List<DitingMarkerEntity>,
    ) {
        val markerText = markers.joinToString("\n") {
            "[${timeLabel(it.positionMillis)}] ${it.title}: ${it.note.ifBlank { "已标记" }}"
        }
        val segmentText = segments.joinToString("\n") {
            "[${timeLabel(it.startMillis)}–${timeLabel(it.endMillis)}] ${it.text.ifBlank { "（文字暂未识别，可回听音频或稍后重试）" }}"
        }
        val text = buildString {
            appendLine("${session.title} · 谛听课堂记录")
            appendLine()
            if (markerText.isNotBlank()) appendLine("标记：\n$markerText\n")
            appendLine("文字分段：")
            append(segmentText)
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "${session.title}课堂记录")
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "分享谛听记录",
            ),
        )
    }

    private fun shareAudio(session: DitingSessionEntity) {
        val file = runCatching { java.io.File(session.audioPath).canonicalFile }.getOrNull() ?: return
        val audioRoot = runCatching { java.io.File(filesDir, "diting").canonicalFile }.getOrNull() ?: return
        if (!file.isFile || !file.path.startsWith(audioRoot.path + java.io.File.separator)) return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/wav"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "分享课堂录音",
            ),
        )
    }

    private fun applyPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        mediaPlayer?.let { player ->
            player.playbackParams = player.playbackParams.setSpeed(speed)
        }
    }

    private fun startPlaybackProgress() {
        playbackProgressJob?.cancel()
        playbackProgressJob = lifecycleScope.launch {
            while (isPlaying) {
                mediaPlayer?.let { player ->
                    playbackPositionMillis = player.currentPosition.toLong()
                    playbackDurationMillis = player.duration.toLong().coerceAtLeast(0L)
                }
                delay(250)
                val stopAt = playbackStopAtMillis
                if (stopAt != null && playbackPositionMillis >= stopAt) {
                    mediaPlayer?.pause()
                    playbackStopAtMillis = null
                    isPlaying = false
                }
            }
        }
    }

    private fun playClip(session: DitingSessionEntity, startMillis: Long, endMillis: Long) {
        togglePlayback(session, startMillis.coerceAtLeast(0L))
        playbackStopAtMillis = endMillis.coerceAtLeast(startMillis + 1_000L)
    }

    private fun playSegment(segment: DitingSegmentEntity) {
        if (segment.audioPath.isBlank()) return
        playbackStopAtMillis = null
        startPlayback(segment.audioPath, -segment.id.coerceAtLeast(1L), 0L)
    }

    private fun stopPlaybackProgress() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
    }

    private fun releasePlayer() {
        stopPlaybackProgress()
        loudnessEnhancer?.runCatching { release() }
        loudnessEnhancer = null
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        playingSessionId = null
        isPlaying = false
        playbackPositionMillis = 0L
        playbackDurationMillis = 0L
        playbackStopAtMillis = null
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }
    private fun timeLabel(value: Long): String = "%02d:%02d".format(value / 60_000, (value / 1_000) % 60)

    private companion object {
        const val PLAYBACK_BOOST_MILLIBELS = 600
    }

    private fun segmentStatusLabel(value: String): String = when (value) {
        "waiting_for_transcription" -> "等待转写"
        "local_audio_only" -> "仅本地音频"
        "transcribing" -> "转写中"
        "completed" -> "已转写"
        "failed" -> "转写失败"
        else -> "待处理"
    }
    private fun startSession(title: String, mode: DitingMode, language: DitingLanguageMode, glossary: String, cloudTranscriptionEnabled: Boolean, aiAnnotationEnabled: Boolean) {
        val start = {
            viewModel.createSession(title, mode.key, language.key, glossary, cloudTranscriptionEnabled, aiAnnotationEnabled) { id ->
                DitingRecorderService.start(this, id)
            }
        }
        val missingPermissions = buildList {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missingPermissions.isEmpty()) {
            start()
        } else {
            pendingStart = start
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}

package win.iqwqi.xiangece.feature.diting.ui

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.speech.SpeechRecognizer
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import win.iqwqi.xiangece.data.settings.AppSettings
import win.iqwqi.xiangece.data.settings.AppSettingsStore
import win.iqwqi.xiangece.feature.diting.domain.DitingMode
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackManager
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

@AndroidEntryPoint
class DitingActivity : ComponentActivity() {
    private val viewModel by viewModels<DitingViewModel>()
    @javax.inject.Inject lateinit var settingsStore: AppSettingsStore
    @javax.inject.Inject lateinit var offlinePackManager: DitingOfflinePackManager
    private var pendingStart: (() -> Unit)? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playingSessionId by mutableStateOf<Long?>(null)
    private var isPlaying by mutableStateOf(false)
    private var localRecognitionAvailable by mutableStateOf(false)
    private var notificationsAvailable by mutableStateOf(false)
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
        localRecognitionAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        notificationsAvailable = isNotificationPermissionAvailable()
        setContent {
            val sessions by viewModel.sessions.collectAsStateWithLifecycle()
            val selected by viewModel.selectedSession.collectAsStateWithLifecycle()
            val segments by viewModel.selectedSegments.collectAsStateWithLifecycle()
            val markers by viewModel.selectedMarkers.collectAsStateWithLifecycle()
            val retryingOfflineSessionId by viewModel.retryingOfflineSessionId.collectAsStateWithLifecycle()
            val reanalyzingSessionId by viewModel.reanalyzingSessionId.collectAsStateWithLifecycle()
            val creatingSession by viewModel.creatingSession.collectAsStateWithLifecycle()
            val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val offlinePackState by offlinePackManager.state.collectAsStateWithLifecycle()
            val useDarkTheme = if (settings.followSystemTheme) isSystemInDarkTheme() else settings.darkMode
            XiangeceTheme(darkTheme = useDarkTheme, themeSeedId = settings.themeSeed) {
                val surface = MaterialTheme.colorScheme.surface.toArgb()
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
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
                    isPlaying = isPlaying,
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
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationsAvailable = isNotificationPermissionAvailable()
    }

    private fun isNotificationPermissionAvailable(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun togglePlayback(session: DitingSessionEntity, seekToMillis: Long? = null) {
        if (playingSessionId == session.id && mediaPlayer != null && seekToMillis == null) {
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
            } else {
                mediaPlayer?.start()
                isPlaying = true
            }
            return
        }
        releasePlayer()
        pendingSeekMillis = seekToMillis?.coerceAtLeast(0L)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
        runCatching {
            MediaPlayer().apply {
                setDataSource(session.audioPath)
                setOnPreparedListener { player ->
                    mediaPlayer = player
                    playingSessionId = session.id
                    pendingSeekMillis?.let(player::seekTo)
                    pendingSeekMillis = null
                    player.start()
                    this@DitingActivity.isPlaying = true
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
        if (playingSessionId != session.id || mediaPlayer == null) {
            togglePlayback(session, positionMillis)
            return
        }
        mediaPlayer?.let { player ->
            val duration = player.duration.toLong().coerceAtLeast(0L)
            player.seekTo(positionMillis.coerceIn(0L, duration).toInt())
            if (!isPlaying) {
                player.start()
                isPlaying = true
            }
        }
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

    private fun releasePlayer() {
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        playingSessionId = null
        isPlaying = false
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }
    private fun timeLabel(value: Long): String = "%02d:%02d".format(value / 60_000, (value / 1_000) % 60)

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

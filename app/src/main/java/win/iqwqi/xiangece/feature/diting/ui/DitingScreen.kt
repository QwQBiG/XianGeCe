package win.iqwqi.xiangece.feature.diting.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import win.iqwqi.xiangece.ui.components.AppPageHeader
import win.iqwqi.xiangece.ui.components.AppSearchField
import win.iqwqi.xiangece.ui.components.AppChoice
import win.iqwqi.xiangece.ui.components.AppConfirmDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.feature.diting.data.DitingSessionEntity
import win.iqwqi.xiangece.ui.components.BrandHeader
import win.iqwqi.xiangece.data.local.CourseEntity
import win.iqwqi.xiangece.ui.components.AppFormSheet
import win.iqwqi.xiangece.ui.components.AppTextField
import win.iqwqi.xiangece.feature.diting.data.DitingMarkerEntity
import win.iqwqi.xiangece.feature.diting.data.DitingSegmentEntity
import win.iqwqi.xiangece.feature.diting.domain.DitingLanguageMode
import win.iqwqi.xiangece.feature.diting.domain.DitingMode
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackCatalog
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DitingScreen(
    sessions: List<DitingSessionEntity>,
    selectedSession: DitingSessionEntity?,
    segments: List<DitingSegmentEntity>,
    markers: List<DitingMarkerEntity>,
    onStart: (String, DitingMode, DitingLanguageMode, String, Boolean, Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRetryOfflineTranscription: (Long) -> Unit,
    onReanalyzeSession: (Long) -> Unit,
    onMarkHighlight: () -> Unit,
    onMarkQuestion: () -> Unit,
    onTogglePlayback: (DitingSessionEntity) -> Unit,
    onSeekPlayback: (DitingSessionEntity, Long) -> Unit,
    onPlayClip: (DitingSessionEntity, Long, Long) -> Unit,
    onPlaySegment: (DitingSegmentEntity) -> Unit,
    playbackPositionMillis: Long,
    playbackDurationMillis: Long,
    playbackSpeed: Float,
    onSetPlaybackSpeed: (Float) -> Unit,
    courses: List<CourseEntity>,
    onUpdateSession: (DitingSessionEntity, String, Long?) -> Unit,
    onUpdateSegment: (DitingSegmentEntity, String) -> Unit,
    onUpdateMarker: (DitingMarkerEntity, String, String) -> Unit,
    onDeleteMarker: (Long) -> Unit,
    isPlaying: Boolean,
    isRetryingOfflineTranscription: Boolean,
    isReanalyzingSession: Boolean,
    creatingSession: Boolean,
    localRecognitionAvailable: Boolean,
    offlinePackState: DitingOfflinePackState,
    onOpenResources: () -> Unit,
    cloudTranscriptionConfigured: Boolean,
    aiAnnotationEnabled: Boolean,
    aiAnnotationConfigured: Boolean,
    notificationsAvailable: Boolean,
    onShareTranscript: (DitingSessionEntity, List<DitingSegmentEntity>, List<DitingMarkerEntity>) -> Unit,
    onShareAudio: (DitingSessionEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onCollectNotebook: (Long) -> Unit,
    onBack: () -> Unit = {},
) {
    var title by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(DitingMode.PROFESSIONAL) }
    var language by rememberSaveable { mutableStateOf(DitingLanguageMode.AUTO) }
    val selectedOfflineTitle = DitingOfflinePackCatalog.find(offlinePackState.selectedPackId)?.title ?: "离线识别包"
    var glossary by rememberSaveable { mutableStateOf("") }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DitingSessionEntity?>(null) }
    var showAllMarkers by remember(selectedSession?.id) { mutableStateOf(false) }
    var markerPage by remember(selectedSession?.id) { mutableStateOf(0) }
    var showAllSegments by remember(selectedSession?.id) { mutableStateOf(false) }
    var showSessionDetails by remember(selectedSession?.id) { mutableStateOf(false) }
    var transcriptQuery by remember(selectedSession?.id) { mutableStateOf("") }
    var transcriptPage by remember(selectedSession?.id) { mutableStateOf(0) }
    var editingSession by remember(selectedSession?.id) { mutableStateOf(false) }
    var editingSegment by remember(selectedSession?.id) { mutableStateOf<DitingSegmentEntity?>(null) }
    var editingMarker by remember(selectedSession?.id) { mutableStateOf<DitingMarkerEntity?>(null) }
    var historyQuery by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf(DitingHistoryFilter.ALL) }
    var collapsedHistoryDays by remember { mutableStateOf(emptySet<String>()) }
    var timelineFilter by remember(selectedSession?.id) { mutableStateOf(DitingTimelineFilter.HIGHLIGHT) }
    var transcriptionEnabled by remember(selectedSession?.id) { mutableStateOf(true) }
    var aiAnnotationForSession by remember(selectedSession?.id, aiAnnotationConfigured) { mutableStateOf(aiAnnotationConfigured) }
    // Keep setup visible until the newly-created session is actually observed.
    val active = selectedSession?.status in setOf("draft", "recording", "paused", "processing")
    val inHistory = !active && (selectedSession == null || showHistory)
    val historyScroll = rememberLazyListState()
    val detailScroll = remember(selectedSession?.id) { LazyListState() }
    LaunchedEffect(selectedSession?.id, active) {
        if (selectedSession != null) showHistory = false
        if (active) showSetup = false
    }
    BackHandler(enabled = !active && !inHistory && !showSetup) { showHistory = true }
    val normalizedHistoryQuery = historyQuery.trim()
    val historySessions = sessions.filter { session ->
        val matchesQuery = normalizedHistoryQuery.isBlank() || session.title.contains(normalizedHistoryQuery, ignoreCase = true)
        matchesQuery && historyFilter.matches(session.status)
    }
    val historyGroups = historySessions.groupBy(::historyGroupLabel)

    if (showSetup && !active) DitingSetupSheet(
        title, { title = it }, mode, { mode = it }, language, { language = it },
        glossary, { glossary = it }, transcriptionEnabled, { transcriptionEnabled = it },
        aiAnnotationForSession, { aiAnnotationForSession = it },
        aiAnnotationEnabled, aiAnnotationConfigured, cloudTranscriptionConfigured,
        offlinePackState.installed, selectedOfflineTitle, localRecognitionAvailable,
        notificationsAvailable, creatingSession,
        onStart = { onStart(title, mode, language, glossary, transcriptionEnabled, aiAnnotationForSession) },
        onDismiss = { showSetup = false }, onOpenResources = onOpenResources,
    )
    pendingDelete?.let { session ->
        AppConfirmDialog(
            title = "删除这节课堂？",
            message = "「${session.title}」的录音、文字和标记会一并删除，无法恢复。",
            onDismiss = { pendingDelete = null },
            onConfirm = { onDelete(session.id); pendingDelete = null },
            confirmLabel = "删除", isDanger = true,
        )
    }

    if (editingSession && selectedSession != null) {
        SessionEditorSheet(
            session = selectedSession,
            courses = courses,
            onDismiss = { editingSession = false },
            onSave = { title, courseId ->
                onUpdateSession(selectedSession, title, courseId)
                editingSession = false
            },
        )
    }
    editingSegment?.let { segment ->
        SegmentEditorSheet(
            segment = segment,
            onDismiss = { editingSegment = null },
            onSave = { text ->
                onUpdateSegment(segment, text)
                editingSegment = null
            },
        )
    }
    editingMarker?.let { marker ->
        MarkerEditorSheet(
            marker = marker,
            onDismiss = { editingMarker = null },
            onSave = { title, note ->
                onUpdateMarker(marker, title, note)
                editingMarker = null
            },
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("diting-list"),
        state = if (inHistory) historyScroll else detailScroll,
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
            item {
                AppPageHeader("谛听", if (inHistory) "${sessions.size} 节课堂" else "课堂记录",
                    onBack = { if (!active && !inHistory) showHistory = true else onBack() }) {
                    if (!active) Button(onClick = { showSetup = true }) { Text("新录音") }
                }
            }
            if (inHistory && sessions.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                when {
                                    cloudTranscriptionConfigured -> "云端转写已配置"
                                    offlinePackState.installed -> "离线资源已准备好"
                                    localRecognitionAvailable -> "手机文字识别可用"
                                    else -> "录音功能随时可用"
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when {
                                    cloudTranscriptionConfigured -> "开启课堂文字记录后优先使用云端；音频按片段上传。"
                                    offlinePackState.installed -> "已选：$selectedOfflineTitle。可在“资源”中切换。"
                                    else -> "离线资源可按需安装，未安装时也能录音和回听。"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = onOpenResources) {
                            Text("资源")
                        }
                    }
                    }
                }
            }
            if (active && selectedSession != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(selectedSession.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("${statusLabel(selectedSession.status)} · ${timeLabel(selectedSession.durationMillis)}", style = MaterialTheme.typography.titleMedium)
                            Text("${ditingModeLabel(selectedSession.mode)} · ${transcriptionLabel(selectedSession.transcriptionEngine)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!selectedSession.errorMessage.isNullOrBlank()) {
                                Text("说明：${selectedSession.errorMessage}", color = MaterialTheme.colorScheme.error)
                            }
                            if (selectedSession.status == "draft") {
                                Text("正在准备录音，请稍候…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (selectedSession.status == "processing") {
                                Text("正在结束并保存课堂音频…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (selectedSession.status == "recording") {
                                        OutlinedButton(
                                            onClick = onPause,
                                            modifier = Modifier.weight(1f),
                                        ) { Text("暂停") }
                                    } else {
                                        Button(
                                            onClick = onResume,
                                            modifier = Modifier.weight(1f),
                                        ) { Text("继续") }
                                    }
                                    Button(
                                        onClick = onStop,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("结束并保存") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = onMarkHighlight,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("手动标记重点") }
                                    OutlinedButton(
                                        onClick = onMarkQuestion,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("手动标记提问") }
                                }
                            }
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("实时文字", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("显示最近约 48 秒，方便接住前后语境。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            if (segments.isEmpty()) {
                                Text("正在等待第一段课堂文字…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                segments.takeLast(6).forEach { segment ->
                                    Text(
                                        "${timeLabel(segment.startMillis)}–${timeLabel(segment.endMillis)}  ${segmentTextLabel(segment)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                val liveQuestions = markers.filter { it.type.contains("question") }.sortedByDescending { it.positionMillis }
                if (liveQuestions.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("课上提问", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("单独处理老师的提问；建议戴耳机重听，避免声音再次录入。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                liveQuestions.take(4).forEach { marker ->
                                    val questionSegment = segments.firstOrNull { it.id == marker.segmentId }
                                        ?: segments.lastOrNull { marker.positionMillis in it.startMillis..it.endMillis }
                                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("${timeLabel(marker.positionMillis)} · ${marker.title.ifBlank { "疑似提问" }}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                            Text(marker.note.ifBlank { "文字可能不完全准确，可以重听原音确认。" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TextButton(
                                                    onClick = { questionSegment?.let(onPlaySegment) },
                                                    enabled = questionSegment?.audioPath?.isNotBlank() == true,
                                                ) { Text("重听提问") }
                                                IconButton(onClick = { onDeleteMarker(marker.id) }) {
                                                    Icon(Icons.Outlined.Delete, contentDescription = "删除这条提问")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (selectedSession != null && !active && !inHistory) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(selectedSession.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                IconButton(onClick = { editingSession = true }) { Icon(Icons.Outlined.Edit, "编辑标题和课程") }
                            }
                            courses.firstOrNull { it.id == selectedSession.courseId }?.let { Text("课程 · " + it.name, color = MaterialTheme.colorScheme.primary) }
                            val highlightMarkers = markers.filter { it.type.contains("highlight") }
                            Text("${statusLabel(selectedSession.status)} · ${timeLabel(selectedSession.durationMillis)} · ${segments.size} 段记录 · ${highlightMarkers.size} 个重点", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("模式：${ditingModeLabel(selectedSession.mode)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("文字：${transcriptionLabel(selectedSession.transcriptionEngine)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(aiAnnotationLabel(selectedSession.aiAnnotationEnabled, aiAnnotationConfigured), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            if (!selectedSession.errorMessage.isNullOrBlank()) {
                                Text("说明：${selectedSession.errorMessage}", color = MaterialTheme.colorScheme.error)
                            }
                             if (selectedSession.audioPath.isNotBlank()) {
                                 Row(
                                     modifier = Modifier.fillMaxWidth(),
                                     horizontalArrangement = Arrangement.spacedBy(8.dp),
                                 ) {
                                     OutlinedButton(
                                         onClick = { onTogglePlayback(selectedSession) },
                                         modifier = Modifier.weight(1f),
                                     ) { Text(if (isPlaying) "暂停回听" else "回听录音") }
                                     OutlinedButton(
                                         onClick = { onShareAudio(selectedSession) },
                                         modifier = Modifier.weight(1f),
                                     ) { Text("分享录音") }
                                 }
                             }
                            if (playbackDurationMillis > 0L) {
                                Text(
                                    timeLabel(playbackPositionMillis) + " / " + timeLabel(playbackDurationMillis),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Slider(
                                    value = playbackPositionMillis.coerceIn(0L, playbackDurationMillis).toFloat(),
                                    onValueChange = { onSeekPlayback(selectedSession, it.toLong()) },
                                    valueRange = 0f..playbackDurationMillis.toFloat(),
                                    enabled = selectedSession.audioPath.isNotBlank(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(0.75f, 1f, 1.25f, 1.5f).forEach { speed ->
                                        FilterChip(
                                            selected = playbackSpeed == speed,
                                            onClick = { onSetPlaybackSpeed(speed) },
                                            label = { Text(speed.toString() + "x") },
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { showSessionDetails = !showSessionDetails; transcriptPage = 0 },
                                    modifier = Modifier.weight(1f),
                                ) { Text(if (showSessionDetails) "收起全文" else "文字与重点") }
                                OutlinedButton(
                                    onClick = { onShareTranscript(selectedSession, segments, markers) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("分享文字") }
                            }
                            OutlinedButton(onClick = { onCollectNotebook(selectedSession.id) },
                                enabled = segments.any { it.isFinal && it.text.isNotBlank() }, modifier = Modifier.fillMaxWidth()) {
                                Text("整理为笔记")
                            }
                            if ((offlinePackState.installed || cloudTranscriptionConfigured) && segments.any { it.text.isBlank() && it.audioPath.isNotBlank() }) {
                                OutlinedButton(
                                    onClick = { onRetryOfflineTranscription(selectedSession.id) },
                                    enabled = !isRetryingOfflineTranscription,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (isRetryingOfflineTranscription) "正在重新识别文字…" else "重新识别未显示文字的片段")
                                }
                            }
                             OutlinedButton(
                                 onClick = { onReanalyzeSession(selectedSession.id) },
                                 enabled = !isReanalyzingSession,
                                 modifier = Modifier.fillMaxWidth(),
                             ) {
                                 Text(if (isReanalyzingSession) "正在重新分析重点…" else "重新分析重点")
                              }
                            if (showSessionDetails) {
                            val highlightCount = markers.count { it.type.contains("highlight") }
                            val filteredMarkers = when (timelineFilter) {
                                DitingTimelineFilter.ALL -> markers.filter { it.type.contains("highlight") }
                                DitingTimelineFilter.HIGHLIGHT -> markers.filter { it.type.contains("highlight") }
                                DitingTimelineFilter.QUESTION -> emptyList()
                            }
                            val markersBySegment = remember(segments, markers) {
                                linkMarkersToSegments(segments, markers.filter { it.type.contains("highlight") })
                            }
                             val filteredSegments = remember(segments, markersBySegment, timelineFilter) {
                                 when (timelineFilter) {
                                     DitingTimelineFilter.ALL -> segments
                                     DitingTimelineFilter.HIGHLIGHT -> segments.filter { markersBySegment[it.id].orEmpty().any { marker -> marker.type.contains("highlight") } }
                                     DitingTimelineFilter.QUESTION -> segments.filter { markersBySegment[it.id].orEmpty().any { marker -> marker.type.contains("question") } }
                                 }
                             }

                            Text("课堂雷达 · ${markers.size} 个标记", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "共整理出 $highlightCount 个重点，可沿时间线直接回听",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(DitingTimelineFilter.ALL, DitingTimelineFilter.HIGHLIGHT).forEach { filter ->
                                    FilterChip(
                                        selected = timelineFilter == filter,
                                        onClick = {
                                            timelineFilter = filter
                                            showAllMarkers = false
                                            showAllSegments = false
                                             markerPage = 0
                                             transcriptPage = 0
                                        },
                                        label = { Text(filter.label) },
                                    )
                                }
                            }
                            if (filteredMarkers.isEmpty()) {
                                Text(
                                    if (highlightCount == 0) "暂未发现课堂重点；录音和文字仍已保存。"
                                    else "当前筛选暂无标记",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                 val markerPageSize = 20
                                 val markerPageCount = maxOf(1, (filteredMarkers.size + markerPageSize - 1) / markerPageSize)
                                 val safeMarkerPage = markerPage.coerceIn(0, markerPageCount - 1)
                                 val visibleMarkers = if (showAllMarkers) {
                                     filteredMarkers.drop(safeMarkerPage * markerPageSize).take(markerPageSize)
                                 } else {
                                     filteredMarkers.takeLast(4)
                                 }
                                 Text(
                                     if (showAllMarkers && markerPageCount > 1) "点击时间点可回听 · 第 ${safeMarkerPage + 1} / $markerPageCount 页" else "点击标记时间点可直接回听",
                                     style = MaterialTheme.typography.labelMedium,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                                 )
                                 visibleMarkers.forEach { marker ->
                                     TextButton(
                                         onClick = { onSeekPlayback(selectedSession, marker.positionMillis) },
                                         modifier = Modifier.fillMaxWidth(),
                                     ) {
                                         Text(
                                             "${timeLabel(marker.positionMillis)}  ${marker.title}: ${marker.note.ifBlank { "已标记" }}",
                                             modifier = Modifier.fillMaxWidth(),
                                             color = if (marker.type.contains("question")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                         )
                                     }
                                     IconButton(onClick = { editingMarker = marker }) {
                                         Icon(Icons.Outlined.Edit, contentDescription = "编辑标记")
                                     }
                                 }
                                 if (showAllMarkers && markerPageCount > 1) {
                                     Row(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalArrangement = Arrangement.SpaceBetween,
                                         verticalAlignment = Alignment.CenterVertically,
                                     ) {
                                         TextButton(onClick = { markerPage = (safeMarkerPage - 1).coerceAtLeast(0) }, enabled = safeMarkerPage > 0) { Text("上一页") }
                                         TextButton(onClick = { markerPage = (safeMarkerPage + 1).coerceAtMost(markerPageCount - 1) }, enabled = safeMarkerPage < markerPageCount - 1) { Text("下一页") }
                                     }
                                 }
                                 if (filteredMarkers.size > 8) {
                                     TextButton(onClick = { showAllMarkers = !showAllMarkers; markerPage = 0 }) {
                                         Text(if (showAllMarkers) "收起标记" else "查看全部 ${filteredMarkers.size} 个标记")
                              }
                         }
                            val quickSeekStops = quickSeekStops(selectedSession.durationMillis)
                            if (quickSeekStops.size > 1) {
                                Text("快速定位", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "长课堂按时间整理，点一下就能跳到对应位置。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    quickSeekStops.forEach { position ->
                                        OutlinedButton(onClick = { onSeekPlayback(selectedSession, position) }) {
                                            Text(timeLabel(position))
                                        }
                                    }
                                }
                            }
                            Text("文字时间线", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (segments.isEmpty()) {
                                Text("暂无文字分段。文字记录未开启或识别不可用时，原音仍会保留，可直接回听。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            } else if (selectedSession.audioPath.isNotBlank()) {
                                Text("带有“重点”标签的文字，可以点击直接回听。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                             if (segments.isNotEmpty()) {
                              if (!showAllSegments && transcriptQuery.isBlank() && segments.size > 8) {
                                  Text(
                                      "当前显示最近 8 条文字；可以搜索关键词，或按页查看更早内容。",
                                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                                      style = MaterialTheme.typography.bodySmall,
                                  )
                              }
                             OutlinedTextField(
                                 value = transcriptQuery,
                                 onValueChange = {
                                     transcriptQuery = it
                                     transcriptPage = 0
                                     showAllSegments = true
                                 },
                                 label = { Text("搜索这节课的文字") },
                                 supportingText = { Text("输入关键词，找到后点击文字即可回听") },
                                 singleLine = true,
                                 modifier = Modifier.fillMaxWidth(),
                             )
                             val searchableSegments = filteredSegments.filter { segment ->
                                 transcriptQuery.isBlank() || segment.text.contains(transcriptQuery.trim(), ignoreCase = true)
                             }
                             val segmentPageSize = 24
                             val segmentPageCount = maxOf(1, (searchableSegments.size + segmentPageSize - 1) / segmentPageSize)
                             val safeSegmentPage = transcriptPage.coerceIn(0, segmentPageCount - 1)
                             if (transcriptQuery.isNotBlank()) {
                                 Text("找到 ${searchableSegments.size} 条文字记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                             }
                             if (searchableSegments.isEmpty()) {
                                 Text("没有找到匹配的文字，可以换个关键词试试。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                             }
                             val visibleSegments = if (showAllSegments) {
                                 searchableSegments.drop(safeSegmentPage * segmentPageSize).take(segmentPageSize)
                             } else {
                                 searchableSegments.takeLast(8)
                             }
                             visibleSegments.forEach { segment ->
                                 val linkedMarkers = markersBySegment[segment.id].orEmpty()
                                 val segmentText = "${timeLabel(segment.startMillis)}–${timeLabel(segment.endMillis)}  ${segmentTextLabel(segment)}"
                                 if (selectedSession.audioPath.isNotBlank()) {
                                     TextButton(
                                         onClick = { onSeekPlayback(selectedSession, segment.startMillis) },
                                         modifier = Modifier.fillMaxWidth(),
                                     ) {
                                         Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                             if (linkedMarkers.isNotEmpty()) {
                                                 Text(
                                                     linkedMarkers.joinToString(" · ") { it.title },
                                                     color = if (linkedMarkers.any { it.type.contains("question") }) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                     style = MaterialTheme.typography.labelMedium,
                                                 )
                                             }
                                             Text(segmentText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                         }
                                     }
                                     IconButton(onClick = { editingSegment = segment }) {
                                         Icon(Icons.Outlined.Edit, contentDescription = "编辑文字")
                                     }
                                 } else {
                                     Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                         if (linkedMarkers.isNotEmpty()) {
                                             Text(linkedMarkers.joinToString(" · ") { it.title }, style = MaterialTheme.typography.labelMedium)
                                         }
                                         Text(segmentText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                     }
                                 }
                             }
                             if (showAllSegments && segmentPageCount > 1) {
                                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                     TextButton(onClick = { transcriptPage = (safeSegmentPage - 1).coerceAtLeast(0) }, enabled = safeSegmentPage > 0) { Text("上一页") }
                                     Text("第 ${safeSegmentPage + 1} / $segmentPageCount 页", style = MaterialTheme.typography.labelMedium)
                                     TextButton(onClick = { transcriptPage = (safeSegmentPage + 1).coerceAtMost(segmentPageCount - 1) }, enabled = safeSegmentPage < segmentPageCount - 1) { Text("下一页") }
                                 }
                             }
                              if (searchableSegments.size > 8) {
                                 TextButton(onClick = { showAllSegments = !showAllSegments; transcriptPage = 0 }) {
                                     Text(if (showAllSegments) "收起文字" else "按页查看全部 ${searchableSegments.size} 条记录")
                              }
                                  }
                              }
                              OutlinedButton(
                                  onClick = { showSessionDetails = false; showAllSegments = false; transcriptPage = 0 },
                                  modifier = Modifier.fillMaxWidth(),
                              ) { Text("收起课堂详情") }
                             }
                            }
                        }
                    }
                }
            }
            if (selectedSession != null && !active && !inHistory && !showSessionDetails) {
                val reviewHighlights = markers.filter { it.type.contains("highlight") }
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("课后重点", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("重点与完整课堂记录分开，复习时先看这里。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            if (reviewHighlights.isEmpty()) {
                                Text("暂未整理出重点，可以点击上方“重新分析重点”。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                reviewHighlights.sortedBy { it.positionMillis }.takeLast(6).forEach { marker ->
                                    val clipRange = questionClipRange(marker, segments)
                                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("${timeLabel(marker.positionMillis)} · ${marker.title.ifBlank { "课堂重点" }}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                            Text(marker.note.ifBlank { "已标记为本节课重点。" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TextButton(
                                                    onClick = { onPlayClip(selectedSession, clipRange.first, clipRange.last) },
                                                    enabled = selectedSession.audioPath.isNotBlank(),
                                                ) { Text("回听重点") }
                                                TextButton(onClick = { editingMarker = marker }) { Text("编辑") }
                                            }
                                        }
                                    }
                                }
                                if (reviewHighlights.size > 6) {
                                    Text("另有 ${reviewHighlights.size - 6} 个重点，可在课堂详情中查看。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            if (inHistory) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("历史课堂", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${sessions.size} 节", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (sessions.size >= 3 || historyQuery.isNotBlank()) {
                        AppSearchField(historyQuery, { historyQuery = it }, "搜索课堂名称")
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DitingHistoryFilter.entries.forEach { filter ->
                            AppChoice(
                                label = filter.label,
                                selected = historyFilter == filter,
                                onClick = { historyFilter = filter },
                            )
                        }
                    }
                    if (historySessions.isEmpty()) {
                        Text(
                            if (sessions.isEmpty()) "还没有课堂录音" else "没有找到符合条件的课堂，可以换个名称或筛选条件。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (historyQuery.isNotBlank()) {
                        Text("找到 ${historySessions.size} 节课堂", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            historyGroups.forEach { (dayLabel, daySessions) ->
                val collapsed = dayLabel in collapsedHistoryDays
                item(key = "history_header_$dayLabel") {
                    TextButton(
                        onClick = {
                            collapsedHistoryDays = if (collapsed) {
                                collapsedHistoryDays - dayLabel
                            } else {
                                collapsedHistoryDays + dayLabel
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "$dayLabel · ${daySessions.size} 节",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(if (collapsed) "展开" else "收起", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (!collapsed) {
                    items(daySessions, key = { it.id }) { session ->
                        val deletable = session.status !in setOf("draft", "recording", "paused", "processing")
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.padding(start = 16.dp, end = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                                    Text(session.title, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${sessionClockLabel(session)} · ${timeLabel(session.durationMillis)} · ${ditingModeLabel(session.mode)} · ${statusLabel(session.status)}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { showHistory = false; onSelect(session.id) }) { Text("查看") }
                                IconButton(onClick = { pendingDelete = session }, enabled = deletable) { Icon(Icons.Outlined.Delete, contentDescription = if (deletable) "删除" else "录音中不可删除") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ditingModeLabel(value: String): String = when (value) {
    "water_class" -> "水课"
    else -> "专业课"
}

private fun questionClipRange(
    marker: DitingMarkerEntity,
    segments: List<DitingSegmentEntity>,
): LongRange {
    val markerIndex = segments.indexOfFirst { it.id == marker.segmentId }
    if (markerIndex < 0) {
        return (marker.positionMillis - 16_000L).coerceAtLeast(0L)..(marker.positionMillis + 2_000L)
    }
    val startIndex = (markerIndex - 2).coerceAtLeast(0)
    val start = segments[startIndex].startMillis.coerceAtLeast(0L)
    val end = (segments[markerIndex].endMillis + 1_000L).coerceAtLeast(start + 1_000L)
    return start..end
}

private fun linkMarkersToSegments(
    segments: List<DitingSegmentEntity>,
    markers: List<DitingMarkerEntity>,
): Map<Long, List<DitingMarkerEntity>> {
    if (segments.isEmpty() || markers.isEmpty()) return emptyMap()

    val segmentById = segments.associateBy { it.id }
    val result = linkedMapOf<Long, MutableList<DitingMarkerEntity>>()
    markers.forEach { marker ->
        val segment = marker.segmentId?.let(segmentById::get)
            ?: segments.firstOrNull { marker.positionMillis in it.startMillis..it.endMillis }
        if (segment != null) {
            result.getOrPut(segment.id) { mutableListOf() }.add(marker)
        }
    }
    return result
}


private enum class DitingHistoryFilter(val label: String) {
    ALL("全部"),
    COMPLETED("已完成"),
    INCOMPLETE("未完成");

    fun matches(status: String): Boolean = when (this) {
        ALL -> true
        COMPLETED -> status == "completed"
        INCOMPLETE -> status != "completed"
    }
}


private enum class DitingTimelineFilter(val label: String) {
    ALL("全部"),
    HIGHLIGHT("重点"),
    QUESTION("提问"),
}

private fun aiAnnotationLabel(enabled: Boolean, configured: Boolean): String = when {
    !enabled -> "AI 课堂整理：未开启"
    configured -> "AI 课堂整理：已开启"
    else -> "AI 课堂整理：待设置"
}

private fun transcriptionLabel(value: String): String = when (value) {
    "android_on_device" -> "手机识别"
    "offline_onnx" -> "离线模型转写"
    "cloud" -> "云端转写"
    "audio_only" -> "仅保存录音"
    else -> "文字记录准备中"
}

private fun timeLabel(value: Long): String {
    val totalSeconds = (value.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}


private fun quickSeekStops(durationMillis: Long): List<Long> {
    val duration = durationMillis.coerceAtLeast(0L)
    val interval = 10 * 60 * 1000L
    if (duration < interval) return emptyList()
    return buildList {
        var position = 0L
        while (position < duration) {
            add(position)
            position += interval
        }
        if (isEmpty() || last() != duration) add(duration)
    }
}
private fun historyGroupLabel(session: DitingSessionEntity): String =
    SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(Date(session.startedAtEpochMillis ?: session.createdAtEpochMillis))
private fun sessionClockLabel(session: DitingSessionEntity): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startedAtEpochMillis ?: session.createdAtEpochMillis))

private fun segmentTextLabel(segment: DitingSegmentEntity): String = segment.text.ifBlank {
    when (segment.status) {
        "local_audio_only", "failed" -> "文字暂未识别，可回听音频或稍后重试"
        "transcribing" -> "正在整理文字"
        else -> segmentStatusLabel(segment.status)
    }
}

private fun segmentStatusLabel(value: String): String = when (value) {
    "waiting_for_transcription" -> "等待转写"
    "local_audio_only" -> "仅本地音频"
    "transcribing" -> "转写中"
    "completed" -> "已转写"
    "failed" -> "转写失败"
    else -> "待处理"
}

private fun statusLabel(value: String): String = when (value) {
    "recording" -> "正在录音"
    "paused" -> "已暂停"
    "completed" -> "已完成"
    "processing" -> "处理中"
    "failed" -> "失败"
    else -> "准备中"
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionEditorSheet(
    session: DitingSessionEntity,
    courses: List<CourseEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit,
) {
    var title by remember(session.id) { mutableStateOf(session.title) }
    var courseId by remember(session.id) { mutableStateOf(session.courseId) }
    AppFormSheet(
        title = "编辑课堂信息",
        subtitle = "修改课堂名称，并关联到已有课程。",
        onConfirm = { onSave(title, courseId) },
        onDismiss = onDismiss,
        confirmLabel = "保存",
        confirmEnabled = title.isNotBlank(),
    ) {
        AppTextField(value = title, onValueChange = { title = it }, label = "课堂名称")
        Text("关联课程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = courseId == null, onClick = { courseId = null }, label = { Text("不关联") })
            courses.take(30).forEach { course ->
                FilterChip(selected = courseId == course.id, onClick = { courseId = course.id }, label = { Text(course.name) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentEditorSheet(
    segment: DitingSegmentEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(segment.id) { mutableStateOf(segment.text) }
    AppFormSheet(
        title = "编辑课堂文字",
        subtitle = timeLabel(segment.startMillis) + "–" + timeLabel(segment.endMillis),
        onConfirm = { onSave(text) },
        onDismiss = onDismiss,
        confirmLabel = "保存",
    ) {
        AppTextField(value = text, onValueChange = { text = it }, label = "转写内容", singleLine = false, minLines = 6)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkerEditorSheet(
    marker: DitingMarkerEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember(marker.id) { mutableStateOf(marker.title) }
    var note by remember(marker.id) { mutableStateOf(marker.note) }
    AppFormSheet(
        title = "编辑课堂标记",
        subtitle = timeLabel(marker.positionMillis),
        onConfirm = { onSave(title, note) },
        onDismiss = onDismiss,
        confirmLabel = "保存",
    ) {
        AppTextField(value = title, onValueChange = { title = it }, label = "标记标题")
        AppTextField(value = note, onValueChange = { note = it }, label = "备注（可选）", singleLine = false, minLines = 3)
    }
}

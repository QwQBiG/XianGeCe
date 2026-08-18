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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.feature.diting.data.DitingSessionEntity
import win.iqwqi.xiangece.ui.components.BrandHeader
import win.iqwqi.xiangece.feature.diting.data.DitingMarkerEntity
import win.iqwqi.xiangece.feature.diting.data.DitingSegmentEntity
import win.iqwqi.xiangece.feature.diting.domain.DitingLanguageMode
import win.iqwqi.xiangece.feature.diting.domain.DitingMode
import win.iqwqi.xiangece.feature.diting.domain.prefersOfflineDitingTranscription
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
) {
    var title by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(DitingMode.PROFESSIONAL) }
    var language by remember { mutableStateOf(DitingLanguageMode.AUTO) }
    var glossary by remember { mutableStateOf("") }
    var showAllMarkers by remember(selectedSession?.id) { mutableStateOf(false) }
    var markerPage by remember(selectedSession?.id) { mutableStateOf(0) }
    var showAllSegments by remember(selectedSession?.id) { mutableStateOf(false) }
    var transcriptQuery by remember(selectedSession?.id) { mutableStateOf("") }
    var transcriptPage by remember(selectedSession?.id) { mutableStateOf(0) }
    var historyQuery by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf(DitingHistoryFilter.ALL) }
    var collapsedHistoryDays by remember { mutableStateOf(emptySet<String>()) }
    var timelineFilter by remember(selectedSession?.id) { mutableStateOf(DitingTimelineFilter.ALL) }
    var transcriptionEnabled by remember(selectedSession?.id) { mutableStateOf(true) }
    var aiAnnotationForSession by remember(selectedSession?.id, aiAnnotationConfigured) { mutableStateOf(aiAnnotationConfigured) }
    val active = creatingSession || selectedSession?.status in setOf("draft", "recording", "paused", "processing")
    val normalizedHistoryQuery = historyQuery.trim()
    val historySessions = sessions.filter { session ->
        val matchesQuery = normalizedHistoryQuery.isBlank() || session.title.contains(normalizedHistoryQuery, ignoreCase = true)
        matchesQuery && historyFilter.matches(session.status)
    }
    val historyGroups = historySessions.groupBy(::historyGroupLabel)

    LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                BrandHeader("谛听", "课堂录音、实时文字和课堂提醒", icon = Icons.Outlined.Mic)
            }
            item { Text("录下课堂，之后可以回听、看文字和找重点。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (active && selectedSession != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(selectedSession.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("状态：${statusLabel(selectedSession.status)}")
                            Text("模式：${ditingModeLabel(selectedSession.mode)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("文字：${transcriptionLabel(selectedSession.transcriptionEngine)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(aiAnnotationLabel(selectedSession.aiAnnotationEnabled, aiAnnotationConfigured), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            if (!selectedSession.errorMessage.isNullOrBlank()) {
                                Text("说明：${selectedSession.errorMessage}", color = MaterialTheme.colorScheme.error)
                            }
                            Text("录音时长：${timeLabel(selectedSession.durationMillis)}", style = MaterialTheme.typography.titleMedium)
                            Text("已记录 ${segments.size} 段声音 · ${markers.size} 个重点/提问", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("实时文字", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("重点和提问会自动整理，也可以随时手动补充。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            segments.takeLast(4).forEach { segment ->
                                Text(
                                    "${timeLabel(segment.startMillis)}–${timeLabel(segment.endMillis)}  ${segmentTextLabel(segment)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            markers.takeLast(4).forEach { marker ->
                                val question = marker.type.contains("question")
                                Text(
                                    "${if (question) "⚠" else "★"} ${timeLabel(marker.positionMillis)}  ${marker.title}: ${marker.note.ifBlank { if (question) "请留意课堂提问" else "已自动标记" }}",
                                    color = if (question) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
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
            }
            if (selectedSession != null && !active) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${selectedSession.title} · 课堂记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("${statusLabel(selectedSession.status)} · ${timeLabel(selectedSession.durationMillis)} · ${segments.size} 段声音 · ${markers.size} 个重点/提问", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("模式：${ditingModeLabel(selectedSession.mode)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("文字：${transcriptionLabel(selectedSession.transcriptionEngine)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(aiAnnotationLabel(selectedSession.aiAnnotationEnabled, aiAnnotationConfigured), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            if (!selectedSession.errorMessage.isNullOrBlank()) {
                                Text("说明：${selectedSession.errorMessage}", color = MaterialTheme.colorScheme.error)
                            }
                            Text("这节课已保存，可以回听或分享。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { showAllSegments = true; transcriptPage = 0 },
                                    modifier = Modifier.weight(1f),
                                ) { Text("查看文字记录") }
                                OutlinedButton(
                                    onClick = { onShareTranscript(selectedSession, segments, markers) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("分享文字") }
                            }
                            if (offlinePackState.installed && segments.any { it.text.isBlank() && it.audioPath.isNotBlank() }) {
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
                                 Text(if (isReanalyzingSession) "正在重新分析重点和提问…" else "重新分析重点和提问")
                             }
                             Text("如果发现重点或提问漏掉了，可以重新分析这节课。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

                            val highlightCount = markers.count { it.type.contains("highlight") }
                            val questionCount = markers.count { it.type.contains("question") }
                            val filteredMarkers = when (timelineFilter) {
                                DitingTimelineFilter.ALL -> markers
                                DitingTimelineFilter.HIGHLIGHT -> markers.filter { it.type.contains("highlight") }
                                DitingTimelineFilter.QUESTION -> markers.filter { it.type.contains("question") }
                            }
                            val markersBySegment = remember(segments, markers) { linkMarkersToSegments(segments, markers) }
                             val filteredSegments = remember(segments, markersBySegment, timelineFilter) {
                                 when (timelineFilter) {
                                     DitingTimelineFilter.ALL -> segments
                                     DitingTimelineFilter.HIGHLIGHT -> segments.filter { markersBySegment[it.id].orEmpty().any { marker -> marker.type.contains("highlight") } }
                                     DitingTimelineFilter.QUESTION -> segments.filter { markersBySegment[it.id].orEmpty().any { marker -> marker.type.contains("question") } }
                                 }
                             }

                            Text("课堂雷达 · ${markers.size} 个标记", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "重点 $highlightCount · 提问 $questionCount；筛选后可沿时间线直接回听",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DitingTimelineFilter.entries.forEach { filter ->
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
                                    if (markers.isEmpty()) "暂未发现自动重点或提问；录音和文字仍已保存。"
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
                                     filteredMarkers.takeLast(8)
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
                                Text("暂无文字分段。若设备不支持本地识别，录音仍会保存在上方，可直接回听。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            } else if (selectedSession.audioPath.isNotBlank()) {
                                Text("带有“重点”或“提问”标签的文字，可以点击直接回听。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                             if (segments.isNotEmpty()) {
                              if (!showAllSegments && transcriptQuery.isBlank() && segments.size > 20) {
                                  Text(
                                      "当前显示最近 20 条文字；可以搜索关键词，或按页查看更早内容。",
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
                                 searchableSegments.takeLast(20)
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
                             if (searchableSegments.size > 20) {
                                 TextButton(onClick = { showAllSegments = !showAllSegments; transcriptPage = 0 }) {
                                     Text(if (showAllSegments) "收起文字" else "按页查看全部 ${searchableSegments.size} 条记录")
                             }
                                 }
                             }
                            }
                        }
                    }
                }
            if (!active) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("开始一节课堂", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(title, { title = it }, label = { Text("课程或课堂名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DitingMode.entries.forEach { item -> FilterChip(mode == item, { mode = item }, label = { Text(item.label) }) }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DitingLanguageMode.entries.forEach { item ->
                                    FilterChip(language == item, { language = item }, label = { Text(item.label) })
                                }
                            }
                            OutlinedTextField(
                                value = glossary,
                                onValueChange = { glossary = it },
                                label = { Text("课上专有名词（可选）") },
                                supportingText = { Text("例如：Cache、Transformer、傅里叶变换，帮助识别专业词") },
                                modifier = Modifier.fillMaxWidth(),
                            )

                             TextButton(onClick = onOpenResources, modifier = Modifier.fillMaxWidth()) { Text("安装离线识别") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = aiAnnotationForSession,
                                    onCheckedChange = { aiAnnotationForSession = it },
                                    enabled = aiAnnotationConfigured,
                                )
                                Column {
                                    Text("本节课 AI 课堂整理")
                                    Text(
                                        when {
                                            !aiAnnotationEnabled -> "请先在“我的”里完成 AI 设置"
                                            !aiAnnotationConfigured -> "AI 还没有准备好，完成设置后即可使用"
                                            else -> "录音结束后，AI 会帮你整理重点和提问"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = transcriptionEnabled,
                                    onCheckedChange = { transcriptionEnabled = it },
                                )
                                Column {
                                    Text("课堂文字记录")
                                    Text(
                                        when {
                                            !transcriptionEnabled -> "已关闭，只保存录音"
                                            prefersOfflineDitingTranscription(language.key, offlinePackState.installed) -> "已准备好，优先使用离线识别"
                                            localRecognitionAvailable -> "已准备好，使用手机识别"
                                            offlinePackState.installed -> "已准备好，使用离线识别"
                                            cloudTranscriptionConfigured -> "已准备好，使用联网识别"
                                            else -> "暂未准备好，仍可录音，之后可补充文字"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        "戴耳机也可以继续录音、刷视频或玩游戏，录音和播放互不影响。",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (!notificationsAvailable) {
                                        Text("通知权限未开启：水课问题提醒可能无法在后台弹出", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Button(
                                onClick = { onStart(title, mode, language, glossary, transcriptionEnabled, aiAnnotationForSession) },
                                enabled = !creatingSession,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (creatingSession) "正在准备录音…" else "开始录音") }
                        }
                    }
                }
            }
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
                        OutlinedTextField(
                            value = historyQuery,
                            onValueChange = { historyQuery = it },
                            label = { Text("搜索课堂名称") },
                            supportingText = { Text("输入课程名，快速找到以前的录音") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DitingHistoryFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = historyFilter == filter,
                                onClick = { historyFilter = filter },
                                label = { Text(filter.label) },
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
                        val deletable = session.status !in setOf("recording", "paused", "processing")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 16.dp, end = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                                    Text(session.title, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${sessionClockLabel(session)} · ${timeLabel(session.durationMillis)} · ${ditingModeLabel(session.mode)} · ${statusLabel(session.status)}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onSelect(session.id) }) { Text("查看") }
                                IconButton(onClick = { onDelete(session.id) }, enabled = deletable) { Icon(Icons.Outlined.Delete, contentDescription = if (deletable) "删除" else "录音中不可删除") }
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
    "android_on_device" -> "已生成文字"
    "offline_onnx" -> "已生成文字"
    "cloud" -> "已生成文字"
    "audio_only" -> "暂未生成文字"
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


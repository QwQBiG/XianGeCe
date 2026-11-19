package win.iqwqi.xiangece.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import win.iqwqi.xiangece.ui.theme.LocalCompanionAppearance
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import win.iqwqi.xiangece.data.local.HabitCheckinEntity
import win.iqwqi.xiangece.data.local.HabitTemplateEntity
import win.iqwqi.xiangece.data.local.CustomQuoteEntity
import win.iqwqi.xiangece.data.BuiltInQuotes
import win.iqwqi.xiangece.domain.model.HabitFrequency
import win.iqwqi.xiangece.ui.AppUiState
import win.iqwqi.xiangece.ui.HabitStats
import win.iqwqi.xiangece.ui.components.AppConfirmDialog
import win.iqwqi.xiangece.ui.components.AppFormSheet
import win.iqwqi.xiangece.ui.components.AppTextField
import win.iqwqi.xiangece.ui.components.BrandHeader
import win.iqwqi.xiangece.ui.components.EmptyCard
import win.iqwqi.xiangece.ui.components.PaperCard

private val habitColors = listOf(
    0xFF52796F, 0xFF4C6A92, 0xFF7B6D9C, 0xFF9B5D66,
    0xFFB07042, 0xFF6D7E45, 0xFF397C8C, 0xFF795548,
)

@Composable
fun HabitsScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    onCreateHabit: (String, String, Long, HabitFrequency) -> Unit,
    onUpdateHabit: (Long, String, String, Long, HabitFrequency) -> Unit,
    onDeleteHabit: (Long) -> Unit,
    onToggleCheckin: (Long) -> Unit,
    onDismissCelebration: () -> Unit = {},
) {
    val habits = state.habits
    val checkins = state.habitCheckins
    val stats = state.habitStats
    val customQuotes = state.customQuotes
    val haptic = LocalHapticFeedback.current
    var editing by remember { mutableStateOf<HabitTemplateEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<HabitTemplateEntity?>(null) }
    var showCreator by remember { mutableStateOf(false) }
    var celebrateVisible by remember { mutableStateOf(false) }
    var showHeatmap by remember { mutableStateOf(false) }

    // 未完成的排前面，已完成的排后面
    val sortedHabits = remember(habits, stats.todayCompletedHabitIds) {
        habits.sortedBy { it.id in stats.todayCompletedHabitIds }
    }

    // 今日全部完成：只在"第一次从有变全"时触发一次庆祝，点击"继续坚持"后当天不再出现
    val allDone = sortedHabits.isNotEmpty() && sortedHabits.all { it.id in stats.todayCompletedHabitIds }
    val todayEpochDay = remember { LocalDate.now(ZoneId.systemDefault()).toEpochDay() }
    val celebrated = state.lastCelebratedEpochDay == todayEpochDay
    val dismissed = state.celebrationDismissedEpochDay == todayEpochDay
    val shouldShowCelebrate = allDone && celebrated && !dismissed
    LaunchedEffect(shouldShowCelebrate) {
        celebrateVisible = shouldShowCelebrate
        if (shouldShowCelebrate) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Keep the streak reminder visible without a continuous animation.
    val hasStreak = stats.currentStreak > 0
    val todayIsEmpty = stats.todayCompletedHabitIds.isEmpty()
    val streakWillBreak = hasStreak && todayIsEmpty

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize().testTag("habits-list"),
            contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 8.dp,
                    end = 20.dp,
                    bottom = contentPadding.calculateBottomPadding() + 96.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
            BrandHeader(
                title = "厚积",
                subtitle = "把想坚持的事，慢慢变成日常",
                icon = Icons.Outlined.AutoGraph,
            )
            }
            item(key = "statistics") {
            HabitStatsHeader(
                stats = stats,
                totalHabits = sortedHabits.size,
                streakWillBreak = streakWillBreak,
                customQuotes = customQuotes,
                onOpenHeatmap = { showHeatmap = true },
            )
            }

            if (sortedHabits.isEmpty()) {
                item {
                EmptyCard(
                    "千里之行，始于足下",
                    "点击下方加号，添加一件想长期坚持的小事。",
                )
                }
            } else {
                    items(sortedHabits, key = { it.id }) { habit ->
                        val motion = LocalCompanionAppearance.current.motionEnabled
                        val isDoneToday = habit.id in stats.todayCompletedHabitIds
                        val perHabit = remember(habit.id, checkins.size) {
                            computePerHabitStats(habit, checkins)
                        }
                        HabitRow(
                            modifier = if (motion) Modifier.animateItem() else Modifier,
                            habit = habit,
                            isDoneToday = isDoneToday,
                            streakDays = perHabit.streakDays,
                            totalCount = perHabit.totalCount,
                            daysSinceCreation = perHabit.daysSinceCreation,
                            onToggle = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleCheckin(habit.id)
                            },
                            onLongPress = { editing = habit },
                        )
                    }
            }
        }

        FloatingActionButton(
            onClick = { showCreator = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "新增长期事项")
        }
    }

    if (showCreator) {
        HabitEditorDialog(
            initial = null,
            onDismiss = { showCreator = false },
            onSave = { title, description, color, frequency ->
                onCreateHabit(title, description, color, frequency)
                showCreator = false
            },
        )
    }
    editing?.let { habit ->
        HabitEditorDialog(
            initial = habit,
            onDismiss = { editing = null },
            onSave = { title, description, color, frequency ->
                onUpdateHabit(habit.id, title, description, color, frequency)
                editing = null
            },
            onDelete = {
                pendingDelete = habit
                editing = null
            },
        )
    }
    pendingDelete?.let { habit ->
        AppConfirmDialog(
            title = "删除这个习惯？",
            message = "已积累的 ${state.habitCheckins.count { it.habitId == habit.id }} 次打卡记录会一并删除，且无法恢复。",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDeleteHabit(habit.id)
                pendingDelete = null
            },
            confirmLabel = "删除",
            isDanger = true,
        )
    }

    if (celebrateVisible) {
        CelebrateSheet(
            stats = stats,
            onDismiss = {
                celebrateVisible = false
                onDismissCelebration()
            },
        )
    }

    if (showHeatmap) {
        YearlyHeatmapSheet(
            checkins = checkins,
            onDismiss = { showHeatmap = false },
        )
    }
}

/**
 * 累计与连续天数为主，完成次数、今日进度和箴言为辅。
 * 整体可点击，打开年度热力图弹层。
 */
@Composable
private fun HabitStatsHeader(
    stats: HabitStats,
    totalHabits: Int,
    streakWillBreak: Boolean,
    customQuotes: List<CustomQuoteEntity>,
    onOpenHeatmap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headerShape = RoundedCornerShape(24.dp)
    val rippleIndication = ripple(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    val interactionSource = remember { MutableInteractionSource() }

    val motion = LocalCompanionAppearance.current.motionEnabled
    val streakAccent = if (streakWillBreak) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    // 每日箴言（按日期取，相同日期相同）。用户自定义箴言优先。
    val todayQuote = remember(customQuotes) { getDailyQuote(customQuotes) }

    PaperCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(headerShape)
            .clickable(
                interactionSource = interactionSource,
                indication = rippleIndication,
                onClick = onOpenHeatmap,
            ),
        elevation = 0.dp,
    ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(Modifier.weight(1f), Icons.Outlined.AutoGraph, "${stats.totalDays}", "累计天数")
                StatTile(Modifier.weight(1f), Icons.Outlined.LocalFireDepartment, "${stats.currentStreak}", "连续天数", streakAccent)
            }
            Spacer(Modifier.height(12.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("累计完成 ${stats.totalCheckins} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("本月完成 ${stats.monthCheckins} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            val todayTotal = totalHabits
            val todayDone = stats.todayCompletedHabitIds.size.coerceAtMost(todayTotal)
            val progress = todayDone.toFloat() / todayTotal.coerceAtLeast(1)
            val animatedProgress by animateFloatAsState(progress, tween(if (motion) 200 else 0), label = "habitProgress")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(
                    progress = { animatedProgress }, modifier = Modifier.weight(1f).height(6.dp),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    strokeCap = StrokeCap.Round,
                )
                Text("今日 $todayDone/$todayTotal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 每日箴言
            Spacer(Modifier.height(10.dp))
            Text(
                todayQuote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String,
    label: String,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(2.dp))
        }
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 完成时短暂改变底色与勾选标记，列表负责位置过渡。
 * 历史热力图由顶栏统计打开。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HabitRow(
    habit: HabitTemplateEntity,
    isDoneToday: Boolean,
    streakDays: Int,
    totalCount: Int,
    daysSinceCreation: Int,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val habitColor = Color(habit.colorArgb)
    val motion = LocalCompanionAppearance.current.motionEnabled
    val backgroundColor by animateColorAsState(
        targetValue = if (isDoneToday) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
        animationSpec = tween(if (motion) 180 else 0),
        label = "habitCardBg",
    )

    val cardShape = RoundedCornerShape(20.dp)
    val rippleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val rippleIndication = ripple(color = rippleColor)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rippleIndication,
                onClick = onToggle,
                onLongClick = onLongPress,
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
            // The colored completion control keeps each habit recognizable.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isDoneToday) habitColor else Color.Transparent,
                        CircleShape,
                    )
                    .border(
                        width = if (isDoneToday) 0.dp else 2.dp,
                        color = if (isDoneToday) Color.Transparent else habitColor.copy(alpha = 0.6f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isDoneToday,
                    enter = scaleIn(initialScale = 0.85f, animationSpec = tween(if (motion) 160 else 0)) + fadeIn(tween(if (motion) 160 else 0)),
                    exit = fadeOut(animationSpec = tween(if (motion) 100 else 0)),
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "今日已完成",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            // 中间文本区域
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    habit.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDoneToday) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (isDoneToday) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (habit.description.isNotBlank()) {
                    Text(
                        habit.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    maxItemsInEachRow = 3,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HabitMetaChip("连续 $streakDays 天", MaterialTheme.colorScheme.error, Icons.Outlined.LocalFireDepartment)
                    HabitMetaChip("累计 $totalCount 次", MaterialTheme.colorScheme.primary, Icons.Outlined.TaskAlt)
                    HabitMetaChip("已坚持 $daysSinceCreation 天", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Outlined.Schedule)
                }
            }
            IconButton(onClick = onLongPress) {
                Icon(Icons.Outlined.Edit, "编辑长期事项", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
                } // closes inner Row (content row)
            } // closes inner Box (weight(1f) wrapper)
        } // closes outer Row (color strip + content)
    }
}

@Composable
private fun HabitMetaChip(text: String, tint: Color, icon: ImageVector) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 年度打卡热力图弹层（GitHub 风格）。
 *
 * - 横轴：一年 52~53 周，可横向滑动。
 * - 纵轴：每周 7 天（周日到周六）。
 * - 颜色等级：按当天 [checkins] 的总打卡条数分 4 级（0 / 1 / 2-3 / 4+），
 *   深浅用主题 primary 的不同 alpha，未来日期不渲染。
 * - 月份分隔用月份标签做轻量提示。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun YearlyHeatmapSheet(
    checkins: List<HabitCheckinEntity>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = LocalDate.now(ZoneId.systemDefault())
    val year = today.year
    val firstDayOfYear = LocalDate.of(year, 1, 1)
    val totalDays = if (firstDayOfYear.isLeapYear) 366 else 365
    val firstDayOfWeek = firstDayOfYear.dayOfWeek.value % 7

    // 按天聚合打卡数：epochDay -> count
    val countByDay = remember(checkins.size) {
        checkins.groupingBy { it.checkinDateEpochDay }.eachCount()
    }

    // 总打卡天数与最大单日打卡数（用于图例与统计展示）
    val totalActiveDays = countByDay.size
    val maxDailyCount = countByDay.values.maxOrNull() ?: 0
    val totalCheckins = checkins.size

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                "${year} 年打卡热力图",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "累计打卡 $totalCheckins 次 · 坚持了 $totalActiveDays 天" +
                    (if (maxDailyCount > 0) " · 单日最多 $maxDailyCount 项" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // 热力图主体：横向可滚动，外包边框避免太空
            val hasAnyData = countByDay.isNotEmpty()
            var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                YearHeatmapCanvas(
                    year = year,
                    totalDays = totalDays,
                    countByDay = countByDay,
                    today = today,
                    onCellClick = { col, row ->
                        val dayOffset = col * 7 + row - firstDayOfWeek
                        if (dayOffset in 0 until totalDays) {
                            val date = firstDayOfYear.plusDays(dayOffset.toLong())
                            if (!date.isAfter(today)) {
                                selectedCell = if (selectedCell == col to row) null else (col to row)
                            }
                        }
                    },
                )
                selectedCell?.let { (col, row) ->
                    val dayOffset = col * 7 + row - firstDayOfWeek
                    if (dayOffset in 0 until totalDays) {
                        val date = firstDayOfYear.plusDays(dayOffset.toLong())
                        val count = countByDay[date.toEpochDay()] ?: 0
                        val label = "${date.monthValue}月${date.dayOfMonth}日：${if (count > 0) "完成 $count 项" else "未打卡"}"
                        val cellTop = 16.dp + (row * 14).dp
                        val tooltipWidth = 132.dp
                        val tooltipX = maxOf(0, col * 14 - 44).dp
                        val tooltipY = if (row >= 3) cellTop - 34.dp else cellTop + 16.dp
                        Box(
                            modifier = Modifier
                                .offset(x = tooltipX, y = tooltipY)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.96f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.width(tooltipWidth),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // 空引导
            if (!hasAnyData) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "开始打卡，让这里染上颜色 ✨",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))
            // 图例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "少",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                listOf(0.12f, 0.35f, 0.65f, 0.95f).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(12.dp)
                            .background(
                                if (alpha == 0.12f) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                },
                                RoundedCornerShape(3.dp),
                            ),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "多",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 用 Canvas 绘制年度热力图。
 *
 * 布局：每周一列，从上到下 7 个格子（周日到周六）。
 * 格子 12dp、间距 2dp。月份标签放在顶部，对齐到该月第一周所在的列。
 * 今日格子加主题色描边高亮，点击格子通过 [onCellClick] 回调。
 */
@Composable
private fun YearHeatmapCanvas(
    year: Int,
    totalDays: Int,
    countByDay: Map<Long, Int>,
    today: LocalDate,
    onCellClick: (col: Int, row: Int) -> Unit = { _, _ -> },
) {
    val cellSize = 12.dp
    val spacing = 2.dp
    val cellPx = with(androidx.compose.ui.platform.LocalDensity.current) { cellSize.toPx() }
    val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { spacing.toPx() }
    val stepPx = cellPx + spacingPx
    val labelHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }

    val firstDay = LocalDate.of(year, 1, 1)
    val firstDayOfWeek = firstDay.dayOfWeek.value % 7
    val totalCells = totalDays + firstDayOfWeek
    val weekCount = (totalCells + 6) / 7
    val widthDp = (weekCount * (cellSize.value + spacing.value)).dp
    val heightDp = (7 * (cellSize.value + spacing.value) + 14).dp

    // 计算今日所在的 col/row
    val todayOffset = today.dayOfYear - 1 + firstDayOfWeek
    val todayCol = todayOffset / 7
    val todayRow = todayOffset % 7

    val monthMarkers = remember(year, today) {
        val markers = mutableMapOf<Int, String>()
        var lastMonth = -1
        for (dayOffset in 0 until totalDays) {
            val date = firstDay.plusDays(dayOffset.toLong())
            if (date.isAfter(today)) break
            val weekIndex = (dayOffset + firstDayOfWeek) / 7
            if (date.monthValue != lastMonth) {
                markers[weekIndex] = "${date.monthValue}月"
                lastMonth = date.monthValue
            }
        }
        markers
    }

    val primary = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.primary

    Box {
        Canvas(
            modifier = Modifier.size(width = widthDp, height = heightDp),
        ) {
            // 月份标签
            monthMarkers.forEach { (weekIndex, label) ->
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    weekIndex * stepPx,
                    labelHeightPx,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(140, 0, 0, 0)
                        textSize = labelHeightPx * 0.8f
                    },
                )
            }

            // 绘制每个格子
            for (dayOffset in 0 until totalDays) {
                val date = firstDay.plusDays(dayOffset.toLong())
                val epochDay = date.toEpochDay()
                val isFuture = date.isAfter(today)
                val cellIndex = dayOffset + firstDayOfWeek
                val col = cellIndex / 7
                val row = cellIndex % 7
                val x = col * stepPx
                val y = row * stepPx + labelHeightPx + spacingPx
                val isToday = date == today

                val color = when {
                    isFuture -> Color.Transparent
                    else -> {
                        val count = countByDay[epochDay] ?: 0
                        when {
                            count == 0 -> emptyColor
                            count == 1 -> primary.copy(alpha = 0.35f)
                            count in 2..3 -> primary.copy(alpha = 0.65f)
                            else -> primary.copy(alpha = 0.95f)
                        }
                    }
                }
                if (color != Color.Transparent) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(cellPx, cellPx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                    )
                }
                // 今日高亮
                if (isToday) {
                    drawRoundRect(
                        color = highlightColor,
                        topLeft = Offset(x - 1.5f, y - 1.5f),
                        size = androidx.compose.ui.geometry.Size(cellPx + 3f, cellPx + 3f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                        style = Stroke(width = 1.5f),
                    )
                }
            }
        }
        // 覆盖一层透明的点击层，处理格子点击（Canvas 在嵌套滚动容器内 pointerInput 易失效）
        Box(
            modifier = Modifier
                .size(width = widthDp, height = heightDp)
                .pointerInput(onCellClick) {
                    detectTapGestures { offset ->
                        val col = (offset.x / stepPx).toInt()
                        val row = ((offset.y - labelHeightPx - spacingPx) / stepPx).toInt()
                        if (col >= 0 && col < weekCount && row in 0..6) {
                            onCellClick(col, row)
                        }
                    }
                },
        )
    }
}

/**
 * 全部完成时弹出的庆祝层。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CelebrateSheet(stats: HabitStats, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val today = LocalDate.now(ZoneId.systemDefault())
    val appearance = win.iqwqi.xiangece.ui.theme.LocalCompanionAppearance.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (appearance.showsFeedback) {
                win.iqwqi.xiangece.ui.companion.CompanionSticker(
                    win.iqwqi.xiangece.ui.companion.CompanionCue.HAPPY, Modifier.size(148.dp),
                )
            } else {
                Text("✓", fontSize = 42.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text("今日厚积已成", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${today.year}年${today.monthValue}月${today.dayOfMonth}日",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("共完成 ${stats.todayCompletedHabitIds.size} 项 · 连续坚持第 ${stats.currentStreak} 天",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("继续坚持") }
        }
    }
}

/**
 * 每日箴言池。相同日期返回相同内容，保证同一天显示一致。
 * 后续用户可以指定替换内容。
 */
private val dailyQuotes = BuiltInQuotes.entries.map { it.text }

private fun getDailyQuote(customQuotes: List<CustomQuoteEntity> = emptyList()): String {
    val today = LocalDate.now(ZoneId.systemDefault())
    // 用户自定义 + 内置合并；若有用户自定义，它们优先（排在前面）。
    val userQuotes = customQuotes.filter { !it.isBuiltIn }.map { it.text }
    val builtInQuotes = customQuotes.filter { it.isBuiltIn }.map { it.text }
    val pool = if (customQuotes.isNotEmpty()) (userQuotes + builtInQuotes) else dailyQuotes
    if (pool.isEmpty()) return "千里之行，始于足下。"
    val index = today.dayOfYear % pool.size
    return pool[index]
}

@Composable
private fun HabitEditorDialog(
    initial: HabitTemplateEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, HabitFrequency) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var color by remember(initial) { mutableStateOf(initial?.colorArgb ?: habitColors.first()) }
    var frequency by remember(initial) { mutableStateOf(initial?.frequency ?: HabitFrequency.DAILY) }
    AppFormSheet(
        title = if (initial == null) "新增长期事项" else "修改长期事项",
        subtitle = if (initial == null) "坚持从一件小事开始" else null,
        onConfirm = { onSave(title.trim(), description.trim(), color, frequency) },
        onDismiss = onDismiss,
        confirmLabel = "保存",
        confirmEnabled = title.isNotBlank(),
    ) {
        AppTextField(
            value = title,
            onValueChange = { title = it },
            label = "事项名称",
        )
        AppTextField(
            value = description,
            onValueChange = { description = it },
            label = "说明（可选）",
            singleLine = false,
            minLines = 2,
        )
        Text("颜色", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            habitColors.forEach { candidate ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(candidate), CircleShape)
                        .border(
                            width = if (color == candidate) 3.dp else 1.dp,
                            color = if (color == candidate) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { color = candidate },
                )
            }
        }
        Text("频率", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = frequency == HabitFrequency.DAILY,
                onClick = { frequency = HabitFrequency.DAILY },
                label = { Text("每天") },
            )
            FilterChip(
                selected = frequency == HabitFrequency.WEEKLY,
                onClick = { frequency = HabitFrequency.WEEKLY },
                label = { Text("每周") },
            )
            FilterChip(
                selected = frequency == HabitFrequency.X_TIMES_PER_WEEK,
                onClick = { frequency = HabitFrequency.X_TIMES_PER_WEEK },
                label = { Text("自定义") },
            )
        }
        if (onDelete != null) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("删除此长期事项") }
        }
    }
}

private data class PerHabitStats(
    val streakDays: Int,
    val totalCount: Int,
    val daysSinceCreation: Int,
)

/**
 * 计算单条长期事项的连续天数、累计次数与坚持天数。
 *
 * - 连续天数：从今天（或昨天，给一天宽限）往前数，对该 habitId 不中断的天数。
 * - 坚持天数：从创建日起到今天跨越的总天数，体现整体投入时长。
 * 这里放在 UI 层用 remember 计算，避免 ViewModel 过度耦合展示数据。
 */
private fun computePerHabitStats(
    habit: HabitTemplateEntity,
    allCheckins: List<HabitCheckinEntity>,
): PerHabitStats {
    val mine = allCheckins.filter { it.habitId == habit.id }
    val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
    // 坚持天数：从创建日起到今天，至少为 1
    val createdEpochDay = (habit.createdAtEpochMillis / 86_400_000L).coerceAtMost(today)
    val daysSinceCreation = ((today - createdEpochDay) + 1).toInt().coerceAtLeast(1)
    if (mine.isEmpty()) return PerHabitStats(
        streakDays = 0,
        totalCount = 0,
        daysSinceCreation = daysSinceCreation,
    )
    val days = mine.map { it.checkinDateEpochDay }.toSet()
    var streakCursor = today
    if (streakCursor !in days) {
        streakCursor -= 1
        if (streakCursor !in days) streakCursor = today
    }
    var streakDays = 0
    while (streakCursor in days) {
        streakDays += 1
        streakCursor -= 1
    }
    return PerHabitStats(
        streakDays = streakDays,
        totalCount = mine.size,
        daysSinceCreation = daysSinceCreation,
    )
}

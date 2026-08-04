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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    // 今日还没完成但有历史连续，触发"快断签"提醒脉冲
    val hasStreak = stats.currentStreak > 0
    val todayIsEmpty = stats.todayCompletedHabitIds.isEmpty()
    val streakWillBreak = hasStreak && todayIsEmpty

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandHeader(
                title = "厚积",
                subtitle = "每日坚持与习惯记录",
                icon = Icons.Outlined.AutoGraph,
            )
            HabitStatsHeader(
                stats = stats,
                totalHabits = sortedHabits.size,
                streakWillBreak = streakWillBreak,
                customQuotes = customQuotes,
                onOpenHeatmap = { showHeatmap = true },
            )

            if (sortedHabits.isEmpty()) {
                EmptyCard(
                    "千里之行，始于足下",
                    "点击下方加号，记录今日第一件坚持之事。每一次打卡，都是未来的回响。",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(sortedHabits, key = { it.id }) { habit ->
                        val isDoneToday = habit.id in stats.todayCompletedHabitIds
                        val perHabit = remember(habit.id, checkins.size) {
                            computePerHabitStats(habit, checkins)
                        }
                        HabitRow(
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
        }

        FloatingActionButton(
            onClick = { showCreator = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
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
            title = "删除这条长期事项？",
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
 * 顶栏统计：4 个核心数字 + 今日进度条 + 每日箴言。
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

    // 连续天数快断时的脉冲动画
    val streakPulse by animateFloatAsState(
        targetValue = if (streakWillBreak) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "streakPulse",
    )
    val streakAccent = if (streakWillBreak) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.4f + streakPulse * 0.6f)
    } else {
        MaterialTheme.colorScheme.error
    }

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
        elevation = 4.dp,
    ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                val animatedTotalDays by animateFloatAsState(targetValue = stats.totalDays.toFloat(), label = "totalDaysTile")
                StatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.AutoGraph,
                    value = "${animatedTotalDays.toInt()}",
                    label = "累计天数",
                )
                val animatedStreak by animateFloatAsState(targetValue = stats.currentStreak.toFloat(), label = "streakTile")
                StatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.LocalFireDepartment,
                    value = "${animatedStreak.toInt()}",
                    label = "连续天数",
                    accent = streakAccent,
                )
                val animatedTotalCheckins by animateFloatAsState(targetValue = stats.totalCheckins.toFloat(), label = "totalCheckinsTile")
                StatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Check,
                    value = "${animatedTotalCheckins.toInt()}",
                    label = "累计完成",
                )
                val animatedMonthCheckins by animateFloatAsState(targetValue = stats.monthCheckins.toFloat(), label = "monthCheckinsTile")
                StatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.CalendarMonth,
                    value = "${animatedMonthCheckins.toInt()}",
                    label = "本月完成",
                )
            }
            Spacer(Modifier.height(12.dp))
            val todayTotal = totalHabits.coerceAtLeast(1)
            val todayDone = stats.todayCompletedHabitIds.size.coerceAtMost(todayTotal)
            val progress = (todayDone.toFloat() / todayTotal).coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(targetValue = progress, label = "habitProgress")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    "今日 $todayDone/$todayTotal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
 * 单条长期事项的卡片（增强版）：
 * 1. 点击完成 = "墨迹扩散"动效（径向渐变从中心向外晕染）。
 * 2. 卡片背景缓动过渡（300ms tween）。
 * 3. 数字跳动由外层 count-up 驱动。
 *
 * 注：30 天热力图已移至顶栏"厚积"入口打开的年度总览弹层，单卡片不再展示。
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
) {
    val habitColor = Color(habit.colorArgb)
    val stampAnimatable = remember { Animatable(0f) }
    LaunchedEffect(isDoneToday) {
        if (isDoneToday) {
            stampAnimatable.snapTo(0f)
            stampAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 500
                    0f at 0 with LinearEasing
                    1.2f at 250 with LinearEasing
                    1f at 500 with LinearEasing
                },
            )
        } else {
            stampAnimatable.snapTo(0f)
        }
    }

    // Card scale + background color transition (300ms smooth tween)
    val cardScale by animateFloatAsState(
        targetValue = if (isDoneToday) 1f else 0.985f,
        animationSpec = tween(durationMillis = 300),
        label = "habitCardScale",
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isDoneToday) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        },
        animationSpec = tween(durationMillis = 300),
        label = "habitCardBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isDoneToday) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
        },
        animationSpec = tween(durationMillis = 300),
        label = "habitCardBorder",
    )

    val cardShape = RoundedCornerShape(24.dp)
    val rippleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val rippleIndication = ripple(color = rippleColor)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clip(cardShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rippleIndication,
                onClick = onToggle,
                onLongClick = onLongPress,
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = CardDefaults.outlinedCardBorder(enabled = true).copy(
            brush = SolidColor(borderColor),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧色条：4dp 宽渐变，用 ConstraintLayout/Row 嵌套实现整高
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(habitColor.copy(alpha = 0.7f), habitColor.copy(alpha = 0.25f)),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
            // 左侧印章：墨迹扩散动效
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
                    enter = scaleIn(initialScale = 0.3f) + fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(200)),
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "今日已完成",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                // 墨迹扩散：径向渐变模拟墨晕在纸上散开的效果
                if (stampAnimatable.value > 0f && stampAnimatable.value < 1.5f) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val progress = stampAnimatable.value.coerceIn(0f, 1f)
                        val center = Offset(size.minDimension / 2f, size.minDimension / 2f)
                        val maxRadius = size.minDimension * (0.4f + progress * 1.8f)
                        // 多层墨晕：外圈大而淡，内圈小而浓
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    habitColor.copy(alpha = 0.35f * (1f - progress)),
                                    habitColor.copy(alpha = 0.08f * (1f - progress)),
                                    Color.Transparent,
                                ),
                            ),
                            radius = maxRadius,
                            center = center,
                        )
                        // 中心残留的一点浓墨
                        drawCircle(
                            color = habitColor.copy(alpha = 0.5f * (1f - progress)),
                            radius = size.minDimension * 0.25f * (1f - progress * 0.6f),
                            center = center,
                        )
                    }
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
                    maxLines = 1,
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
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        onClick = onLongPress,
                    ),
            )
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
            .border(0.5.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
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
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
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
    val particleProgress = remember { Animatable(0f) }
    val today = LocalDate.now(ZoneId.systemDefault())
    LaunchedEffect(Unit) {
        particleProgress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val count = 6
                    val progress = particleProgress.value
                    repeat(count) { i ->
                        val angle = Math.PI * 2 * i / count
                        val distance = 30f + progress * 90f
                        val particleX = center.x + (Math.cos(angle) * distance).toFloat()
                        val particleY = center.y + (Math.sin(angle) * distance).toFloat()
                        drawCircle(
                            color = Color(0xFFA4834E).copy(alpha = (1f - progress) * 0.8f),
                            radius = (1f - progress) * 8f + 2f,
                            center = Offset(particleX, particleY),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFB4493E), Color(0xFF8C362E)),
                            ),
                            shape = CircleShape,
                        )
                        .border(3.dp, Color(0xFFA4834E), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "完",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 38.sp,
                    )
                }
            }
            Text("今日厚积已成", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${today.year}年${today.monthValue}月${today.dayOfMonth}日",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Text(
                "共完成 ${stats.todayCompletedHabitIds.size} 项 · 连续坚持第 ${stats.currentStreak} 天",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("继续坚持")
            }
        }
    }
}

/**
 * 每日箴言池。相同日期返回相同内容，保证同一天显示一致。
 * 后续用户可以指定替换内容。
 */
private val dailyQuotes = listOf(
    "不积跬步，无以至千里；不积小流，无以成江海。",
    "锲而舍之，朽木不折；锲而不舍，金石可镂。",
    "千里之行，始于足下。",
    "天行健，君子以自强不息。",
    "合抱之木，生于毫末；九层之台，起于累土。",
    "每日一善，功不唐捐。",
    "日拱一卒，功不唐捐。",
    "有志者事竟成。",
    "业精于勤，荒于嬉。",
    "博观而约取，厚积而薄发。",
    "士不可以不弘毅，任重而道远。",
    "知之者不如好之者，好之者不如乐之者。",
    "纸上得来终觉浅，绝知此事要躬行。",
    "千淘万漉虽辛苦，吹尽狂沙始到金。",
    "宝剑锋从磨砺出，梅花香自苦寒来。",
    "随风潜入夜，润物细无声。",
    "读书破万卷，下笔如有神。",
    "学而不思则罔，思而不学则殆。",
    "温故而知新，可以为师矣。",
    "见贤思齐焉，见不贤而内自省也。",
)

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

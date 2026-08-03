package win.iqwqi.xiangece.ui.screens

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import win.iqwqi.xiangece.R
import win.iqwqi.xiangece.ui.theme.XiangeceTheme

private val Context.pomodoroDataStore by preferencesDataStore("pomodoro_settings")
private val KEY_WORK = intPreferencesKey("work_minutes")
private val KEY_BREAK = intPreferencesKey("break_minutes")
private val KEY_ROUNDS = intPreferencesKey("rounds")
private val KEY_DND = booleanPreferencesKey("do_not_disturb")
private val KEY_KEEP_SCREEN = booleanPreferencesKey("keep_screen_on")
private val KEY_SOUND = booleanPreferencesKey("sound_on_complete")

data class PomodoroConfig(
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val rounds: Int = 4,
    val doNotDisturb: Boolean = false,
    val keepScreenOn: Boolean = true,
    val soundOnComplete: Boolean = true,
)

suspend fun loadPomodoroConfig(context: Context): PomodoroConfig {
    val prefs = context.pomodoroDataStore.data.first()
    return PomodoroConfig(
        workMinutes = prefs[KEY_WORK] ?: 25,
        breakMinutes = prefs[KEY_BREAK] ?: 5,
        rounds = prefs[KEY_ROUNDS] ?: 4,
        doNotDisturb = prefs[KEY_DND] ?: false,
        keepScreenOn = prefs[KEY_KEEP_SCREEN] ?: true,
        soundOnComplete = prefs[KEY_SOUND] ?: true,
    )
}

private suspend fun savePomodoroConfig(context: Context, config: PomodoroConfig) {
    context.pomodoroDataStore.edit { prefs ->
        prefs[KEY_WORK] = config.workMinutes
        prefs[KEY_BREAK] = config.breakMinutes
        prefs[KEY_ROUNDS] = config.rounds
        prefs[KEY_DND] = config.doNotDisturb
        prefs[KEY_KEEP_SCREEN] = config.keepScreenOn
        prefs[KEY_SOUND] = config.soundOnComplete
    }
}

/**
 * 番茄钟设置底部弹窗。用户配置好时长/轮数/免打扰后点「开始专注」进入全屏沉浸页。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PomodoroSetupSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(PomodoroConfig()) }

    LaunchedEffect(Unit) {
        config = loadPomodoroConfig(context)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("专注番茄", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "设定时长与专注环境，开始沉浸式专注。完成后会有提醒。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            DurationPickerRow(
                label = "专注时长（分钟）",
                value = config.workMinutes,
                range = 5..90,
                step = 5,
                onValueChange = { config = config.copy(workMinutes = it) },
            )
            DurationPickerRow(
                label = "休息时长（分钟）",
                value = config.breakMinutes,
                range = 1..30,
                step = 1,
                onValueChange = { config = config.copy(breakMinutes = it) },
            )
            DurationPickerRow(
                label = "循环轮数",
                value = config.rounds,
                range = 1..8,
                step = 1,
                onValueChange = { config = config.copy(rounds = it) },
            )

            SettingToggleRow(
                label = "专注时屏蔽通知",
                description = "开启免打扰，专注期间不弹通知",
                checked = config.doNotDisturb,
                onCheckedChange = { config = config.copy(doNotDisturb = it) },
            )
            SettingToggleRow(
                label = "屏幕常亮",
                description = "专注期间保持屏幕不熄灭",
                checked = config.keepScreenOn,
                onCheckedChange = { config = config.copy(keepScreenOn = it) },
            )
            SettingToggleRow(
                label = "完成提醒",
                description = "每轮结束振动 + 提示音",
                checked = config.soundOnComplete,
                onCheckedChange = { config = config.copy(soundOnComplete = it) },
            )

            Button(
                onClick = {
                    scope.launch {
                        savePomodoroConfig(context, config)
                        val intent = Intent(context, PomodoroActivity::class.java).apply {
                            putExtra("work_minutes", config.workMinutes)
                            putExtra("break_minutes", config.breakMinutes)
                            putExtra("rounds", config.rounds)
                            putExtra("dnd", config.doNotDisturb)
                            putExtra("keep_screen", config.keepScreenOn)
                            putExtra("sound", config.soundOnComplete)
                        }
                        context.startActivity(intent)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Timer, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("开始专注")
            }
        }
    }
}

@Composable
private fun DurationPickerRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                "范围 ${range.first}–${range.last}，步进 $step",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = {
                val next = (value - step).coerceAtLeast(range.first)
                onValueChange(next)
            }) { Text("−") }
            Text(
                "$value",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            OutlinedButton(onClick = {
                val next = (value + step).coerceAtMost(range.last)
                onValueChange(next)
            }) { Text("+") }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 全屏专注页。大圆环倒计时 + 暂停/继续/放弃，完成自动切换专注↔休息，
 * 全部轮数完成后发通知提醒并退出。
 */
@AndroidEntryPoint
class PomodoroActivity : ComponentActivity() {

    enum class Phase { WORK, BREAK, DONE }

    @Inject lateinit var settingsStore: win.iqwqi.xiangece.data.settings.AppSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸全屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )

        val workMinutes = intent.getIntExtra("work_minutes", 25).coerceIn(1, 180)
        val breakMinutes = intent.getIntExtra("break_minutes", 5).coerceIn(1, 60)
        val rounds = intent.getIntExtra("rounds", 4).coerceIn(1, 12)
        val keepScreenOn = intent.getBooleanExtra("keep_screen", true)

        setContent {
            val settings by settingsStore.settings.collectAsState(
                initial = win.iqwqi.xiangece.data.settings.AppSettings(),
            )
            XiangeceTheme(
                darkTheme = settings.darkMode,
                themeSeedId = settings.themeSeed,
            ) {
                PomodoroFullScreen(
                    workSeconds = workMinutes * 60L,
                    breakSeconds = breakMinutes * 60L,
                    totalRounds = rounds,
                    keepScreenOn = keepScreenOn,
                    onComplete = { finish() },
                    onExit = { finish() },
                )
            }
        }
    }
}

@Composable
private fun PomodoroFullScreen(
    workSeconds: Long,
    breakSeconds: Long,
    totalRounds: Int,
    keepScreenOn: Boolean,
    onComplete: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(PomodoroActivity.Phase.WORK) }
    var round by remember { mutableIntStateOf(1) }
    var remaining by remember { mutableLongStateOf(workSeconds) }
    var paused by remember { mutableStateOf(false) }

    val totalForPhase = when (phase) {
        PomodoroActivity.Phase.WORK -> workSeconds
        PomodoroActivity.Phase.BREAK -> breakSeconds
        PomodoroActivity.Phase.DONE -> 1L
    }

    // 计时驱动
    LaunchedEffect(phase, paused) {
        if (phase == PomodoroActivity.Phase.DONE) return@LaunchedEffect
        while (remaining > 0 && !paused) {
            delay(1000L)
            remaining -= 1
        }
        if (remaining <= 0 && !paused) {
            // 当前阶段结束
            notifyPhaseEnd(context, phase == PomodoroActivity.Phase.WORK)
            when (phase) {
                PomodoroActivity.Phase.WORK -> {
                    if (round >= totalRounds) {
                        // 全部完成
                        phase = PomodoroActivity.Phase.DONE
                        notifyCompletion(context, totalRounds)
                        delay(1500L)
                        onComplete()
                    } else {
                        phase = PomodoroActivity.Phase.BREAK
                        remaining = breakSeconds
                    }
                }
                PomodoroActivity.Phase.BREAK -> {
                    round += 1
                    phase = PomodoroActivity.Phase.WORK
                    remaining = workSeconds
                }
                PomodoroActivity.Phase.DONE -> Unit
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        // 顶部退出按钮
        IconButton(
            onClick = onExit,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "退出专注",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = when (phase) {
                    PomodoroActivity.Phase.WORK -> "专注中"
                    PomodoroActivity.Phase.BREAK -> "休息一下"
                    PomodoroActivity.Phase.DONE -> "已完成"
                },
                style = MaterialTheme.typography.titleMedium,
                color = when (phase) {
                    PomodoroActivity.Phase.WORK -> MaterialTheme.colorScheme.primary
                    PomodoroActivity.Phase.BREAK -> MaterialTheme.colorScheme.tertiary
                    PomodoroActivity.Phase.DONE -> MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "第 $round / $totalRounds 轮",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            // 大圆环倒计时
            CountingRing(
                remainingSeconds = remaining,
                totalSeconds = totalForPhase,
                isBreak = phase == PomodoroActivity.Phase.BREAK,
                modifier = Modifier.size(280.dp),
            )

            Spacer(Modifier.height(40.dp))

            // 控制按钮
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = {
                        // 重新开始当前轮
                        remaining = when (phase) {
                            PomodoroActivity.Phase.WORK -> workSeconds
                            PomodoroActivity.Phase.BREAK -> breakSeconds
                            else -> workSeconds
                        }
                        paused = false
                    },
                    enabled = phase != PomodoroActivity.Phase.DONE,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("重置")
                }
                Button(
                    onClick = { paused = !paused },
                    enabled = phase != PomodoroActivity.Phase.DONE,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (phase == PomodoroActivity.Phase.BREAK) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
                ) {
                    Icon(
                        if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(if (paused) "继续" else "暂停")
                }
            }

            if (phase == PomodoroActivity.Phase.DONE) {
                Spacer(Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.SelfImprovement,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "完成 $totalRounds 轮专注，辛苦了",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountingRing(
    remainingSeconds: Long,
    totalSeconds: Long,
    isBreak: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = if (totalSeconds > 0) {
        remainingSeconds.toFloat() / totalSeconds.toFloat()
    } else 0f
    val ringColor = if (isBreak) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val arcSize = Size(size.minDimension - strokeWidth, size.minDimension - strokeWidth)
            val topLeft = Offset(
                (size.width - arcSize.width) / 2,
                (size.height - arcSize.height) / 2,
            )
            // 背景轨道
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            // 进度
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%02d:%02d".format(minutes, seconds),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (isBreak) "放松片刻" else "保持专注",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单轮结束：振动 + 提示音。 */
private fun notifyPhaseEnd(context: Context, wasWork: Boolean) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            it.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            it.vibrate(400)
        }
    }
    try {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, uri)?.play()
    } catch (_: Exception) {
    }
}

/** 全部完成：发一条通知。 */
private fun notifyCompletion(context: Context, rounds: Int) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "pomodoro_complete",
            "专注完成",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "番茄钟专注完成提醒" }
        nm.createNotificationChannel(channel)
    }
    val notification = NotificationCompat.Builder(context, "pomodoro_complete")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("专注完成")
        .setContentText("你已完成 $rounds 轮番茄专注，辛苦了！")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    nm.notify(9001, notification)
}

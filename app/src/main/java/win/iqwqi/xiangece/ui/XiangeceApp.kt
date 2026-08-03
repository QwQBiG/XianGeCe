package win.iqwqi.xiangece.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import win.iqwqi.xiangece.ui.components.AppSnackbar
import win.iqwqi.xiangece.ui.components.MessageType
import win.iqwqi.xiangece.ui.screens.CoursesScreen
import win.iqwqi.xiangece.ui.screens.DraftEditorDialog
import win.iqwqi.xiangece.ui.screens.HabitsScreen
import win.iqwqi.xiangece.ui.screens.OnboardingScreen
import win.iqwqi.xiangece.ui.screens.PermissionState
import win.iqwqi.xiangece.ui.screens.SettingsScreen
import win.iqwqi.xiangece.ui.screens.TodayScreen
import win.iqwqi.xiangece.ui.screens.TimetableEditorDialog
import win.iqwqi.xiangece.ui.screens.ToolboxScreen

private data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    Destination("today", "今日", Icons.Outlined.Home),
    Destination("habits", "厚积", Icons.Outlined.AutoGraph),
    Destination("courses", "课程", Icons.Outlined.CalendarMonth),
    Destination("toolbox", "百宝", Icons.Outlined.AutoAwesomeMosaic),
    Destination("mine", "我的", Icons.Outlined.Person),
)

@Composable
fun XiangeceRoot(state: AppUiState, viewModel: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { it?.let(viewModel::importImage) }
    val timetablePdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { it?.let { uri -> viewModel.importTimetableFile(uri, "pdf") } }
    val timetableHtmlPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { it?.let { uri -> viewModel.importTimetableFile(uri, "html") } }
    val timetableExcelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { it?.let { uri -> viewModel.importTimetableFile(uri, "excel") } }
    val timetableWallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { it?.let(viewModel::saveTimetableWallpaper) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { it?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { it?.let(viewModel::restoreBackup) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    var showStartupSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // 留出 splash 淡入动画时间即可，避免过长的固定延迟拖慢冷启动
        delay(760)
        showStartupSplash = false
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            // 统一用 Snackbar 显示（不再同时弹 Toast，避免重复打扰）
            // 若有待撤销动作（如删除），附带「撤销」按钮
            val undo = viewModel.pendingUndo.value
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = undo?.actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && undo != null) {
                undo.onUndo()
            } else {
                // 超时或手动关闭，清空待撤销动作
                viewModel.pendingUndo.value = null
            }
        }
    }
    LaunchedEffect(
        state.settings.onboardingComplete,
        state.settings.notificationPermissionAsked,
    ) {
        if (
            state.settings.onboardingComplete &&
            !state.settings.notificationPermissionAsked &&
            Build.VERSION.SDK_INT >= 33
        ) {
            viewModel.markNotificationPermissionAsked()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (!state.settings.onboardingComplete) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = {
                    SnackbarHost(snackbar) { data ->
                        AppSnackbar(
                            message = data.visuals.message,
                            type = MessageType.INFO,
                            onDismiss = { data.dismiss() },
                            actionLabel = data.visuals.actionLabel,
                            onAction = { data.performAction() },
                        )
                    }
                },
            ) { padding ->
                OnboardingScreen(
                    contentPadding = padding,
                    onComplete = viewModel::completeOnboarding,
                )
            }
            XiangeceSplash(showStartupSplash)
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { destinations.size })
    var courseNavExpanded by remember { mutableStateOf(true) }
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }
    val scope = rememberCoroutineScope()
    LaunchedEffect(currentPage) {
        courseNavExpanded = currentPage != 2 // courses is index 2
    }
    val requestAdjacent: (Int) -> Unit = { delta ->
        val target = (currentPage + delta).coerceIn(0, destinations.lastIndex)
        if (target != currentPage) {
            scope.launch { pagerState.animateScrollToPage(target) }
        }
    }

    val appContext = LocalContext.current.applicationContext
    val permissionState = remember { mutableStateOf(PermissionState()) }
    LaunchedEffect(
        state.settings.onboardingComplete,
        state.settings.notificationPermissionAsked,
        currentPage,
    ) {
        while (true) {
            val granted = when {
                Build.VERSION.SDK_INT >= 33 ->
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                else -> {
                    val nm = appContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                        as android.app.NotificationManager
                    nm.areNotificationsEnabled()
                }
            }
            val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = appContext.getSystemService(android.content.Context.ALARM_SERVICE)
                    as android.app.AlarmManager
                am.canScheduleExactAlarms()
            } else {
                true
            }
            permissionState.value = PermissionState(
                notificationsGranted = granted,
                exactAlarmGranted = exact,
            )
            delay(1500)
        }
    }
    val requestNotificationRuntime: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            viewModel.markNotificationPermissionAsked()
        }
    }
    val openNotificationSettings: () -> Unit = {
        val ctx = appContext
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        } else {
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", ctx.packageName, null))
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
    val openExactAlarmSettings: () -> Unit = {
        val ctx = appContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(android.net.Uri.fromParts("package", ctx.packageName, null))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(intent) }
                .onFailure {
                    val fallback = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.fromParts("package", ctx.packageName, null))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(fallback)
                }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    AppSnackbar(
                        message = data.visuals.message,
                        type = MessageType.INFO,
                        onDismiss = { data.dismiss() },
                        actionLabel = data.visuals.actionLabel,
                        onAction = { data.performAction() },
                    )
                }
            },
            bottomBar = {
                if (currentPage == 2 && !courseNavExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.38f))
                            .clickable { courseNavExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.18f)
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.26f)),
                        )
                    }
                } else {
                    NavigationBar(
                        modifier = Modifier.height(64.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        destinations.forEachIndexed { index, destination ->
                            NavigationBarItem(
                                selected = currentPage == index,
                                onClick = {
                                    if (currentPage == 2 && index == 2) {
                                        courseNavExpanded = false
                                    } else {
                                        scope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                },
                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                                label = { Text(destination.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            @OptIn(ExperimentalFoundationApi::class)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // 提前渲染相邻页，避免左右切换时下一页（尤其课程页解码壁纸）出现加载顿挫
                beyondViewportPageCount = 1,
            ) { page ->
                when (page) {
                    0 -> TodayScreen(
                        state = state,
                        contentPadding = padding,
                        onToggleTask = viewModel::toggleTask,
                        onUpdateTask = viewModel::updateTask,
                        onUpdateEvent = viewModel::updateEvent,
                        onCreateTask = viewModel::createTask,
                        onCreateEvent = viewModel::createEvent,
                        onDeleteTask = viewModel::deleteTask,
                        onDeleteEvent = viewModel::deleteEvent,
                    )
                    1 -> HabitsScreen(
                        state = state,
                        contentPadding = padding,
                        onCreateHabit = viewModel::createHabit,
                        onUpdateHabit = viewModel::updateHabit,
                        onDeleteHabit = viewModel::deleteHabit,
                        onToggleCheckin = viewModel::toggleHabitCheckin,
                        onDismissCelebration = viewModel::dismissCelebration,
                    )
                    2 -> CoursesScreen(
                        state = state,
                        contentPadding = padding,
                        onSaveCourse = viewModel::saveCourse,
                        onDeleteMeeting = viewModel::deleteCourseMeeting,
                        onPickTimetablePdf = {
                            timetablePdfPicker.launch(arrayOf("application/pdf"))
                        },
                        onPickTimetableHtml = {
                            timetableHtmlPicker.launch(arrayOf("text/html", "application/xhtml+xml", "text/plain"))
                        },
                        onPickTimetableExcel = {
                            timetableExcelPicker.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "text/csv",
                                    "text/tab-separated-values",
                                    "application/octet-stream",
                                ),
                            )
                        },
                        onImportTimetableCode = viewModel::importTimetableCode,
                        onSaveSemester = viewModel::saveSemester,
                        onCreateTimetable = viewModel::createTimetable,
                        onSwitchTimetable = viewModel::switchTimetable,
                        onRenameTimetable = viewModel::renameTimetable,
                        onSaveTimetableLayout = viewModel::saveTimetableLayout,
                        onSavePeriod = viewModel::savePeriod,
                        onPickWallpaper = {
                            timetableWallpaperPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onClearWallpaper = viewModel::clearTimetableWallpaper,
                        onSaveBackgroundOptions = viewModel::saveTimetableBackgroundOptions,
                        onNavigateAdjacent = requestAdjacent,
                    )
                    3 -> ToolboxScreen(
                        state = state,
                        contentPadding = padding,
                        onParseText = viewModel::parseToolText,
                        onPickImage = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onSavePeriod = viewModel::savePeriod,
                        onAddGrade = viewModel::addGrade,
                        onDeleteGrade = viewModel::deleteGrade,
                        onSaveGradePreferences = viewModel::saveGradePreferences,
                        onOpenAiSettings = {
                            scope.launch { pagerState.animateScrollToPage(4) }
                        },
                    )
                    4 -> SettingsScreen(
                        state = state,
                        contentPadding = padding,
                        onSaveSemester = viewModel::saveSemester,
                        onSaveGeneral = viewModel::saveGeneralSettings,
                        onRequestNotifications = requestNotificationRuntime,
                        onSetReminderEnabled = viewModel::setReminderEnabled,
                        onDeleteReminder = viewModel::deleteReminder,
                        onSavePeriod = viewModel::savePeriod,
                        onSaveTimetableLayout = viewModel::saveTimetableLayout,
                        onSaveAi = viewModel::saveAiSettings,
                        onTestAi = viewModel::testAiConnection,
                        onExport = { exportLauncher.launch("弦歌册-${java.time.LocalDate.now()}.xiangece") },
                        onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        onPickImage = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onOpenInbox = viewModel::openInbox,
                        onRetryInbox = viewModel::retryInboxOcr,
                        onDeleteInbox = viewModel::deleteInbox,
                        onAddQuote = viewModel::addCustomQuote,
                        onUpdateQuote = viewModel::updateCustomQuote,
                        onDeleteQuote = viewModel::deleteCustomQuote,
                        onSendTestNotification = viewModel::sendTestNotification,
                        onSetThemeSeed = viewModel::setThemeSeed,
                        onSetDarkMode = viewModel::setDarkMode,
                        onRegister = viewModel::registerAccount,
                        onLogin = viewModel::loginAccount,
                        onLogout = viewModel::logoutAccount,
                        permissionState = permissionState.value,
                        onRequestNotificationRuntime = requestNotificationRuntime,
                        onOpenNotificationSettings = openNotificationSettings,
                        onOpenExactAlarmSettings = openExactAlarmSettings,
                    )
                }
            }
        }
        XiangeceSplash(showStartupSplash)
    }

    state.editor?.let {
        DraftEditorDialog(
            editor = it,
            aiEnabled = state.settings.aiEnabled,
            isWorking = state.isWorking,
            semesterWeekCount = state.semester?.weekCount ?: 20,
            onDismiss = viewModel::closeEditor,
            onSourceTextChange = viewModel::updateEditorSourceText,
            onReparse = viewModel::reparseEditorSource,
            onTitleChange = viewModel::updateEditorTitle,
            onDateTimeChange = viewModel::updateEditorDateTime,
            onCourseNameChange = viewModel::updateEditorCourseName,
            onLocationChange = viewModel::updateEditorLocation,
            onTeachingWeekChange = viewModel::updateEditorTeachingWeek,
            onDayOfWeekChange = viewModel::updateEditorDayOfWeek,
            onStartPeriodChange = viewModel::updateEditorStartPeriod,
            onEndPeriodChange = viewModel::updateEditorEndPeriod,
            onAmbiguitiesAcknowledged = viewModel::acknowledgeEditorAmbiguities,
            onTypeChange = viewModel::updateEditorType,
            onEnhance = viewModel::enhanceEditorWithAi,
            onConfirm = viewModel::confirmEditor,
        )
    }
    state.timetableEditor?.let {
        TimetableEditorDialog(
            editor = it,
            isWorking = state.isWorking,
            onDismiss = viewModel::closeTimetableEditor,
            onUpdateRow = viewModel::updateTimetableRow,
            onAddRow = viewModel::addTimetableRow,
            onRemoveRow = viewModel::removeTimetableRow,
            onConfirm = viewModel::confirmTimetable,
        )
    }
    if (state.isWorking) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun XiangeceSplash(visible: Boolean) {
    var splashEntered by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            splashEntered = false
            delay(40)
            splashEntered = true
        }
    }
    val iconScale by animateFloatAsState(
        targetValue = if (splashEntered) 1f else 0.72f,
        animationSpec = tween(560),
        label = "xiangeceSplashScale",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (splashEntered) 1f else 0f,
        animationSpec = tween(420),
        label = "xiangeceSplashAlpha",
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (splashEntered) 0f else -5f,
        animationSpec = tween(560),
        label = "xiangeceSplashRotation",
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(win.iqwqi.xiangece.R.drawable.ic_launcher_splash),
                    contentDescription = "弦歌册",
                    modifier = Modifier
                        .size(128.dp)
                        .scale(iconScale)
                        .rotate(iconRotation)
                        .clip(CircleShape)
                        .alpha(iconAlpha),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "弦歌册",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 2.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "大学诸事，尽入一册",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

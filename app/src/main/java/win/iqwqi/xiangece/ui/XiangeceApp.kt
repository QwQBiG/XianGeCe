package win.iqwqi.xiangece.ui

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import win.iqwqi.xiangece.ui.theme.LocalCompanionAppearance
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import win.iqwqi.xiangece.ui.components.AppSnackbar
import win.iqwqi.xiangece.ui.companion.CompanionFeedbackHost
import win.iqwqi.xiangece.ui.companion.CompanionWorking
import win.iqwqi.xiangece.ui.companion.WardrobeSheet
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import win.iqwqi.xiangece.ui.screens.CalendarScreen
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer

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
fun XiangeceRoot(state: AppUiState, viewModel: MainViewModel, initialPage: Int = 0) {
    val snackbar = remember { SnackbarHostState() }
    var showWardrobe by remember { mutableStateOf(false) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var settingsEntry by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(showCalendar) { showCalendar = false }
    val companionPaused by viewModel.companionPaused.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var imageUseCloud by rememberSaveable { mutableStateOf(true) }
    var imageIsTimetable by rememberSaveable { mutableStateOf(false) }
    var pendingCloudImage by remember { mutableStateOf<android.net.Uri?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            if (imageUseCloud && state.settings.aiEnabled && state.settings.aiBaseUrl.isNotBlank() &&
                (!imageIsTimetable || (state.settings.aiSupportsVision && state.settings.aiVisionModel.isNotBlank()))) pendingCloudImage = uri
            else if (imageIsTimetable) viewModel.importTimetableImage(uri)
            else viewModel.importImage(uri)
        }
    }
    pendingCloudImage?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingCloudImage = null },
            title = { Text(if (imageIsTimetable) "使用云端识别这张课表？" else "使用云端整理这张图片？") },
            text = { Text(
                "服务：${state.settings.aiProvider}\n地址：${state.settings.aiBaseUrl}\n" +
                    if (state.settings.aiSupportsVision && state.settings.aiVisionModel.isNotBlank())
                        "确认后发送这张图片给上述服务；结果仍需你核对后写入。"
                    else "确认后先在本机识字，再发送识别文字整理事项；原图留在本机。",
            ) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                pendingCloudImage = null
                if (imageIsTimetable) viewModel.importTimetableImage(uri, state.settings.aiBaseUrl)
                else viewModel.importImage(uri, state.settings.aiBaseUrl)
            }) { Text("同意并整理") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = {
                pendingCloudImage = null
                if (imageIsTimetable) viewModel.importTimetableImage(uri)
                else viewModel.importImage(uri)
            }) { Text("仅本地") } },
        )
    }
    val timetablePdfTablePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { it?.let { uri -> viewModel.importTimetableFile(uri, "pdf_table") } }
    val timetablePdfListPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { it?.let { uri -> viewModel.importTimetableFile(uri, "pdf_list") } }
    val timetableHtmlPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { it?.let { uri -> viewModel.importTimetableFile(uri, "html") } }
    val timetableExcelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { it?.let { uri -> viewModel.importTimetableFile(uri, "excel") } }
    val ditingOfflinePackPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { it?.let(viewModel::importDitingOfflinePack) }
    val offlineOcrPackPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { it?.let(viewModel::importOfflineOcrPack) }
    val timetableWallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { it?.let(viewModel::saveTimetableWallpaper) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { it?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { it?.let(viewModel::restoreBackup) }
    var sendTestAfterPermission by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (sendTestAfterPermission) {
            sendTestAfterPermission = false
            if (granted) viewModel.sendTestNotification()
            else viewModel.messages.tryEmit("通知权限未开启，测试通知无法发送")
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            val undo = viewModel.pendingUndo.value
            // 普通提示使用系统 Toast，保证在 ModalBottomSheet、系统弹层之上显示；
            // 只有需要“撤销”操作时才保留可交互的 Snackbar。
            if (undo == null) {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
                return@collectLatest
            }
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
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, destinations.lastIndex), pageCount = { destinations.size })
    var courseNavExpanded by remember { mutableStateOf(true) }
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }
    val scope = rememberCoroutineScope()
    var handledEntryPage by rememberSaveable { mutableStateOf(initialPage) }
    LaunchedEffect(initialPage) {
        if (handledEntryPage == initialPage) return@LaunchedEffect
        handledEntryPage = initialPage
        val target = initialPage.coerceIn(0, destinations.lastIndex)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    LaunchedEffect(currentPage) {
        courseNavExpanded = currentPage != 2 // courses is index 2
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
    val requestTestNotificationRuntime: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) {
            sendTestAfterPermission = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            viewModel.markNotificationPermissionAsked()
        } else {
            viewModel.sendTestNotification()
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
                if (showCalendar) {
                    Spacer(Modifier.height(0.dp))
                } else if (currentPage == 2 && !courseNavExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { courseNavExpanded = true },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.38f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.18f)
                                    .height(3.dp)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.26f)),
                            )
                        }
                        Spacer(Modifier.navigationBarsPadding())
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            // Keep the complete Material navigation bar above legacy
                            // three-button navigation. Applying the inset outside the
                            // bar avoids compressing and clipping its icon/label area.
                            .windowInsetsPadding(
                                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
                            ),
                    ) {
                        NavigationBar(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        ) {
                            destinations.forEachIndexed { index, destination ->
                                NavigationBarItem(
                                    selected = currentPage == index,
                                    onClick = {
                                        if (currentPage == 2 && index == 2) {
                                            courseNavExpanded = false
                                        } else {
                                            scope.launch {
                                                pagerState.scrollToPage(index)
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
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
            @OptIn(ExperimentalFoundationApi::class)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().then(if (showCalendar)
                    Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {} else Modifier),
                // 课程页会在课表实际超出屏幕宽度时自行接管横滑；其余位置仍可切换主页面。
                beyondViewportPageCount = 1,
                userScrollEnabled = !showCalendar,
            ) { page ->
                when (page) {
                    0 -> TodayScreen(
                        state = state,
                        contentPadding = padding,
                        onToggleTask = viewModel::toggleTask,
                        onOpenWardrobe = { showWardrobe = true },
                        onGreetCompanion = viewModel::greetCompanion,
                        companionPaused = companionPaused,
                        companionActive = currentPage == 0 && !showCalendar && !showWardrobe,
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
                        onPickTimetableImage = {
                            imageIsTimetable = true
                            imageUseCloud = true
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onPickTimetablePdfTable = {
                            timetablePdfTablePicker.launch(arrayOf("application/pdf"))
                        },
                        onPickTimetablePdfList = {
                            timetablePdfListPicker.launch(arrayOf("application/pdf"))
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
                        onDeleteTimetable = viewModel::deleteTimetable,
                        onSaveTimetableLayout = viewModel::saveTimetableLayout,
                        onSavePeriod = viewModel::savePeriod,
                        onPickWallpaper = {
                            timetableWallpaperPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onClearWallpaper = viewModel::clearTimetableWallpaper,
                        onSaveBackgroundOptions = viewModel::saveTimetableBackgroundOptions,
                    )
                    3 -> ToolboxScreen(
                        onOpenCalendar = { showCalendar = true },
                        state = state,
                        contentPadding = padding,
                        onParseText = viewModel::parseToolText,
                        onPickImage = { useCloud ->
                            imageIsTimetable = false
                            imageUseCloud = useCloud
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onSavePeriod = viewModel::savePeriod,
                        onAddGrade = viewModel::addGrade,
                        onDeleteGrade = viewModel::deleteGrade,
                        onSaveGradePreferences = viewModel::saveGradePreferences,
                        onOpenAiSettings = {
                            settingsEntry = "service"
                            scope.launch { pagerState.animateScrollToPage(4) }
                        },
                         onOpenResources = { settingsEntry = "resources"; scope.launch { pagerState.animateScrollToPage(4) } },
                    )
                    4 -> SettingsScreen(
                        requestedPanel = settingsEntry,
                        onPanelConsumed = { settingsEntry = null },
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
                        onSelectOcrPack = viewModel::selectOcrPack,
                        onSelectDitingPack = viewModel::selectDitingPack,
                        onSaveDitingTranscription = viewModel::saveDitingTranscription,
                        onSaveDitingAiAnnotation = viewModel::saveDitingAiAnnotation,
                        onExport = { exportLauncher.launch("弦歌册-${java.time.LocalDate.now()}.xiangece") },
                        onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        onPickImage = {
                            imageUseCloud = true
                            imageIsTimetable = false
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
                        onOpenWardrobe = { showWardrobe = true },
                        onSetDarkMode = viewModel::setDarkMode,
                        onSetFollowSystemTheme = viewModel::setFollowSystemTheme,
                        onRegister = viewModel::registerAccount,
                        onLogin = viewModel::loginAccount,
                        onLogout = viewModel::logoutAccount,
                        permissionState = permissionState.value,
                        onRequestNotificationRuntime = requestNotificationRuntime,
                        onRequestTestNotificationRuntime = requestTestNotificationRuntime,
                        onOpenNotificationSettings = openNotificationSettings,
                        onOpenExactAlarmSettings = openExactAlarmSettings,
                         onDownloadDitingOfflinePack = viewModel::startDitingOfflinePackDownload,
                         onCancelDitingOfflinePackDownload = viewModel::cancelDitingOfflinePackDownload,
                         onDeleteDitingOfflinePack = viewModel::deleteDitingOfflinePack,
                         onImportDitingOfflinePack = {
                             ditingOfflinePackPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                         },
                         onDownloadOfflineOcrPack = viewModel::startOfflineOcrPackDownload,
                         onCancelOfflineOcrPackDownload = viewModel::cancelOfflineOcrPackDownload,
                         onDeleteOfflineOcrPack = viewModel::deleteOfflineOcrPack,
                         onImportOfflineOcrPack = {
                             offlineOcrPackPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                         },
                    )
                }
            }
            val sceneDuration = if (LocalCompanionAppearance.current.motionEnabled) 200 else 0
            AnimatedVisibility(
                visible = showCalendar,
                enter = fadeIn(tween(sceneDuration)) + slideInHorizontally(tween(sceneDuration)) { it / 14 },
                exit = fadeOut(tween(sceneDuration)) + slideOutHorizontally(tween(sceneDuration)) { it / 14 },
            ) {
                val calendarNotes by viewModel.calendarNotes.collectAsStateWithLifecycle()
                CalendarScreen(state, calendarNotes, onBack = { showCalendar = false },
                    onSaveNote = viewModel::saveCalendarNote,
                    onCreateTask = viewModel::createTask, onUpdateTask = viewModel::updateTask,
                    onToggleTask = viewModel::toggleTask, onDeleteTask = viewModel::deleteTask,
                    onUpdateEvent = viewModel::updateEvent)
            }
            }
        }
        CompanionFeedbackHost(
            events = viewModel.companionEvents,
            page = currentPage,
            paused = companionPaused,
            obscured = showCalendar || showWardrobe || state.isWorking || state.editor != null || state.timetableEditor != null,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 96.dp),
        )
    }

    if (showWardrobe) {
        WardrobeSheet(state.settings.companion, state.settings.themeSeed, viewModel::saveCompanion,
            onDismiss = { showWardrobe = false }, motionAllowed = !companionPaused)
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!companionPaused && currentPage != 2) CompanionWorking(state.isWorking)
                CircularProgressIndicator()
            }
        }
    }
}

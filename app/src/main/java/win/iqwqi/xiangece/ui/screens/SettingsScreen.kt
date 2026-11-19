package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import win.iqwqi.xiangece.BuildConfig
import win.iqwqi.xiangece.core.reminder.ReminderChannels
import win.iqwqi.xiangece.core.reminder.ReminderTargets
import win.iqwqi.xiangece.data.local.CustomQuoteEntity
import win.iqwqi.xiangece.data.local.InboxItemEntity
import win.iqwqi.xiangece.data.local.ReminderEntity
import win.iqwqi.xiangece.domain.model.InboxStatus
import win.iqwqi.xiangece.core.ocr.OfflineOcrPackState
import win.iqwqi.xiangece.core.ocr.OfflineOcrPackCatalog
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackState
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackCatalog
import win.iqwqi.xiangece.ui.AppUiState
import win.iqwqi.xiangece.ui.components.AppConfirmDialog
import win.iqwqi.xiangece.ui.components.AppFormSheet
import win.iqwqi.xiangece.ui.components.AppFullScreenSheet
import win.iqwqi.xiangece.ui.components.AppTextField
import win.iqwqi.xiangece.ui.components.BrandHeader
import win.iqwqi.xiangece.ui.components.InkDivider
import win.iqwqi.xiangece.ui.components.PaperCard
import win.iqwqi.xiangece.ui.components.AppActionRow
import win.iqwqi.xiangece.ui.components.AppActionGroup
import win.iqwqi.xiangece.ui.components.SectionTitle
import win.iqwqi.xiangece.ui.components.LocalPolicyButton

private enum class MinePanel {
    LOGIN, AI, INFO, BACKUP, INBOX, QUOTES, REMINDERS, APPEARANCE, PRIVACY, RESOURCES, SHARE
}

private const val XIANGECE_PUBLIC_URL = "https://iqwqi.win/cs/posts/xiangece/"

/** App-level settings only. Timetable and study preferences live in Courses. */
data class PermissionState(
    val notificationsGranted: Boolean = false,
    val exactAlarmGranted: Boolean = false,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    onSaveSemester: (String, String, String) -> Unit,
    onSaveGeneral: (Boolean, String, String, String) -> Unit,
    onRequestNotifications: () -> Unit,
    onSetReminderEnabled: (ReminderEntity, Boolean) -> Unit,
    onDeleteReminder: (ReminderEntity) -> Unit,
    onSavePeriod: (Int, String, String) -> Unit,
    onSaveTimetableLayout: (String, String, String) -> Unit,
    onSaveAi: (Boolean, String, String, String, String, String, Boolean, String) -> Unit,
    onTestAi: suspend (Boolean, String, String, String, String, String, Boolean, String) -> Result<Unit>,
    onSaveDitingTranscription: (String, String) -> Unit,
    onSaveDitingAiAnnotation: (Boolean) -> Unit = {},
    onExport: () -> Unit,
    onImport: () -> Unit,
    onPickImage: () -> Unit,
    onOpenInbox: (InboxItemEntity) -> Unit,
    onRetryInbox: (InboxItemEntity) -> Unit,
    onDeleteInbox: (InboxItemEntity) -> Unit,
    onAddQuote: (String, String) -> Unit,
    onUpdateQuote: (Long, String, String, Int) -> Unit,
    onDeleteQuote: (Long) -> Unit,
    onSendTestNotification: () -> Unit,
    onSetThemeSeed: (String) -> Unit = {},
    onOpenWardrobe: () -> Unit = {},
    onSetDarkMode: (Boolean) -> Unit = {},
    onSetFollowSystemTheme: (Boolean) -> Unit = {},
    onRegister: (String, String) -> Unit = { _, _ -> },
    onLogin: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    permissionState: PermissionState = PermissionState(),
    onRequestNotificationRuntime: () -> Unit = {},
    onRequestTestNotificationRuntime: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
    onDownloadDitingOfflinePack: () -> Unit = {},
    onCancelDitingOfflinePackDownload: () -> Unit = {},
    onDeleteDitingOfflinePack: () -> Unit = {},
    onImportDitingOfflinePack: () -> Unit = {},
    onDownloadOfflineOcrPack: () -> Unit = {},
    onCancelOfflineOcrPackDownload: () -> Unit = {},
    onDeleteOfflineOcrPack: () -> Unit = {},
    onImportOfflineOcrPack: () -> Unit = {},
    onSelectOcrPack: (String) -> Unit = {},
    onSelectDitingPack: (String) -> Unit = {},
    requestedPanel: String? = null,
    onPanelConsumed: () -> Unit = {},
) {
    var panel by remember { mutableStateOf<MinePanel?>(null) }
    androidx.compose.runtime.LaunchedEffect(requestedPanel) {
        if (requestedPanel != null) {
            panel = when (requestedPanel) { "service" -> MinePanel.AI; "resources" -> MinePanel.RESOURCES; else -> panel }
            onPanelConsumed()
        }
    }
    val latestInbox = state.inbox.firstOrNull()
    val isLoggedIn = state.settings.accountLoggedIn
    val accountEmail = state.settings.accountEmail

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { BrandHeader("我的", "管理偏好、提醒与个人数据", icon = Icons.Outlined.Person) }

        item {
            PaperCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            when {
                                isLoggedIn -> {
                                    Text(accountEmail, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("已登录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                accountEmail.isNotEmpty() -> {
                                    Text("未登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("账号：$accountEmail", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                else -> {
                                    Text("未登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("资料保存在这台设备上", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (isLoggedIn) {
                        OutlinedButton(onClick = onLogout) {
                            Text("退出")
                        }
                    } else {
                        Button(onClick = { panel = MinePanel.LOGIN }) {
                            Icon(Icons.Outlined.Login, contentDescription = null)
                            Text(if (accountEmail.isEmpty()) " 注册" else " 登录")
                        }
                    }
                }
            }
        }
        item { SectionTitle("日常与外观") }
        item { AppActionGroup {
            AppActionRow("外观与角色", "${state.settings.companion.outfit.label} · 主题与装扮", Icons.Outlined.Palette, { panel = MinePanel.APPEARANCE })
            AppActionRow("提醒", "通知权限与提醒时间", Icons.Outlined.Notifications, { panel = MinePanel.REMINDERS })
            AppActionRow("箴言", if (state.customQuotes.isEmpty()) "每日一句" else "${state.customQuotes.size} 条箴言", Icons.Outlined.Edit, { panel = MinePanel.QUOTES })
        } }
        item { SectionTitle("资料与服务", modifier = Modifier.padding(top = 8.dp)) }
        item { AppActionGroup {
            AppActionRow("收件箱", "${state.inbox.size} 条记录", Icons.Outlined.TextSnippet, { panel = MinePanel.INBOX })
            AppActionRow("智能服务", if (state.settings.aiEnabled) state.settings.aiProvider else "未启用", Icons.Outlined.SmartToy, { panel = MinePanel.AI })
            AppActionRow("离线资源", offlineResourceSummary(state.ditingOfflinePack.installedPackIds.size, state.offlineOcrPack.installedPackIds.size),
                Icons.Outlined.Save, { panel = MinePanel.RESOURCES })
            AppActionRow("本地备份", "导出或恢复个人数据", Icons.Outlined.CloudOff, { panel = MinePanel.BACKUP })
        } }
        item { SectionTitle("关于弦歌册", modifier = Modifier.padding(top = 8.dp)) }
        item { AppActionGroup {
            AppActionRow("隐私", "权限与数据说明", Icons.Outlined.Lock, { panel = MinePanel.PRIVACY })
            AppActionRow("关于", "版本、帮助与说明", Icons.Outlined.Info, { panel = MinePanel.INFO })
            AppActionRow("分享应用", "邀请同学一起使用", Icons.Outlined.Share, { panel = MinePanel.SHARE })
        } }
    }

    when (panel) {
        MinePanel.LOGIN -> LoginSheet(
            state = state,
            onRegister = onRegister,
            onLogin = onLogin,
            onDismiss = { panel = null },
        )
        MinePanel.AI -> AiSheet(
            state = state,
            onSaveAi = onSaveAi,
            onTestAi = onTestAi,
            onSaveDitingTranscription = onSaveDitingTranscription,
            onSaveDitingAiAnnotation = onSaveDitingAiAnnotation,
            onDismiss = { panel = null },
        )
        MinePanel.INBOX -> InboxSheet(
            state = state,
            onPickImage = onPickImage,
            onOpenInbox = onOpenInbox,
            onRetryInbox = onRetryInbox,
            onDeleteInbox = onDeleteInbox,
            onDismiss = { panel = null },
        )
        MinePanel.BACKUP -> BackupSheet(
            onExport = onExport,
            onImport = onImport,
            onDismiss = { panel = null },
        )
        MinePanel.QUOTES -> QuotesSheet(
            quotes = state.customQuotes,
            onAddQuote = onAddQuote,
            onUpdateQuote = onUpdateQuote,
            onDeleteQuote = onDeleteQuote,
            onDismiss = { panel = null },
        )
        MinePanel.REMINDERS -> RemindersSheet(
            state = state,
            permissionState = permissionState,
            onSendTestNotification = onSendTestNotification,
            onRequestNotificationRuntime = onRequestNotificationRuntime,
            onRequestTestNotificationRuntime = onRequestTestNotificationRuntime,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            onDismiss = { panel = null },
        )
        MinePanel.APPEARANCE -> AppearanceSheet(
            state = state,
            onSetThemeSeed = onSetThemeSeed,
            onOpenWardrobe = { panel = null; onOpenWardrobe() },
            onSetDarkMode = onSetDarkMode,
            onSetFollowSystemTheme = onSetFollowSystemTheme,
            onDismiss = { panel = null },
        )
        MinePanel.PRIVACY -> PrivacySheet(onDismiss = { panel = null })
        MinePanel.INFO -> InfoSheet(onDismiss = { panel = null })
        MinePanel.SHARE -> ShareSheet(onDismiss = { panel = null })
        MinePanel.RESOURCES -> OfflineResourcesSheet(
            ditingPack = state.ditingOfflinePack,
            ocrPack = state.offlineOcrPack,
            onDownloadDiting = onDownloadDitingOfflinePack,
            onCancelDiting = onCancelDitingOfflinePackDownload,
            onDeleteDiting = onDeleteDitingOfflinePack,
            onImportDiting = onImportDitingOfflinePack,
            onDownloadOcr = onDownloadOfflineOcrPack,
            onCancelOcr = onCancelOfflineOcrPackDownload,
            onDeleteOcr = onDeleteOfflineOcrPack,
            onImportOcr = onImportOfflineOcrPack,
            onSelectOcrPack = onSelectOcrPack,
            onSelectDitingPack = onSelectDitingPack,
            onDismiss = { panel = null },
        )
        null -> Unit
    }
}

private fun offlineResourceSummary(ditingCount: Int, ocrCount: Int): String =
    if (ditingCount + ocrCount == 0) "语音与 OCR · 按需安装"
    else "已安装：语音 $ditingCount 包 · OCR $ocrCount 包"

@Composable
private fun MineTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    PaperCard(modifier = modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LoginSheet(
    state: AppUiState,
    onRegister: (String, String) -> Unit,
    onLogin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val accountEmail = state.settings.accountEmail
    val isRegistered = accountEmail.isNotEmpty()

    // 登录成功后自动关闭
    LaunchedEffect(state.settings.accountLoggedIn) {
        if (state.settings.accountLoggedIn) onDismiss()
    }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isRegistered) {
                Text("登录弦歌册", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "账号：$accountEmail",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "隐藏" else "显示")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onLogin(password) },
                    enabled = password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("登录")
                }
            } else {
                Text("注册账号", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "账号信息只保存在本机，不会上传。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码（至少 6 位）") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "隐藏" else "显示")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("确认密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                val canSubmit = email.isNotBlank() &&
                    password.length >= 6 &&
                    password == confirmPassword
                Button(
                    onClick = { onRegister(email, password) },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("注册并登录")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AiSheet(
    state: AppUiState,
    onSaveAi: (Boolean, String, String, String, String, String, Boolean, String) -> Unit,
    onTestAi: suspend (Boolean, String, String, String, String, String, Boolean, String) -> Result<Unit>,
    onSaveDitingTranscription: (String, String) -> Unit,
    onSaveDitingAiAnnotation: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var aiEnabled by remember(state.settings.aiEnabled) { mutableStateOf(state.settings.aiEnabled) }
    var provider by remember(state.settings.aiProvider) { mutableStateOf(state.settings.aiProvider) }
    var baseUrl by remember(state.settings.aiBaseUrl) { mutableStateOf(state.settings.aiBaseUrl) }
    var model by remember(state.settings.aiModel) { mutableStateOf(state.settings.aiModel) }
    var visionModel by remember(state.settings.aiVisionModel) { mutableStateOf(state.settings.aiVisionModel) }
    var authHeader by remember(state.settings.aiAuthHeader) { mutableStateOf(state.settings.aiAuthHeader) }
    var supportsVision by remember(state.settings.aiSupportsVision) { mutableStateOf(state.settings.aiSupportsVision) }
    var apiKey by remember { mutableStateOf("") }
    var showConnectionOptions by remember { mutableStateOf(false) }
    var transcriptionModel by remember(state.settings.ditingTranscriptionModel) { mutableStateOf(state.settings.ditingTranscriptionModel) }
    var transcriptionEndpoint by remember(state.settings.ditingTranscriptionEndpoint) { mutableStateOf(state.settings.ditingTranscriptionEndpoint) }
    var aiAnnotationEnabled by remember(state.settings.ditingAiAnnotationEnabled) { mutableStateOf(state.settings.ditingAiAnnotationEnabled) }
    val providers = remember { aiProviderPresets() }
    val scope = rememberCoroutineScope()
    var testJob by remember { mutableStateOf<Job?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(aiEnabled, provider, baseUrl, model, visionModel, authHeader, supportsVision, apiKey) {
        testJob?.cancel()
        testing = false
        testResult = null
    }

    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("智能服务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("帮助你从通知、图片和课堂录音中整理信息。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                PaperCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("启用云端服务", fontWeight = FontWeight.Medium)
                            Text("使用你配置的服务识别和整理内容。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(aiEnabled, { aiEnabled = it })
                    }
                }
            }
            item {
                Text("服务商", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        providers.forEach { preset ->
                            FilterChip(
                                selected = provider == preset.name,
                                onClick = {
                                    provider = preset.name
                                    baseUrl = preset.baseUrl
                                    model = preset.textModel
                                    visionModel = preset.visionModel
                                    supportsVision = preset.supportsVision
                                    authHeader = preset.authHeader
                                    preset.transcriptionModel?.let {
                                        transcriptionModel = it
                                        transcriptionEndpoint = preset.transcriptionEndpoint
                                    }
                                },
                                label = { Text(preset.name) },
                            )
                        }
                }
            }
            item { OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("接口地址") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(model, { model = it }, label = { Text("文本模型") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(visionModel, { visionModel = it }, label = { Text("视觉模型（截图识别）") }, modifier = Modifier.fillMaxWidth()) }
            if (provider == "通义千问") item {
                Text("此预设使用百炼北京地域：文字、图片和 Fun-ASR 转写共用下方密钥。请在同一地域开通相应模型。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                TextButton(onClick = { showConnectionOptions = !showConnectionOptions }) {
                    Text(if (showConnectionOptions) "收起其他连接设置" else "其他连接设置")
                }
                if (showConnectionOptions) {
                    OutlinedTextField(authHeader, { authHeader = it },
                        label = { Text("身份验证格式") },
                        supportingText = { Text("服务商未特别要求时保持默认。{key} 会自动替换为你填写的密钥。") },
                        modifier = Modifier.fillMaxWidth())
                }
            }
            item { Text("谛听云端转写", fontWeight = FontWeight.SemiBold) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动整理课堂重点", fontWeight = FontWeight.Medium)
                        Text("将课堂文字发送到所选服务，帮助找出重点和提问。此功能只发送文字。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(aiAnnotationEnabled, { aiAnnotationEnabled = it })
                }
            }
            item { Text("保存云端配置并开启本节课的“课堂文字记录”后，会优先上传音频进行识别。关闭文字记录时只保存录音。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                transcriptionModel = "fun-asr-realtime"
                                transcriptionEndpoint = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Fun-ASR 预设") }
                        OutlinedButton(
                            onClick = {
                                transcriptionModel = "whisper-1"
                                transcriptionEndpoint = ""
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("通用服务预设") }
                    }
                    Text("点击预设只填写模型与转写地址，不修改密钥；点“保存”后生效。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { OutlinedTextField(transcriptionModel, { transcriptionModel = it }, label = { Text("云端转写模型") }, modifier = Modifier.fillMaxWidth()) }
            item {
                OutlinedTextField(
                    transcriptionEndpoint, { transcriptionEndpoint = it },
                    label = { Text("云端转写地址（可选）") },
                    supportingText = {
                        Text(if (transcriptionModel.trim().startsWith("fun-asr-realtime"))
                            "留空使用阿里云北京地域的默认地址。"
                        else "留空使用上方服务地址，也可填写服务商提供的转写地址。")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "课堂转写使用下方密钥。请确认所选语音服务已开通且额度充足；阿里云其他地域需填写对应地址。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                )
            }

            item { Text("未配置云端时使用可用的本地识别。云端失败时会尝试已选的离线包；识别失败也不会中断原音保存。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("启用图片识别")
                    Switch(supportsVision, { supportsVision = it })
                }
            }
            item {
                OutlinedTextField(
                    apiKey,
                    { apiKey = it },
                    label = { Text(if (state.settings.encryptedApiKey.isBlank()) "API 密钥" else "API 密钥（留空不修改）") },
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                if (testing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在检查文字服务…", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
                testResult?.let {
                    Text(it, Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (testing) {
                                testJob?.cancel()
                                testing = false
                                testResult = "已停止测试，设置尚未保存。"
                            } else {
                                testing = true
                                testResult = null
                                testJob = scope.launch {
                                    try {
                                        testResult = onTestAi(aiEnabled, provider, baseUrl, model, visionModel, authHeader, supportsVision, apiKey)
                                            .fold({ "文字服务已连通。图片和课堂语音请分别试一次；设置需点击保存后生效。" },
                                                { it.message ?: "连接未完成，请检查配置后重试。" })
                                    } finally { testing = false }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (testing) "停止测试" else "测试文字服务") }
                    Button(
                        enabled = !testing,
                        onClick = {
                            onSaveAi(aiEnabled, provider, baseUrl, model, visionModel, authHeader, supportsVision, apiKey)
                            onSaveDitingTranscription(transcriptionModel, transcriptionEndpoint)
                            onSaveDitingAiAnnotation(aiAnnotationEnabled)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("保存") }
                }
                Text("测试仅发送一条测试文字，不发送你的图片、录音或笔记。",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun InboxSheet(
    state: AppUiState,
    onPickImage: () -> Unit,
    onOpenInbox: (InboxItemEntity) -> Unit,
    onRetryInbox: (InboxItemEntity) -> Unit,
    onDeleteInbox: (InboxItemEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("收件信息", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("分享来的文字与图片都在这里核对。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onPickImage, modifier = Modifier.padding(start = 12.dp)) {
                        Text("导入图片")
                    }
                }
            }
            if (state.inbox.isEmpty()) {
                item {
                    PaperCard {
                        Text("暂无收件记录", fontWeight = FontWeight.Medium)
                        Text("从系统分享文字、图片，或在这里导入图片后会出现在此处。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(state.inbox, key = { it.id }) { item ->
                    PaperCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(item.status.label(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    item.ocrText.ifBlank { item.originalText }.ifBlank { item.errorMessage ?: "图片收件" }.take(96),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row {
                                TextButton(onClick = { onOpenInbox(item) }) { Text("打开") }
                                if (item.status == InboxStatus.FAILED && item.imagePath != null) {
                                    TextButton(onClick = { onRetryInbox(item) }) { Text("重试") }
                                }
                                IconButton(onClick = { onDeleteInbox(item) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BackupSheet(onExport: () -> Unit, onImport: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("本地备份", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            PaperCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(".xiangece 本地文件", fontWeight = FontWeight.SemiBold)
                        Text("备份不包含服务密钥；恢复后如需云端识别，请重新填写。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Text(" 导出")
                    }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("恢复") }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun InfoSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("关于弦歌册", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            PaperCard {
                Text("弦歌册 · 大学生校园助手", fontWeight = FontWeight.SemiBold)
                Text("版本 ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("大学诸事，尽入一册。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PaperCard {
                Text("简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "弦歌册是一款面向大学生的校园生活助手。课程表、通知、截图、笔记、课堂录音和日常计划，都可以在这里整理、提醒和回看；重要内容尽量留在你的设备上。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PaperCard {
                Text("核心功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "• 今日：查看当天安排、任务和提醒，快速知道接下来要做什么\n" +
                        "• 收件：接收分享来的文字和图片，确认后整理成日程、课程或备忘\n" +
                        "• 课程：管理多套课表，按周或按天查看，支持文件、图片和口令导入\n" +
                        "• 笔记库：编辑和预览 Markdown，收集资料、查找笔记，核对整理结果的来源\n" +
                        "• 谛听：录下课堂，实时或离线转成文字，回听重点片段和提问内容\n" +
                        "• 厚积：记录习惯、连续天数和每日坚持\n" +
                        "• 百宝：日历日程、成绩、课程时间、番茄钟、记账等常用工具\n" +
                        "• 我的：管理智能服务、离线资源、备份、外观、隐私和分享",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PaperCard {
                Text("数据与账号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "课程、任务、录音、文字和设置默认保存在这台设备上。离线识别资源可按需下载；云端识别仅在你配置并授权使用时发送内容。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PaperCard {
                Text("软件与素材许可", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "自有源码采用 Apache-2.0。原创角色与品牌美术归 QwQBiG 所有，保留所有权利。图片范围、历史授权和第三方许可可在下方查看。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LocalPolicyButton("使用说明", "license.txt")
            LocalPolicyButton("第三方组件说明", "third-party.txt")
            PaperCard {
                Text("官方网站与文档", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    XIANGECE_PUBLIC_URL,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(XIANGECE_PUBLIC_URL)))
                    },
                )
                Text(
                    "查看应用介绍与联系方式。使用说明也可以在应用内离线阅读。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "权限、录音、离线识别和联网数据处理说明见「我的 → 隐私」。使用说明与隐私政策均可离线阅读。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "版权所有 © 2026 弦歌册（iqwqi）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun InboxStatus.label(): String = when (this) {
    InboxStatus.PENDING -> "待确认"
    InboxStatus.CONFIRMED -> "已收录"
    InboxStatus.FAILED -> "需处理"
    InboxStatus.PROCESSING -> "处理中"
}

private data class AiProviderPreset(
    val name: String,
    val baseUrl: String,
    val textModel: String,
    val visionModel: String,
    val supportsVision: Boolean,
    val authHeader: String = "Authorization: Bearer {key}",
    val transcriptionModel: String? = null,
    val transcriptionEndpoint: String = "",
)

private fun aiProviderPresets() = listOf(
    AiProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini", "gpt-4.1-mini", true),
    AiProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4.1-mini", "openai/gpt-4.1-mini", true),
    AiProviderPreset("硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen3-32B", "Qwen/Qwen2.5-VL-32B-Instruct", true),
    AiProviderPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "", false),
    AiProviderPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "qwen-vl-plus", true,
        transcriptionModel = "fun-asr-realtime", transcriptionEndpoint = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"),
    AiProviderPreset("智谱", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "glm-4v-flash", true),
    AiProviderPreset("火山方舟", "https://ark.cn-beijing.volces.com/api/v3", "doubao-lite-32k", "doubao-vision-lite-32k", true),
    AiProviderPreset("自定义", "", "", "", false),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
private fun QuotesSheet(
    quotes: List<CustomQuoteEntity>,
    onAddQuote: (String, String) -> Unit,
    onUpdateQuote: (Long, String, String, Int) -> Unit,
    onDeleteQuote: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreator by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustomQuoteEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<CustomQuoteEntity?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("每日箴言", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "共 ${quotes.size} 条 · 长按可排序",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { showCreator = true }) { Text("新增箴言") }
            }

            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(quotes, key = { it.id }) { quote ->
                    QuoteItem(
                        quote = quote,
                        onEdit = { editing = quote },
                        onDelete = { pendingDelete = quote },
                    )
                }
                item { Spacer(Modifier.padding(bottom = 16.dp)) }
            }
        }
    }

    if (showCreator) {
        QuoteEditorDialog(
            initial = null,
            onDismiss = { showCreator = false },
            onSave = { text, author ->
                onAddQuote(text, author)
                showCreator = false
            },
        )
    }
    editing?.let { quote ->
        QuoteEditorDialog(
            initial = quote,
            onDismiss = { editing = null },
            onSave = { text, author ->
                onUpdateQuote(quote.id, text, author, quote.orderIndex)
                editing = null
            },
        )
    }
    pendingDelete?.let { quote ->
        AppConfirmDialog(
            title = "删除这条箴言？",
            message = "删除后将不再出现在每日箴言池中。",
            onConfirm = {
                onDeleteQuote(quote.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
            confirmLabel = "删除",
            dismissLabel = "取消",
            isDanger = true,
        )
    }
}

@Composable
private fun QuoteItem(
    quote: CustomQuoteEntity,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    PaperCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                quote.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (quote.author.isNotBlank()) {
                Text(
                    "— ${quote.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (onEdit != null) {
                    TextButton(onClick = onEdit) { Text("编辑") }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除")
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuoteEditorDialog(
    initial: CustomQuoteEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial?.text.orEmpty()) }
    var author by remember(initial) { mutableStateOf(initial?.author.orEmpty()) }

    AppFormSheet(
        title = if (initial == null) "新增长期箴言" else "修改箴言",
        onConfirm = { onSave(text, author) },
        onDismiss = onDismiss,
        confirmEnabled = text.isNotBlank(),
    ) {
        AppTextField(
            value = text,
            onValueChange = { text = it },
            label = "箴言内容",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 2,
        )
        AppTextField(
            value = author,
            onValueChange = { author = it },
            label = "署名（可选）",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RemindersSheet(
    state: AppUiState,
    permissionState: PermissionState,
    onSendTestNotification: () -> Unit,
    onRequestNotificationRuntime: () -> Unit,
    onRequestTestNotificationRuntime: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val upcomingReminders = state.reminders
        .filter { it.enabled && it.triggerAtEpochMillis > now }
        .sortedBy { it.triggerAtEpochMillis }
        .take(20)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("提醒诊断", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "验证通知是否正常工作",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 权限状态
            PaperCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 通知权限
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = if (permissionState.notificationsGranted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("通知权限", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (permissionState.notificationsGranted) "已开启，可以正常接收课程、任务和事件提醒"
                                else "未开启，无法在系统通知栏显示提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (permissionState.notificationsGranted) {
                            Button(onClick = onOpenNotificationSettings) { Text("设置") }
                        } else {
                            val buildOk = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }
                            if (buildOk) {
                                Button(onClick = onRequestNotificationRuntime) { Text("请求") }
                                Spacer(Modifier.padding(horizontal = 3.dp))
                                Button(onClick = onOpenNotificationSettings) { Text("设置") }
                            } else {
                                Button(onClick = onOpenNotificationSettings) { Text("去开启") }
                            }
                        }
                    }
                    // 精确闹钟权限（Android 12+ 才可能缺）
                    val exactSdkOk = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
                    if (exactSdkOk) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.AccessTime,
                                contentDescription = null,
                                tint = if (permissionState.exactAlarmGranted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("精确提醒", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (permissionState.exactAlarmGranted) "已允许设置精确闹钟，提醒时间会准时触发"
                                    else "未授予，系统可能延迟或推迟提醒（如省电时）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(onClick = onOpenExactAlarmSettings) {
                                Text(if (permissionState.exactAlarmGranted) "设置" else "去开启")
                            }
                        }
                    }
                }
            }

            // 测试推送按钮
            PaperCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("测试本地推送", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "点击按钮后 3 秒发送一条测试通知",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = {
                                if (permissionState.notificationsGranted) {
                                    onSendTestNotification()
                                } else {
                                    onRequestTestNotificationRuntime()
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.Notifications, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text("发送")
                        }
                    }
                }
            }

            // 即将到来的提醒列表
            Text(
                "已规划的提醒 (${upcomingReminders.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (upcomingReminders.isEmpty()) {
                PaperCard {
                    Text(
                        "暂无已规划的提醒。当你创建课程、任务或事件时，会自动设置提醒。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(upcomingReminders, key = { it.id }) { reminder ->
                        ReminderDiagnosticItem(reminder = reminder)
                    }
                }
            }

            // 提示信息
            Text(
                "提示：如果测试通知没有出现，请检查系统通知权限是否已授予弦歌册。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ReminderDiagnosticItem(reminder: ReminderEntity) {
    val now = System.currentTimeMillis()
    val triggerTime = java.time.Instant.ofEpochMilli(reminder.triggerAtEpochMillis)
    val timeText = java.time.LocalDateTime.ofInstant(triggerTime, java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))

    val channelName = when (reminder.channel) {
        ReminderChannels.COURSE -> "课程"
        ReminderChannels.TASK -> "任务"
        ReminderChannels.EVENT -> "事件"
        else -> "其他"
    }

    val targetName = when (reminder.targetType) {
        ReminderTargets.COURSE -> "课程提醒"
        ReminderTargets.TASK -> "任务提醒"
        ReminderTargets.EVENT -> "事件提醒"
        else -> "其他提醒"
    }

    val timeUntil = reminder.triggerAtEpochMillis - now
    val timeUntilText: String = when {
        timeUntil < 0 -> "已过期"
        timeUntil < 60_000L -> "${timeUntil / 1000L}秒后"
        timeUntil < 3_600_000L -> "${timeUntil / 60_000L}分钟后"
        timeUntil < 86_400_000L -> "${timeUntil / 3_600_000L}小时后"
        else -> "${timeUntil / 86_400_000L}天后"
    }

    PaperCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(reminder.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "${reminder.body} · $targetName · $channelName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(timeText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    timeUntilText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AppearanceSheet(
    state: AppUiState,
    onSetThemeSeed: (String) -> Unit,
    onOpenWardrobe: () -> Unit,
    onSetDarkMode: (Boolean) -> Unit,
    onSetFollowSystemTheme: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AppFullScreenSheet("外观与角色", onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize().testTag("appearance-scroll").verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(onClick = onOpenWardrobe, modifier = Modifier.fillMaxWidth()) {
                Text("角色衣橱 · ${state.settings.companion.outfit.label}")
            }

            Text(
                when {
                    state.settings.followSystemTheme -> "当前：跟随系统"
                    state.settings.darkMode -> "当前：深色"
                    else -> "当前：浅色"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            PaperCard {
                Text("显示模式", style = MaterialTheme.typography.titleMedium)
                Text(
                    "选择固定外观，或随手机切换深浅模式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !state.settings.followSystemTheme && !state.settings.darkMode,
                        onClick = {
                            onSetFollowSystemTheme(false)
                            onSetDarkMode(false)
                        },
                        label = { Text("浅色") },
                        modifier = Modifier.height(48.dp),
                    )
                    FilterChip(
                        selected = !state.settings.followSystemTheme && state.settings.darkMode,
                        onClick = {
                            onSetFollowSystemTheme(false)
                            onSetDarkMode(true)
                        },
                        label = { Text("深色") },
                        modifier = Modifier.height(48.dp),
                    )
                    FilterChip(
                        selected = state.settings.followSystemTheme,
                        onClick = { onSetFollowSystemTheme(true) },
                        label = { Text("跟随系统") },
                        modifier = Modifier.height(48.dp),
                    )
                }
            }

            win.iqwqi.xiangece.ui.companion.ThemePaletteChoices(
                selectedId = state.settings.themeSeed,
                custom = state.settings.companion.palette == win.iqwqi.xiangece.data.settings.CompanionPalette.CUSTOM,
                onSelect = onSetThemeSeed,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ShareSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("转发弦歌册", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            PaperCard {
                Text("分享给同学", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "把课程表、收件整理、谛听课堂录音和常用小工具分享给身边的同学。对方可以先了解功能，再决定是否安装。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "弦歌册｜把课程、通知、课堂录音和日常计划收进一个工具箱。\n$XIANGECE_PUBLIC_URL",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "弦歌册｜把课程、通知、课堂录音和日常计划收进一个工具箱。\n$XIANGECE_PUBLIC_URL",
                            )
                        }
                        context.startActivity(Intent.createChooser(send, "分享弦歌册"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Text(" 转发给同学")
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("完成")
            }
        }
    }
}
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PrivacySheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("隐私与权限", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            PaperCard {
                Text("数据存储", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "课程、任务、事件、习惯、成绩、录音和设置默认保存在本机。应用不会主动扫描相册，只读取你明确选择或分享的内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PaperCard {
                Text("通知权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "用于发送课程提醒、任务截止提醒、校园事件通知与番茄钟完成提示。Android 13 及以上版本需你主动授权；你可在「我的 → 提醒诊断」查看权限状态并跳转系统设置随时开启或关闭。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PaperCard {
                Text("精确闹钟（提醒准时）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "为保证提醒准时触发，Android 12 及以上版本需授予「精确闹钟」权限；未授予时系统可能延迟提醒（如省电模式下）。你可在「我的 → 提醒诊断」授予。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PaperCard {
                Text("存储权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "用于保存你主动选择的图片、课表文件、谛听录音和本地备份。文件默认留在本机，是否导出或分享由你决定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PaperCard {
                Text("麦克风与课堂录音", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "谛听只有在你主动进入功能并点击「开始录音」后才会使用麦克风。录音通过前台服务持续运行，并在系统通知栏显示状态；默认保存在应用私有目录。当前版本的课堂文字、重点和问题会进入本地备份，但音频文件默认不进入备份。请在使用前遵守学校和当地关于课堂录音的规定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PaperCard {
                Text("相机 / 相册权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "用于拍摄或选择课表、通知图片。使用已安装的离线 OCR 时，图片在本机识别；如果你主动选择 AI 识别，图片会按确认提示发送到你配置的服务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PaperCard {
                Text("云端识别与数据处理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "云端服务默认关闭。开启后，只有在你主动使用文字整理、图片识别、课堂转写或重点整理时，才会把必要内容发送到你填写的服务商；音频不会因为打开页面而自动上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PaperCard {
                Text("备份与导出", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Android 自动云备份已被禁用。你可主动导出包含收件图片副本的本地 .xiangece 备份并自行保管；卸载会清除应用私有数据，已导出或分享的副本需要另行删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PaperCard {
                Text("隐私承诺", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "1. 不主动收集你的个人身份信息\n" +
                        "2. 课程、任务、录音和设置默认保存在本机\n" +
                        "3. 离线识别不需要把内容上传到云端\n" +
                        "4. 云端服务只有在你主动配置和使用时才会发送必要内容\n" +
                        "5. 录音只在你主动开始后进行，并显示系统状态\n" +
                        "6. 你可以随时导出或删除自己的数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LocalPolicyButton("阅读完整隐私政策", "privacy.txt")
            PaperCard {
                Text("联系与网站说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    XIANGECE_PUBLIC_URL,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(XIANGECE_PUBLIC_URL)))
                    },
                )
                Text(
                    "隐私政策可以在应用内离线阅读。如有疑问，可通过网站联系。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OfflineResourcesSheet(
    ditingPack: DitingOfflinePackState,
    ocrPack: OfflineOcrPackState,
    onDownloadDiting: () -> Unit,
    onCancelDiting: () -> Unit,
    onDeleteDiting: () -> Unit,
    onImportDiting: () -> Unit,
    onDownloadOcr: () -> Unit,
    onCancelOcr: () -> Unit,
    onDeleteOcr: () -> Unit,
    onImportOcr: () -> Unit,
    onSelectOcrPack: (String) -> Unit,
    onSelectDitingPack: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OfflineResourcesCard(
                ditingPack = ditingPack,
                ocrPack = ocrPack,
                onDownloadDiting = onDownloadDiting,
                onCancelDiting = onCancelDiting,
                onDeleteDiting = onDeleteDiting,
                onImportDiting = onImportDiting,
                onDownloadOcr = onDownloadOcr,
                onCancelOcr = onCancelOcr,
                onDeleteOcr = onDeleteOcr,
                onImportOcr = onImportOcr,
                onSelectOcrPack = onSelectOcrPack,
                onSelectDitingPack = onSelectDitingPack,
            )
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("完成")
            }
        }
    }
}

@Composable
private fun OfflineResourcesCard(
    ditingPack: DitingOfflinePackState,
    ocrPack: OfflineOcrPackState,
    onDownloadDiting: () -> Unit,
    onCancelDiting: () -> Unit,
    onDeleteDiting: () -> Unit,
    onImportDiting: () -> Unit,
    onDownloadOcr: () -> Unit,
    onCancelOcr: () -> Unit,
    onDeleteOcr: () -> Unit,
    onImportOcr: () -> Unit,
    onSelectOcrPack: (String) -> Unit,
    onSelectDitingPack: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("我的资源", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "按需下载或导入，选择适合你的识别方式。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        PaperCard {
            Text("图片文字识别 · OCR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("用于课表、通知和日常事项的图片文字识别。", style = MaterialTheme.typography.bodySmall)
            OfflineResourceChoices(
                choices = OfflineOcrPackCatalog.packs.map { OfflineResourceChoice(it.id, it.title, it.description, it.totalBytes) },
                selectedId = ocrPack.selectedPackId,
                installedIds = ocrPack.installedPackIds,
                enabled = !ocrPack.downloading,
                onSelect = onSelectOcrPack,
            )
            OfflineResourceRow(
                title = "当前选择：${OfflineOcrPackCatalog.find(ocrPack.selectedPackId)?.title.orEmpty()}",
                subtitle = if (ocrPack.installed) "已准备好，可在本机识别图片。"
                    else "下载或导入后，即可在本机识别图片。",
                installed = ocrPack.installed, bundled = ocrPack.bundled,
                downloading = ocrPack.downloading, paused = ocrPack.paused,
                progress = ocrPack.progress, currentFile = ocrPack.currentFile, errorMessage = ocrPack.errorMessage,
                onDownload = onDownloadOcr, onCancel = onCancelOcr, onDelete = onDeleteOcr, onImport = onImportOcr,
            )
        }
        PaperCard {
            Text("课堂语音识别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("开启课堂文字记录并配置云端后优先使用云端；本地转写使用当前选中的已安装包。", style = MaterialTheme.typography.bodySmall)
            OfflineResourceChoices(
                choices = DitingOfflinePackCatalog.packs.map { OfflineResourceChoice(it.id, it.title, it.description, it.totalBytes) },
                selectedId = ditingPack.selectedPackId,
                installedIds = ditingPack.installedPackIds,
                enabled = !ditingPack.downloading,
                onSelect = onSelectDitingPack,
            )
            OfflineResourceRow(
                title = "当前选择：${DitingOfflinePackCatalog.find(ditingPack.selectedPackId)?.title.orEmpty()}",
                subtitle = if (ditingPack.installed) "已准备好，可在本机将录音转成文字。"
                    else "下载或导入后，即可在本机将录音转成文字。",
                installed = ditingPack.installed, bundled = ditingPack.bundled,
                downloading = ditingPack.downloading, paused = ditingPack.paused,
                progress = ditingPack.progress, currentFile = ditingPack.currentFile, errorMessage = ditingPack.errorMessage,
                onDownload = onDownloadDiting, onCancel = onCancelDiting, onDelete = onDeleteDiting, onImport = onImportDiting,
            )
        }
        Text(
            "已有配套资源包时，可直接导入 ZIP 文件。安装时请留足存储空间；下载和删除只针对当前选择。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class OfflineResourceChoice(val id: String, val title: String, val description: String, val totalBytes: Long)

@Composable
private fun OfflineResourceChoices(
    choices: List<OfflineResourceChoice>,
    selectedId: String,
    installedIds: Set<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.forEach { choice ->
            val selected = choice.id == selectedId
            val installed = choice.id in installedIds
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(choice.id) })
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null, enabled = enabled)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(choice.title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(choice.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        listOfNotNull(
                            android.text.format.Formatter.formatShortFileSize(context, choice.totalBytes),
                            if (installed) "已安装" else "未安装",
                            "当前选择".takeIf { selected },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!enabled) {
            Text("正在处理当前资源，完成或暂停后可切换。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OfflineResourceRow(
    title: String,
    subtitle: String,
    installed: Boolean,
    bundled: Boolean,
    downloading: Boolean,
    paused: Boolean,
    progress: Float,
    currentFile: String,
    errorMessage: String?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            when {
                downloading -> Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                installed -> TextButton(onClick = onDelete) { Text("删除当前包") }
                paused -> Text("已暂停", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                bundled -> Text("已内置", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                else -> Text("未安装", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!errorMessage.isNullOrBlank()) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        when {
            downloading -> {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("正在处理${if (currentFile.isBlank()) "" else "：$currentFile"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(if (bundled) "取消安装" else "暂停下载") }
            }
            paused -> OfflineResourceActions(if (bundled) "继续安装" else "继续下载", onDownload, onImport)
            !installed && !errorMessage.isNullOrBlank() -> {
                OfflineResourceActions(if (bundled) "重新安装" else "重试下载", onDownload, onImport)
            }
            !installed -> OfflineResourceActions(if (bundled) "安装" else "下载", onDownload, onImport)
        }
    }
}

@Composable
private fun OfflineResourceActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onPrimary, modifier = Modifier.weight(1f)) { Text(primaryLabel) }
        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("导入 ZIP") }
    }
}

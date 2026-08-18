package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackState
import win.iqwqi.xiangece.ui.AppUiState
import win.iqwqi.xiangece.ui.components.AppConfirmDialog
import win.iqwqi.xiangece.ui.components.AppFormSheet
import win.iqwqi.xiangece.ui.components.AppTextField
import win.iqwqi.xiangece.ui.components.BrandHeader
import win.iqwqi.xiangece.ui.components.InkDivider
import win.iqwqi.xiangece.ui.components.PaperCard

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
    onTestAi: (Boolean, String, String, String, String, String, Boolean, String) -> Unit,
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
) {
    var panel by remember { mutableStateOf<MinePanel?>(null) }
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
        item { BrandHeader("我的", "账户、AI 与数据中心", icon = Icons.Outlined.Person) }

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
                                    Text("注册账号以启用个人空间", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.SmartToy,
                    title = "AI",
                    subtitle = if (state.settings.aiEnabled) state.settings.aiProvider else "未启用",
                    onClick = { panel = MinePanel.AI },
                )
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.TextSnippet,
                    title = "收件",
                    subtitle = if (latestInbox == null) "暂无记录" else "${state.inbox.size} 条记录",
                    onClick = { panel = MinePanel.INBOX },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Save,
                    title = "我的资源",
                    subtitle = offlineResourceSummary(state.ditingOfflinePack.installed, state.offlineOcrPack.installed),
                    onClick = { panel = MinePanel.RESOURCES },
                )
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.CloudOff,
                    title = "本地备份",
                    subtitle = ".xiangece 导出恢复",
                    onClick = { panel = MinePanel.BACKUP },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Edit,
                    title = "箴言",
                    subtitle = if (state.customQuotes.isEmpty()) "内置箴言" else "${state.customQuotes.size} 条箴言",
                    onClick = { panel = MinePanel.QUOTES },
                )
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Info,
                    title = "信息",
                    subtitle = "关于与隐私说明",
                    onClick = { panel = MinePanel.INFO },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Notifications,
                    title = "提醒",
                    subtitle = "诊断与测试",
                    onClick = { panel = MinePanel.REMINDERS },
                )
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Palette,
                    title = "外观",
                    subtitle = "主题与深色模式",
                    onClick = { panel = MinePanel.APPEARANCE },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Lock,
                    title = "隐私",
                    subtitle = "权限与政策",
                    onClick = { panel = MinePanel.PRIVACY },
                )
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Share,
                    title = "转发",
                    subtitle = "分享给同学",
                    onClick = { panel = MinePanel.SHARE },
                )
            }
        }
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
            onDismiss = { panel = null },
        )
        null -> Unit
    }
}

private fun offlineResourceSummary(ditingInstalled: Boolean, ocrInstalled: Boolean): String = when {
    ditingInstalled && ocrInstalled -> "语音、OCR 已安装"
    ditingInstalled -> "语音已安装 · OCR 未安装"
    ocrInstalled -> "OCR 已安装 · 语音未安装"
    else -> "可选离线能力"
}

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
private fun AiSheet(
    state: AppUiState,
    onSaveAi: (Boolean, String, String, String, String, String, Boolean, String) -> Unit,
    onTestAi: (Boolean, String, String, String, String, String, Boolean, String) -> Unit,
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
    var transcriptionModel by remember(state.settings.ditingTranscriptionModel) { mutableStateOf(state.settings.ditingTranscriptionModel) }
    var transcriptionEndpoint by remember(state.settings.ditingTranscriptionEndpoint) { mutableStateOf(state.settings.ditingTranscriptionEndpoint) }
    var aiAnnotationEnabled by remember(state.settings.ditingAiAnnotationEnabled) { mutableStateOf(state.settings.ditingAiAnnotationEnabled) }
    val providers = remember { aiProviderPresets() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("AI 服务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("联网增强解析与图片识别。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                PaperCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("启用 AI", fontWeight = FontWeight.Medium)
                            Text("文本模型处理通知；视觉模型处理截图。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(aiEnabled, { aiEnabled = it })
                    }
                }
            }
            item {
                Text("服务商", fontWeight = FontWeight.SemiBold)
                providers.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            FilterChip(
                                selected = provider == preset.name,
                                onClick = {
                                    provider = preset.name
                                    baseUrl = preset.baseUrl
                                    model = preset.textModel
                                    visionModel = preset.visionModel
                                    supportsVision = preset.supportsVision
                                    authHeader = preset.authHeader
                                },
                                label = { Text(preset.name) },
                            )
                        }
                    }
                }
            }
            item { OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("接口地址") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(model, { model = it }, label = { Text("文本模型") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(visionModel, { visionModel = it }, label = { Text("视觉模型（截图识别）") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(authHeader, { authHeader = it }, label = { Text("鉴权头，使用 {key}") }, modifier = Modifier.fillMaxWidth()) }
            item { Text("谛听云端分段转写", fontWeight = FontWeight.SemiBold) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("谛听 AI 自动标注", fontWeight = FontWeight.Medium)
                        Text("按批次分析转写文字，自动标出重点与提问；不会发送音频。未配置 AI 服务时不会产生请求，本地规则标注仍可用。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(aiAnnotationEnabled, { aiAnnotationEnabled = it })
                }
            }
            item { Text("没有可用的本地识别时，谛听会按音频片段使用你配置的转写服务；只有你主动开启相关功能后才会使用。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { OutlinedTextField(transcriptionModel, { transcriptionModel = it }, label = { Text("云端转写模型（例如 whisper-1）") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(transcriptionEndpoint, { transcriptionEndpoint = it }, label = { Text("联网转写地址（可选）") }, modifier = Modifier.fillMaxWidth()) }

            item { Text("如果没有配置转写服务，仍然可以正常录音，并在之后补充文字。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("支持多模态截图识别")
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onTestAi(aiEnabled, provider, baseUrl, model, visionModel, authHeader, supportsVision, apiKey) },
                        modifier = Modifier.weight(1f),
                    ) { Text("测试连接") }
                    Button(
                        onClick = {
                            onSaveAi(aiEnabled, provider, baseUrl, model, visionModel, authHeader, supportsVision, apiKey)
                            onSaveDitingTranscription(transcriptionModel, transcriptionEndpoint)
                            onSaveDitingAiAnnotation(aiAnnotationEnabled)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("保存") }
                }
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
                        Text("不含 API 密钥；恢复后如需 AI，请重新填写密钥。", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    "弦歌册是一款面向大学生的校园生活助手。课程表、通知、截图、课堂录音和日常计划，都可以在这里整理、提醒和回看；重要内容尽量留在你的设备上。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PaperCard {
                Text("核心功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "• 今日：查看当天安排、任务和提醒，快速知道接下来要做什么\n" +
                        "• 收件：接收分享来的文字和图片，确认后整理成日程、课程或备忘\n" +
                        "• 课程：管理多套课表，按周查看课程，支持文件、图片和口令导入\n" +
                        "• 谛听：录下课堂，实时或离线转成文字，回听重点片段和提问内容\n" +
                        "• 厚积：记录习惯、连续天数和每日坚持\n" +
                        "• 百宝：成绩、课程时间、番茄钟、记账等常用工具\n" +
                        "• 我的：管理 AI、离线资源、备份、外观、隐私和分享",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PaperCard {
                Text("数据与账号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "课程、任务、录音、转写和设置默认保存在这台设备上。离线语音识别和离线 OCR 需要你主动下载；AI 功能也只有在你主动配置和使用时才会联网。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PaperCard {
                Text("开源协议", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "本应用基于 GPL-3.0 协议开源。你可以自由使用、修改与学习源码；任何衍生作品须同样以 GPL-3.0 开源并保留版权声明。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                    "应用介绍、使用文档与完整隐私政策均发布于此。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "权限、录音、离线识别和 AI 数据处理说明见「我的 → 隐私」。完整说明和联系方式请打开上方网页。",
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
)

private fun aiProviderPresets() = listOf(
    AiProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini", "gpt-4.1-mini", true),
    AiProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4.1-mini", "openai/gpt-4.1-mini", true),
    AiProviderPreset("硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen3-32B", "Qwen/Qwen2.5-VL-32B-Instruct", true),
    AiProviderPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "", false),
    AiProviderPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "qwen-vl-plus", true),
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
private fun AppearanceSheet(
    state: AppUiState,
    onSetThemeSeed: (String) -> Unit,
    onSetDarkMode: (Boolean) -> Unit,
    onSetFollowSystemTheme: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val themeSeeds = win.iqwqi.xiangece.ui.theme.themeSeeds
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("外观设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

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
                Text("选择应用自己的外观，不会自动读取手机模式", style = MaterialTheme.typography.titleMedium)
                Text(
                    "只有选择“跟随系统”时，才会根据手机的浅色/深色模式切换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !state.settings.followSystemTheme && !state.settings.darkMode,
                        onClick = {
                            onSetFollowSystemTheme(false)
                            onSetDarkMode(false)
                        },
                        label = { Text("浅色") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = !state.settings.followSystemTheme && state.settings.darkMode,
                        onClick = {
                            onSetFollowSystemTheme(false)
                            onSetDarkMode(true)
                        },
                        label = { Text("深色") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = state.settings.followSystemTheme,
                        onClick = { onSetFollowSystemTheme(true) },
                        label = { Text("跟随系统") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 主题色选择
            Text("主题色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(themeSeeds) { seed ->
                    val isSelected = seed.id == state.settings.themeSeed
                    PaperCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetThemeSeed(seed.id) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(seed.lightPrimary),
                                )
                                Text(
                                    seed.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
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
                Text("AI 识别与数据处理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "AI 功能默认关闭。开启后，只有在你主动使用文字整理、图片识别、课堂转写或自动标注时，才会把必要内容发送到你填写的服务商；音频不会因为打开页面而自动上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PaperCard {
                Text("备份与导出", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Android 自动云备份已被禁用。你可主动导出包含收件图片副本的本地 .xiangece 备份并自行保管；也可随时删除应用以彻底清除所有本地数据。",
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
                        "4. AI 只有在你主动配置和使用时才会发送必要内容\n" +
                        "5. 录音只在你主动开始后进行，并显示系统状态\n" +
                        "6. 你可以随时导出或删除自己的数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PaperCard {
                Text("完整隐私政策", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    XIANGECE_PUBLIC_URL,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(XIANGECE_PUBLIC_URL)))
                    },
                )
                Text(
                    "最新完整的隐私政策与使用文档发布于此网址，如有更新以网页版本为准。",
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
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
) {
    PaperCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("我的资源", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (ditingPack.bundled || ocrPack.bundled) "此安装包已内置离线资源；点“安装”即可使用，无需联网。"
                else "可选的免费离线能力；安装后无需联网，也可以导入资源包。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OfflineResourceRow("中文/英文离线语音识别", "谛听课堂转写 · 中文优先，支持 English 与中英混合 · 约 237 MB", ditingPack.installed, ditingPack.bundled, ditingPack.downloading, ditingPack.paused, ditingPack.progress, ditingPack.currentFile, ditingPack.errorMessage, onDownloadDiting, onCancelDiting, onDeleteDiting, onImportDiting)
            OfflineResourceRow("中文离线 OCR", "图片与课表识别 · 约 21.5 MB", ocrPack.installed, ocrPack.bundled, ocrPack.downloading, ocrPack.paused, ocrPack.progress, ocrPack.currentFile, ocrPack.errorMessage, onDownloadOcr, onCancelOcr, onDeleteOcr, onImportOcr)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            when {
                installed -> {
                    Text("已安装", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onDelete) { Text("删除") }
                }
                downloading -> Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                paused -> Text("已暂停", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                bundled -> Text("已内置", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                else -> Text("未安装", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        when {
            downloading -> {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("正在${if (bundled) "安装" else "下载"}${if (currentFile.isBlank()) "" else "：$currentFile"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(if (bundled) "取消安装" else "暂停下载") }
            }
            paused -> OfflineResourceActions(if (bundled) "继续安装" else "继续下载", onDownload, onImport)
            !installed && !errorMessage.isNullOrBlank() -> {
                Text("${if (bundled) "安装" else "下载"}失败：$errorMessage", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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

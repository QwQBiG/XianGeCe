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
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import win.iqwqi.xiangece.ui.AppUiState
import win.iqwqi.xiangece.ui.components.AppConfirmDialog
import win.iqwqi.xiangece.ui.components.AppFormSheet
import win.iqwqi.xiangece.ui.components.AppTextField
import win.iqwqi.xiangece.ui.components.BrandHeader
import win.iqwqi.xiangece.ui.components.InkDivider
import win.iqwqi.xiangece.ui.components.PaperCard

private enum class MinePanel {
    LOGIN, AI, INFO, BACKUP, INBOX, QUOTES, REMINDERS, APPEARANCE, PRIVACY
}

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
    onRegister: (String, String) -> Unit = { _, _ -> },
    onLogin: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    permissionState: PermissionState = PermissionState(),
    onRequestNotificationRuntime: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
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
                    icon = Icons.Outlined.CloudOff,
                    title = "本地备份",
                    subtitle = ".xiangece 导出恢复",
                    onClick = { panel = MinePanel.BACKUP },
                )
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Edit,
                    title = "箴言",
                    subtitle = if (state.customQuotes.isEmpty()) "内置箴言" else "${state.customQuotes.size} 条箴言",
                    onClick = { panel = MinePanel.QUOTES },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Info,
                    title = "信息",
                    subtitle = "关于与隐私说明",
                    onClick = { panel = MinePanel.INFO },
                )
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Notifications,
                    title = "提醒",
                    subtitle = "诊断与测试",
                    onClick = { panel = MinePanel.REMINDERS },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Palette,
                    title = "外观",
                    subtitle = "主题与深色模式",
                    onClick = { panel = MinePanel.APPEARANCE },
                )
                MineTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Lock,
                    title = "隐私",
                    subtitle = "权限与政策",
                    onClick = { panel = MinePanel.PRIVACY },
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
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            onDismiss = { panel = null },
        )
        MinePanel.APPEARANCE -> AppearanceSheet(
            state = state,
            onSetThemeSeed = onSetThemeSeed,
            onSetDarkMode = onSetDarkMode,
            onDismiss = { panel = null },
        )
        MinePanel.PRIVACY -> PrivacySheet(onDismiss = { panel = null })
        MinePanel.INFO -> InfoSheet(onDismiss = { panel = null })
        null -> Unit
    }
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
                    "账号信息仅保存在本机。密码经 Keystore 加密存储，不会上传。",
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
    val providers = remember { aiProviderPresets() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("AI 服务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("云端增强解析与截图识别。密钥仅本机加密保存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        onClick = { onSaveAi(aiEnabled, provider, baseUrl, model, visionModel, authHeader, supportsVision, apiKey) },
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
                    "弦歌册是一款面向大学生的本地校园事项助手。它把群聊通知、截图和课表中的零散信息转成可编辑、可提醒的课程、任务和校园事件，并以习惯打卡与每日箴言陪伴你沉淀日常。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PaperCard {
                Text("核心功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "• 今日：教学周进度、下一节与当天课程，任务支持全部 / 7 天内 / 逾期 / 无日期筛选，并显示课程归属与最近提醒\n" +
                        "• 收件：接收系统分享的图片 / 文字；文字可本地整理，图片识别需配置支持视觉的 AI，确认后才进入课程或日程\n" +
                        "• 课程：可切换教学周的七天课表、按天列表、空白节次快捷加课、时段冲突提示；支持 PDF / HTML / Excel / 口令导入，并可调用视觉 AI 识别图片\n" +
                        "• 厚积：习惯打卡与连续记录，年度热力图，沉淀每日坚持\n" +
                        "• 百宝：成绩 GPA、考试倒计时、专注番茄、生活费记账等小工具\n" +
                        "• 我的：提醒诊断、外观主题、本地备份恢复、每日箴言等设置",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PaperCard {
                Text("数据与账号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "当前为本地账户，所有业务数据以 Room 数据库为唯一数据源，保存在本机。第一阶段没有账号、聊天、云同步、广告或支付。后续接入云同步或设备迁移时再开放完整登录。",
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
                    "https://iqwqi.win/xiangece/",
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://iqwqi.win/xiangece/")))
                    },
                )
                Text(
                    "应用介绍、使用文档与完整隐私政策均发布于此。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "权限与数据处理说明见「我的 → 隐私」。如需反馈，请访问官方网站。",
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
                        Button(onClick = onSendTestNotification) {
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

            // 深色模式开关
            PaperCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("深色模式", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "跟随系统或手动开启",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.darkMode,
                        onCheckedChange = onSetDarkMode,
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
                    "弦歌册以「本地优先」为原则：课程、任务、事件、习惯、成绩、备份等所有业务数据均以 Room 数据库存储在本机设备，不主动上传任何服务器。应用不主动扫描相册，只读取你明确选择或分享的内容；分享图片会复制到应用私有目录，避免临时 URI 失效。",
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
                    "用于保存课表截图、导出 .xiangece 本地备份文件与缓存识别结果。所有文件均存储在本地设备。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PaperCard {
                Text("相机 / 相册权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "用于拍摄或选择课表图片进行 OCR 识别。图片仅用于本地识别处理，不会上传到服务器。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PaperCard {
                Text("AI 识别与数据处理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "AI 功能默认关闭，由你主动配置服务商（OpenAI 兼容接口）与 API 密钥。密钥使用 Android Keystore 与 AES-GCM 加密存储，不进入日志、备份或版本库。开启 AI 后，仅在你主动使用相关功能时，才会把文字或压缩后的图片发送到你配置的服务商；本应用不经手任何第三方服务器。每次增强前会展示数据离开设备提示，只发送 OCR 文字与学期上下文，不发送原图。",
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
                    "1. 我们不收集任何可识别个人身份的信息\n" +
                        "2. 你的所有数据均存储在本地设备\n" +
                        "3. AI 识别仅在主动触发时发送必要数据，且仅用于识别\n" +
                        "4. 我们不将你的信息分享给任何第三方\n" +
                        "5. 你可随时导出或删除你的数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PaperCard {
                Text("完整隐私政策", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "https://iqwqi.win/xiangece/",
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://iqwqi.win/xiangece/")))
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

package win.iqwqi.xiangece

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import win.iqwqi.xiangece.core.backup.BackupManager
import win.iqwqi.xiangece.core.update.AppUpdateManifest
import win.iqwqi.xiangece.core.update.AppUpdateRepository
import win.iqwqi.xiangece.core.update.AppUpdateState
import win.iqwqi.xiangece.ui.MainViewModel
import win.iqwqi.xiangece.ui.XiangeceRoot
import win.iqwqi.xiangece.ui.components.AppUpdateDialog
import win.iqwqi.xiangece.ui.theme.XiangeceTheme
import win.iqwqi.xiangece.widget.XiangeceWidgetProvider

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    @Inject lateinit var appUpdateRepository: AppUpdateRepository
    @Inject lateinit var backupManager: BackupManager
    private var openMyResources by mutableStateOf(false)
    private var updateState by mutableStateOf<AppUpdateState>(AppUpdateState.Idle)
    private var pendingBackupUpdate by mutableStateOf<AppUpdateManifest?>(null)
    private var checkedForForeground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        openMyResources = intent?.getBooleanExtra("open_my_resources", false) == true

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val useDarkTheme = if (state.settings.followSystemTheme) isSystemInDarkTheme() else state.settings.darkMode
            val backupLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip"),
            ) { uri -> finishBackupAndContinue(uri) }
            XiangeceTheme(
                darkTheme = useDarkTheme,
                themeSeedId = state.settings.themeSeed,
            ) {
                val navigationSurface = MaterialTheme.colorScheme.surface.toArgb()
                val contentBackground = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    window.statusBarColor = contentBackground
                    window.navigationBarColor = navigationSurface
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !useDarkTheme
                        isAppearanceLightNavigationBars = !useDarkTheme
                    }
                }
                XiangeceRoot(state = state, viewModel = viewModel, initialPage = if (openMyResources) 4 else 0)
                AppUpdateDialog(
                    state = updateState,
                    onUpdate = ::downloadAndInstall,
                    onBackupAndUpdate = { manifest ->
                        pendingBackupUpdate = manifest
                        updateState = AppUpdateState.PreparingBackup(manifest)
                        backupLauncher.launch("弦歌册-更新前备份-${System.currentTimeMillis()}")
                    },
                    onRetry = ::downloadAndInstall,
                    onDismiss = { updateState = AppUpdateState.Idle },
                )
            }
            LaunchedEffect(Unit) {
                viewModel.handleIntent(intent)
                setIntent(Intent())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_my_resources", false)) openMyResources = true
        setIntent(intent)
        viewModel.handleIntent(intent)
        setIntent(Intent())
    }

    override fun onStart() {
        super.onStart()
        if (!checkedForForeground) {
            checkedForForeground = true
            lifecycleScope.launch {
                updateState = AppUpdateState.Checking
                updateState = appUpdateRepository.check()
            }
        }
    }

    override fun onStop() {
        checkedForForeground = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        XiangeceWidgetProvider.refreshAll(this)
    }
    private fun finishBackupAndContinue(uri: Uri?) {
        val manifest = pendingBackupUpdate
        pendingBackupUpdate = null
        if (manifest == null) return
        if (uri == null) {
            updateState = AppUpdateState.Available(manifest)
            return
        }
        lifecycleScope.launch {
            runCatching { backupManager.exportTo(uri) }
                .onSuccess { downloadAndInstall(manifest) }
                .onFailure {
                    updateState = AppUpdateState.Failed(
                        manifest = manifest,
                        message = it.message ?: "备份没有保存成功，请重新选择位置",
                        duringBackup = true,
                    )
                }
        }
    }

    private fun downloadAndInstall(manifest: AppUpdateManifest) {
        updateState = AppUpdateState.Downloading(manifest, null)
        lifecycleScope.launch {
            runCatching {
                appUpdateRepository.download(manifest) { progress ->
                    runOnUiThread { updateState = AppUpdateState.Downloading(manifest, progress) }
                }
            }.onSuccess(::installApk).onFailure {
                updateState = AppUpdateState.Failed(manifest, it.message ?: "更新下载失败")
            }
        }
    }

    private fun installApk(file: java.io.File) {
        val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(installIntent) }.onFailure {
            val manifest = (updateState as? AppUpdateState.Downloading)?.manifest
            if (manifest != null) {
                updateState = AppUpdateState.Failed(manifest, "系统安装器没有打开，请重试更新")
            }
        }
    }
}
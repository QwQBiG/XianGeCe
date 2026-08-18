package win.iqwqi.xiangece.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.core.update.AppUpdateManifest
import win.iqwqi.xiangece.core.update.AppUpdateState

@Composable
fun AppUpdateDialog(
    state: AppUpdateState,
    onUpdate: (AppUpdateManifest) -> Unit,
    onBackupAndUpdate: (AppUpdateManifest) -> Unit,
    onRetry: (AppUpdateManifest) -> Unit,
    onDismiss: () -> Unit,
) {
    val manifest = when (state) {
        is AppUpdateState.Available -> state.manifest
        is AppUpdateState.PreparingBackup -> state.manifest
        is AppUpdateState.Downloading -> state.manifest
        is AppUpdateState.Failed -> state.manifest
        else -> return
    }
    val mandatory = manifest.mandatory
    Dialog(
        onDismissRequest = { if (!mandatory) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !mandatory,
            dismissOnClickOutside = !mandatory,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (mandatory) "需要更新后继续使用" else "发现新版本",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "弦歌册 ${manifest.versionName.ifBlank { "新版本" }}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "正常覆盖安装不会清除课表、设置、厚积、任务或谛听记录。你也可以先导出一份备份，再开始更新。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (manifest.releaseNotes.isNotBlank()) {
                    Text(manifest.releaseNotes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (state) {
                    is AppUpdateState.PreparingBackup -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Text("正在保存备份…")
                        }
                    }
                    is AppUpdateState.Downloading -> {
                        Text("正在下载更新，请保持网络连接")
                        if (state.progress == null) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("已完成 ${state.progress}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is AppUpdateState.Failed -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                    else -> Unit
                }
                if (state !is AppUpdateState.PreparingBackup && state !is AppUpdateState.Downloading) {
                    if (state is AppUpdateState.Failed && state.duringBackup) {
                        OutlinedButton(
                            onClick = { onBackupAndUpdate(manifest) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("重新选择备份位置") }
                    } else {
                        OutlinedButton(
                            onClick = { onBackupAndUpdate(manifest) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("先备份，再更新") }
                    }
                    Button(
                        onClick = {
                            if (state is AppUpdateState.Failed) onRetry(manifest) else onUpdate(manifest)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state is AppUpdateState.Failed) "重新下载更新" else "立即更新")
                    }
                    if (!mandatory) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("稍后再说") }
                    }
                }
            }
        }
    }
}

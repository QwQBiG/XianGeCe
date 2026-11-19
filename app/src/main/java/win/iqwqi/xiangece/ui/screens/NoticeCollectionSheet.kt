package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.ui.components.AppFormSheet

@Composable
internal fun NoticeCollectionSheet(
    text: String, onText: (String) -> Unit, useService: Boolean, onUseService: (Boolean) -> Unit,
    serviceAvailable: Boolean, onDismiss: () -> Unit, onParse: () -> Unit, onImage: () -> Unit,
    onResources: () -> Unit, onService: () -> Unit,
) {
    AppFormSheet("通知整理", "先核对内容，再决定保存哪些日程。", onParse, onDismiss,
        confirmLabel = "整理文字", confirmEnabled = text.isNotBlank()) {
        FilledTonalButton(onClick = onImage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Image, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp)); Text("从图片识别")
        }
        OutlinedTextField(text, onText, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 7,
            label = { Text("粘贴通知内容") }, shape = MaterialTheme.shapes.medium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("智能整理", style = MaterialTheme.typography.titleSmall)
                Text(if (serviceAvailable) "使用已配置的服务理解文字" else "配置服务后可启用，本机整理仍然可用",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(useService, onUseService, enabled = serviceAvailable)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onResources) { Text("离线资源") }
            TextButton(onClick = onService) { Text("服务设置") }
        }
    }
}

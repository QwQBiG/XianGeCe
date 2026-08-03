package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import win.iqwqi.xiangece.data.local.InboxItemEntity
import win.iqwqi.xiangece.domain.model.InboxStatus
import win.iqwqi.xiangece.ui.AppUiState
import win.iqwqi.xiangece.ui.components.AppConfirmDialog
import win.iqwqi.xiangece.ui.components.BrandHeader
import win.iqwqi.xiangece.ui.components.EmptyStateCard
import win.iqwqi.xiangece.ui.components.PaperCard

@Composable
fun InboxScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    onPickImage: () -> Unit,
    onOpen: (InboxItemEntity) -> Unit,
    onRetry: (InboxItemEntity) -> Unit,
    onDelete: (InboxItemEntity) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<InboxItemEntity?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            BrandHeader(
                title = "收件",
                subtitle = "截图与文字",
                action = {
                    IconButton(onClick = onPickImage) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "导入图片")
                    }
                },
            )
        }
        if (state.inbox.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "收件箱是空的",
                    message = "从微信、QQ或相册分享截图到弦歌册，也可以点击右上角导入。",
                    icon = Icons.Outlined.AddPhotoAlternate,
                )
            }
        } else {
            items(state.inbox, key = { it.id }) { item ->
                PaperCard(
                    modifier = Modifier.clickable(
                        enabled = item.status == InboxStatus.PENDING,
                    ) { onOpen(item) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.status.label(), color = item.status.color(), modifier = Modifier.weight(1f))
                        Text(formatInboxTime(item.createdAtEpochMillis), style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { pendingDelete = item }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除收件项")
                        }
                    }
                    Text(
                        item.ocrText.ifBlank { item.originalText }.ifBlank { "图片待识别" },
                        maxLines = 3,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    item.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (item.status == InboxStatus.FAILED && !item.imagePath.isNullOrBlank()) {
                        Button(onClick = { onRetry(item) }) {
                            Text("重新识别")
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AppConfirmDialog(
            title = "删除收件项？",
            message = "识别文字和应用私有目录中的图片副本会被删除；已经写入的课程或任务不会受影响。",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDelete(item)
                pendingDelete = null
            },
            confirmLabel = "删除",
            isDanger = true,
        )
    }
}

private fun InboxStatus.label(): String = when (this) {
    InboxStatus.PROCESSING -> "识别中"
    InboxStatus.PENDING -> "待确认"
    InboxStatus.CONFIRMED -> "已收录"
    InboxStatus.FAILED -> "识别失败"
}

@Composable
private fun InboxStatus.color() = when (this) {
    InboxStatus.PROCESSING -> MaterialTheme.colorScheme.secondary
    InboxStatus.PENDING -> MaterialTheme.colorScheme.primary
    InboxStatus.CONFIRMED -> MaterialTheme.colorScheme.onSurfaceVariant
    InboxStatus.FAILED -> MaterialTheme.colorScheme.error
}

private val inboxTimeFormatter = DateTimeFormatter.ofPattern("M-d HH:mm")

private fun formatInboxTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(inboxTimeFormatter)

package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.data.local.TimetableEntity
import win.iqwqi.xiangece.ui.components.PaperCard

@Composable
internal fun TimetableDeleteChoices(timetables: List<TimetableEntity>, onDelete: (Long) -> Unit) {
    var pending by remember { mutableStateOf<TimetableEntity?>(null) }
    PaperCard {
        Text("管理课表", style = MaterialTheme.typography.titleMedium)
        timetables.forEach { book ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(book.name, style = MaterialTheme.typography.bodyLarge)
                    if (book.isCurrent) Text("当前使用", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { pending = book }) {
                    Icon(Icons.Outlined.DeleteOutline, "删除课表：${book.name}", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    pending?.let { book ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("删除“${book.name}”？") },
            text = { Text("这张课表中的课程会被移除，上课提醒会取消。待办、日程、录音与导入原文保留。" +
                if (timetables.size == 1) "删除后会新建一张空白默认课表。" else "删除当前课表后会切换到另一张。") },
            confirmButton = { TextButton(onClick = { onDelete(book.id); pending = null }) {
                Text("确认删除", color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } },
        )
    }
}

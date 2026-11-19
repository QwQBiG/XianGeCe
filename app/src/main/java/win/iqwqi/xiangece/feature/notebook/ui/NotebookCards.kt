package win.iqwqi.xiangece.feature.notebook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.feature.notebook.data.NoteDocument
import win.iqwqi.xiangece.feature.notebook.domain.*

@Composable
internal fun NoteListCard(note: NoteDocument, match: NoteMatch?, onClick: () -> Unit, onPin: () -> Unit, onTag: (String) -> Unit = {}) {
    val info = remember(note.text) { NotebookMarkdown.info(note.text) }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 10.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.record.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (note.record.deletedAt == null) IconButton(onClick = onPin, Modifier.size(48.dp)) {
                    Icon(if (note.record.pinned) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        if (note.record.pinned) "取消收藏" else "收藏", tint = if (note.record.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                } else Icon(Icons.Outlined.Restore, "恢复笔记", Modifier.size(24.dp))
            }
            Text(match?.excerpt ?: info.bodyPreview.take(180).ifBlank { "还没有正文，点开写下第一句。" },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (info.tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                info.tags.take(3).forEach { tag ->
                    Text("#$tag", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(6.dp)).clickable { onTag(tag) }.padding(vertical = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val sourceLabel = when (note.record.sources.firstOrNull()?.kind) { "class" -> "课堂"; "image" -> "图片"; "notes" -> "笔记整理"; else -> "笔记" }
            Text(match?.reason ?: if (note.record.deletedAt != null) "点开恢复" else note.record.folder.ifBlank { sourceLabel } + " · " +
                java.time.Instant.ofEpochMilli(note.modifiedAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun NotebookEmpty(search: Boolean, trash: Boolean, onNew: () -> Unit, onImport: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (search) Icons.Outlined.ManageSearch else Icons.Outlined.AutoStories, null, Modifier.size(48.dp), MaterialTheme.colorScheme.primary)
        Text(if (search) "没有找到匹配内容" else if (trash) "回收站是空的" else "从一篇笔记开始", style = MaterialTheme.typography.titleMedium)
        Text(if (search) "试试标题中的词，或用 #标签 查找。" else if (trash) "移入这里的笔记可以随时恢复。" else "课堂记录、灵感和日常，都可以留在这里。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!search && !trash) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onNew) { Text("写一篇") }
            OutlinedButton(onClick = { onImport(false) }) { Text("导入笔记") }
        }
    }
}

internal val NOTEBOOK_GUIDE = """
    # 把想法留在这里

    一篇笔记就是一个 Markdown 文件。内容会自动保存，也可以从菜单导出。

    ## 写下重点

    用 **粗体** 标出重点，用 *斜体* 留下旁注，或用 ~~删除线~~ 记录改变。

    - 一段课堂记录
    - 一个待研究的问题
    - 一次突然出现的灵感

    - [ ] 找到相关资料
    - [x] 写下第一个想法

    > 不必一开始就整理得很完整，先把值得留住的东西写下来。

    ## 把笔记连起来

    输入 [[下一篇笔记]]，预览时点开。已有同名笔记就会跳转，没有则可以新建。
    也可以写成 [[下一篇笔记|换个显示名称]]。「联系」会显示反向链接和相关内容。

    ## 表格

    | 主题 | 下一步 |
    | --- | --- |
    | 课堂 | 补充不懂的地方 |
    | 阅读 | 记下一句自己的理解 |

    #随记 #使用方法
""".trimIndent()

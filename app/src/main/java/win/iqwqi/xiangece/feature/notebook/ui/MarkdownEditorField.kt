package win.iqwqi.xiangece.feature.notebook.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.feature.notebook.data.NoteDocument

@Composable
internal fun MarkdownEditorField(id: String, text: String, onChange: (String) -> Unit, notes: List<NoteDocument>, onImage: () -> Unit, modifier: Modifier) {
    var value by rememberSaveable(id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(text)) }
    val undo = remember(id) { mutableStateListOf<TextFieldValue>() }
    val redo = remember(id) { mutableStateListOf<TextFieldValue>() }
    var lastCheckpoint by remember(id) { mutableLongStateOf(0) }
    var linkPicker by remember { mutableStateOf(false) }
    LaunchedEffect(text) { if (text != value.text) value = TextFieldValue(text, TextRange(text.length)) }
    fun update(next: TextFieldValue, checkpoint: Boolean = false) {
        if (next.text != value.text) {
            val now = System.currentTimeMillis()
            if (checkpoint || now - lastCheckpoint > 800) { undo += value; if (undo.size > 40) undo.removeAt(0); lastCheckpoint = now }
            redo.clear()
            onChange(next.text)
        }
        value = next
    }
    fun insert(before: String, after: String = "", placeholder: String = "") {
        val start = value.selection.min
        val end = value.selection.max
        val selected = value.text.substring(start, end).ifEmpty { placeholder }
        val replacement = before + selected + after
        update(TextFieldValue(value.text.replaceRange(start, end, replacement),
            if (selected.isNotEmpty()) TextRange(start + before.length, start + before.length + selected.length) else TextRange(start + replacement.length)), true)
    }
    Column(modifier) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                IconButton(enabled = undo.isNotEmpty(), onClick = { redo += value; value = undo.removeAt(undo.lastIndex); onChange(value.text) }) { Icon(Icons.AutoMirrored.Outlined.Undo, "撤销编辑") }
                IconButton(enabled = redo.isNotEmpty(), onClick = { undo += value; value = redo.removeAt(redo.lastIndex); onChange(value.text) }) { Icon(Icons.AutoMirrored.Outlined.Redo, "重做编辑") }
                TextButton(onClick = { insert("## ") }) { Text("标题") }
                TextButton(onClick = { insert("**", "**", "重点") }) { Text("粗体") }
                TextButton(onClick = { insert("\n- ") }) { Text("列表") }
                TextButton(onClick = { insert("\n- [ ] ") }) { Text("待办") }
                TextButton(onClick = { linkPicker = true }) { Text("链接") }
                TextButton(onClick = { insert("\n> ") }) { Text("引用") }
                TextButton(onClick = { insert("\n```\n", "\n```\n", "代码") }) { Text("代码") }
                IconButton(onClick = onImage) { Icon(Icons.Outlined.Image, "插入图片") }
            }
        }
        BasicTextField(value, { update(it) }, modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(22.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 200.dp)) {
                if (value.text.isEmpty()) Text("写下此刻的想法…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            } })
    }
    if (linkPicker) {
        var search by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { linkPicker = false }, title = { Text("链接到笔记") }, text = {
            Column {
                OutlinedTextField(search, { search = it }, label = { Text("查找或新建名称") }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(notes.filter { it.record.id != id && it.record.title.contains(search, true) }) { note ->
                        TextButton(onClick = { insert("[[${note.record.path.removeSuffix(".md")}|${note.record.title}]]"); linkPicker = false }) { Text(note.record.path) }
                    }
                }
            }
        }, confirmButton = { TextButton(enabled = search.isNotBlank(), onClick = { insert("[[$search]]"); linkPicker = false }) { Text("插入链接") } },
            dismissButton = { TextButton(onClick = { linkPicker = false }) { Text("取消") } })
    }
}

package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

internal data class CalendarDayPreview(val title: String, val done: Boolean = false, val isEvent: Boolean = false)

@Composable
internal fun CalendarDayCell(
    day: LocalDate, active: Boolean, today: Boolean, inMonth: Boolean,
    previews: List<CalendarDayPreview>, noted: Boolean, marked: Boolean,
    height: Dp, onClick: () -> Unit, modifier: Modifier = Modifier,
    noteText: String = "",
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val count = previews.size
    val entries = if (noteText.isBlank()) previews else previews + CalendarDayPreview(noteText)
    val dateHeight = with(density) { 20.sp.toDp() } + 14.dp
    val textLine = with(density) { 13.sp.toDp() }
    val space = (height - dateHeight - 5.dp).coerceAtLeast(0.dp)
    val lines = if (entries.isNotEmpty() && space >= (textLine * 2 + 3.dp) * entries.size) 2 else 1
    val itemHeight = textLine * lines + 3.dp
    val capacity = (space / itemHeight).toInt().coerceAtLeast(0)
    val shown = if (entries.size > capacity) (capacity - 1).coerceAtLeast(0) else entries.size
    val gridColor = colors.outlineVariant.copy(alpha = .45f)
    Column(modifier.height(height)
        .clip(RectangleShape)
        .background(if (active) colors.primary.copy(alpha = .045f) else Color.Transparent)
        .drawBehind {
            val stroke = .5.dp.toPx()
            drawLine(gridColor, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), stroke)
        }
        .then(if (active) Modifier.border(1.dp, colors.primary, RectangleShape) else Modifier)
        .semantics {
            contentDescription = day.toString()
            stateDescription = buildString {
                append("${count}项安排")
                if (previews.any { it.done }) append("，${previews.count { it.done }}项已完成")
                if (noted) append("，有手记")
            }
        }
        .selectable(active, enabled = day.year in 1..9999, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(dateHeight).padding(vertical = 4.dp)
            .widthIn(min = 28.dp)
            .background(if (today) colors.primaryContainer else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(day.dayOfMonth.toString(), fontSize = 14.sp, lineHeight = 18.sp,
                fontWeight = if (active || today) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    today || active -> colors.primary
                    inMonth -> colors.onSurface
                    else -> colors.onSurfaceVariant.copy(alpha = .45f)
                })
        }
        entries.take(shown).forEachIndexed { index, preview ->
            val isNote = index >= previews.size
            val tint = when {
                isNote -> colors.tertiary
                preview.isEvent -> colors.secondary
                else -> colors.primary
            }
            Text(preview.title.replace('\n', ' '),
                Modifier.fillMaxWidth().padding(horizontal = 1.dp, vertical = 1.dp)
                    .background(tint.copy(alpha = if (preview.done) .04f else .09f))
                    .testTag("calendar-preview-$day-$index"),
                fontSize = 10.sp, lineHeight = 13.sp, maxLines = lines,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (preview.done) TextDecoration.LineThrough else TextDecoration.None,
                color = colors.onSurface.copy(alpha = if (preview.done || !inMonth) .55f else .9f))
        }
        if (shown < entries.size) {
            Text("+${entries.size - shown}项", Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, color = colors.primary)
        } else if (entries.isEmpty() && (marked || noted)) {
            Text(if (noted) "手记" else "有安排", fontSize = 10.sp, lineHeight = 13.sp, color = colors.primary)
        }
    }
}

package win.iqwqi.xiangece.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.ui.theme.modernCourseColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoursePaletteChoices(classic: List<Long>, selected: Long, onSelect: (Long) -> Unit, onCustom: () -> Unit) {
    for ((label, palette) in listOf("清新" to modernCourseColors, "经典" to classic)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.fillMaxWidth()) {
            palette.forEachIndexed { index, value ->
                val interactions = remember { MutableInteractionSource() }
                Box(Modifier.size(48.dp)
                    .semantics { contentDescription = "$label 颜色 ${index + 1}" }
                    .selectable(selected == value, interactionSource = interactions, indication = null,
                        role = Role.RadioButton, onClick = { onSelect(value) }),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(value))
                        .indication(interactions, ripple())
                        .border(if (selected == value) 3.dp else 0.dp,
                            if (selected == value) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape))
                }
            }
        }
    }
    TextButton(onClick = onCustom) {
        Icon(Icons.Outlined.Palette, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("自定义 RGB")
    }
}

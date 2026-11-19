package win.iqwqi.xiangece.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.ui.theme.themeSeeds

/** No inner scrolling: every palette participates in the sheet's one scroll range. */
@Composable
internal fun ThemePaletteChoices(selectedId: String, custom: Boolean, onSelect: (String) -> Unit) {
    for (modern in listOf(true, false)) {
        Text(if (modern) "清新配色" else "经典配色", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            themeSeeds.filter { it.modern == modern }.forEach { seed ->
                val selected = custom && selectedId == seed.id
                val colors = MaterialTheme.colorScheme
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (selected) colors.primaryContainer else colors.surface)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(seed.id) })
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (color in listOf(seed.lightPrimary, seed.lightPrimaryContainer, seed.lightSecondary)) {
                            Box(Modifier.size(18.dp).background(color, CircleShape))
                        }
                    }
                    Text(seed.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    if (selected) Icon(Icons.Outlined.Check, "已选择", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

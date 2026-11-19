package win.iqwqi.xiangece.ui.companion

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import win.iqwqi.xiangece.data.settings.*
import win.iqwqi.xiangece.ui.components.AppFullScreenSheet
import win.iqwqi.xiangece.ui.theme.LocalCompanionDark
import win.iqwqi.xiangece.ui.theme.XiangeceTheme
import win.iqwqi.xiangece.ui.theme.getThemeSeed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeSheet(
    appearance: CompanionAppearance,
    themeSeedId: String,
    onApply: (CompanionAppearance) -> Unit,
    onDismiss: () -> Unit,
    motionAllowed: Boolean = true,
) {
    var draft by remember(appearance) { mutableStateOf(appearance) }
    val dark = LocalCompanionDark.current
    AppFullScreenSheet("角色衣橱", onDismiss) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).testTag("wardrobe-scroll")
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("同一个她，两种日常。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                XiangeceTheme(darkTheme = dark, themeSeedId = themeSeedId, companion = draft) {
                    CompanionHero("今天也好", "${draft.outfit.label}日常", "衣装预览", null, {},
                        appearance = draft.copy(display = CompanionDisplay.STANDARD, motionEnabled = draft.motionEnabled && motionAllowed))
                }
                Text("衣装", style = MaterialTheme.typography.titleSmall)
                ChoiceRow(CompanionOutfit.entries, draft.outfit, { it.label }) { draft = draft.copy(outfit = it) }
                PreferenceSwitch("背景随衣装切换", draft.sceneFollowsOutfit) { draft = draft.withSceneLink(it) }
                if (!draft.sceneFollowsOutfit) {
                    ChoiceRow(CompanionOutfit.entries, draft.scene, { "${it.label}窗景" }) { draft = draft.copy(scene = it) }
                }
                Text("配色", style = MaterialTheme.typography.titleSmall)
                ChoiceRow(CompanionPalette.entries, draft.palette,
                    { if (it == CompanionPalette.OUTFIT) "随衣装" else "${getThemeSeed(themeSeedId).name}主题" }) {
                    draft = draft.copy(palette = it)
                }
                Text("首页显示", style = MaterialTheme.typography.titleSmall)
                ChoiceRow(CompanionDisplay.entries, draft.display, { it.label }) { draft = draft.copy(display = it) }
                PreferenceSwitch("小表情反馈", draft.feedbackEnabled,
                    enabled = draft.display != CompanionDisplay.HIDDEN) { draft = draft.copy(feedbackEnabled = it) }
                PreferenceSwitch("轻动效", draft.motionEnabled) { draft = draft.copy(motionEnabled = it) }
                Text("深浅模式沿用外观设置。隐藏角色时，小表情也会隐藏。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = { onApply(draft); onDismiss() }, modifier = Modifier.weight(1.6f)) { Text("应用装扮") }
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) },
                label = { Text(label(value)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

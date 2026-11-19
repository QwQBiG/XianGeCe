package win.iqwqi.xiangece.ui.companion

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import win.iqwqi.xiangece.data.settings.CompanionAppearance
import win.iqwqi.xiangece.data.settings.CompanionDisplay
import win.iqwqi.xiangece.ui.theme.LocalCompanionAppearance
import win.iqwqi.xiangece.ui.theme.LocalCompanionDark

@Composable
fun CompanionHero(
    greeting: String,
    semester: String,
    status: String,
    progress: Float?,
    onWardrobe: () -> Unit,
    onGreet: () -> Unit = {},
    modifier: Modifier = Modifier,
    appearance: CompanionAppearance = LocalCompanionAppearance.current,
) {
    if (appearance.display == CompanionDisplay.HIDDEN) return
    val dark = LocalCompanionDark.current
    val surface = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(24.dp)
    if (appearance.display == CompanionDisplay.COMPACT) {
        Surface(modifier.fillMaxWidth(), shape = shape, color = surface, tonalElevation = 1.dp) {
            Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompanionPortrait(appearance.outfit, Modifier.size(48.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            role = Role.Button, onClickLabel = "打个招呼", onClick = onGreet), avatar = true)
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.titleSmall)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onWardrobe) { Text("换装") }
            }
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth().clip(shape).background(surface)) {
        val largeType = LocalDensity.current.fontScale > 1.2f
        val heroHeight = if (largeType || maxWidth < 320.dp) 280.dp else 244.dp
        val textWidth = (maxWidth * 0.52f).coerceAtMost(if (largeType) 190.dp else 172.dp)
        val portraitWidth = maxWidth - textWidth
        Crossfade(
            targetState = Triple(appearance.outfit, appearance.resolvedScene, dark),
            animationSpec = tween(if (appearance.motionEnabled) 220 else 0), label = "companion-outfit",
            modifier = Modifier.matchParentSize(),
        ) { (outfit, scene, night) ->
            Box(Modifier.fillMaxSize()) {
                Image(painterResource(companionSceneResource(scene, night)), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    0f to surface.copy(alpha = 0.97f), 0.42f to surface.copy(alpha = 0.89f),
                    0.72f to surface.copy(alpha = 0.08f), 1f to Color.Transparent,
                )))
                CompanionPortrait(outfit,
                    Modifier.align(Alignment.CenterEnd).width(portraitWidth).heightIn(max = 280.dp).fillMaxHeight()
                        .padding(top = 8.dp).clipToBounds().testTag("companion-portrait")
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            role = Role.Button, onClickLabel = "打个招呼", onClick = onGreet),
                    contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            }
        }
        Column(Modifier.width(textWidth).testTag("companion-copy")
            .heightIn(min = heroHeight).padding(start = 18.dp, end = 8.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${appearance.outfit.label} · 日常", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(greeting, fontSize = 27.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold)
            Text(semester, style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
                fontWeight = FontWeight.Medium)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            progress?.let {
                LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.width(100.dp).height(4.dp),
                    trackColor = MaterialTheme.colorScheme.primaryContainer)
            }
            TextButton(onClick = onWardrobe, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("换装")
            }
        }
    }
}

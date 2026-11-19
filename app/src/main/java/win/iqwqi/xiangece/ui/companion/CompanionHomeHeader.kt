package win.iqwqi.xiangece.ui.companion

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import win.iqwqi.xiangece.data.settings.*
import win.iqwqi.xiangece.ui.theme.LocalCompanionDark
import androidx.compose.ui.res.painterResource

/** The scene belongs to the page; only the compact greeting uses a small surface. */
@Composable
fun CompanionHomeHeader(
    appearance: CompanionAppearance, greeting: String, date: String,
    semester: String, status: String, progress: Float?,
    onWardrobe: () -> Unit, onGreet: () -> Unit,
    onSearch: () -> Unit, onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val interaction = rememberCompanionInteraction(appearance, active)
    val pose = if (active && appearance.showsFeedback) interaction.pose else CompanionPose.IDLE
    val respond = {
        if (active && appearance.showsFeedback) { interaction.tap(); onGreet() }
    }
    val colors = MaterialTheme.colorScheme
    val dark = LocalCompanionDark.current
    val largeType = LocalDensity.current.fontScale > 1.2f
    BoxWithConstraints(modifier.fillMaxWidth().testTag("home-header")) {
        val standard = appearance.display == CompanionDisplay.STANDARD
        val copyWidth = (maxWidth * 0.48f).coerceAtMost(190.dp)
        val portraitWidth = maxWidth - copyWidth
        val bodyHeight = if (largeType || maxWidth < 350.dp) 294.dp else 266.dp
        if (standard) {
            Crossfade(Triple(appearance.outfit, appearance.resolvedScene, dark),
                modifier = Modifier.matchParentSize(),
                animationSpec = tween(if (appearance.motionEnabled) 220 else 0), label = "home-outfit") { (outfit, scene, night) ->
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    Image(painterResource(companionSceneResource(scene, night)), null,
                        Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                        0f to colors.background.copy(alpha = if (night) 0.91f else 0.80f),
                        0.40f to colors.background.copy(alpha = 0.65f),
                        0.69f to Color.Transparent)))
                    CompanionPortrait(outfit,
                        Modifier.align(Alignment.BottomEnd).width(portraitWidth)
                            .height(bodyHeight).clipToBounds().testTag("home-portrait").semantics { stateDescription = pose.label }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            role = Role.Button, onClickLabel = "切换表情和动作", onClick = respond),
                        contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                        pose = pose, motionEnabled = appearance.motionEnabled)
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background))))
                }
            }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            HomeToolbar(appearance.display != CompanionDisplay.HIDDEN, onWardrobe, onSearch, onCreate)
            if (standard) {
                Column(Modifier.width(copyWidth).heightIn(min = bodyHeight).testTag("home-copy")
                    .padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(date, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Text(greeting, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
                    Text(semester, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    progress?.let {
                        LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) },
                            modifier = Modifier.width(88.dp).height(4.dp), trackColor = colors.primaryContainer)
                    }
                }
            } else if (appearance.display == CompanionDisplay.COMPACT) {
                Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp), color = colors.primaryContainer.copy(alpha = 0.38f)) {
                    Row(Modifier.heightIn(min = 88.dp).padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f).testTag("home-copy"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(greeting, style = MaterialTheme.typography.headlineMedium)
                            Text(status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Crossfade(appearance.outfit, animationSpec = tween(if (appearance.motionEnabled) 220 else 0),
                            label = "compact-outfit", modifier = Modifier.size(72.dp).clip(CircleShape)
                                .background(colors.surface.copy(alpha = 0.65f))
                                .border(2.dp, colors.primary, CircleShape)
                                .testTag("home-portrait").semantics { stateDescription = pose.label }
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            role = Role.Button, onClickLabel = "切换表情和动作", onClick = respond)
                                .padding(3.dp).clip(CircleShape)) { outfit ->
                            CompanionPortrait(outfit, Modifier.fillMaxSize().graphicsLayer {
                                scaleX = 1.2f; scaleY = 1.2f; transformOrigin = TransformOrigin(0.5f, 0f)
                            }, avatar = true, pose = pose, motionEnabled = appearance.motionEnabled)
                        }
                    }
                }
            } else {
                Text(date, Modifier.padding(start = 20.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HomeToolbar(showWardrobe: Boolean, onWardrobe: () -> Unit, onSearch: () -> Unit, onCreate: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("弦歌册", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Search, "搜索") }
        IconButton(onClick = onCreate, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Add, "新建事项") }
        if (showWardrobe) {
            FilledTonalButton(onClick = onWardrobe, contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))) {
                Text("换装", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

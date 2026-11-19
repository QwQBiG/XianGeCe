package win.iqwqi.xiangece.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SourceImagePreview(path: String, onDismiss: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("核对原图", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("复位") }
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
                Text("双指缩放与移动，原始图片保持不变", style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer { clip = true }, contentAlignment = Alignment.Center) {
                    bitmap?.let { image ->
                        Image(image, "识别来源原图", Modifier.fillMaxSize()
                            .pointerInput(path) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    offset = if (scale == 1f) Offset.Zero else offset + pan
                                }
                            }
                            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y })
                    } ?: Text("原图正在读取或已不可用，可返回继续核对识别文字")
                }
            }
        }
    }
}

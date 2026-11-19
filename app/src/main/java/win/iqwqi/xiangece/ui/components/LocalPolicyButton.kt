package win.iqwqi.xiangece.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** The policy matching this installed build remains readable without a website. */
@Composable
fun LocalPolicyButton(title: String, assetName: String) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    TextButton(onClick = { open = true }) { Text(title) }
    if (open) {
        val content = remember(assetName) {
            context.assets.open("policies/$assetName").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Text(content, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } },
        )
    }
}

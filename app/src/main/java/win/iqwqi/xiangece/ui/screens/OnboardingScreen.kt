package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import win.iqwqi.xiangece.ui.components.BrandHeader
import win.iqwqi.xiangece.ui.components.PaperCard

@Composable
fun OnboardingScreen(
    contentPadding: PaddingValues,
    onComplete: (String, String, String) -> Unit,
) {
    val defaultStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var name by remember { mutableStateOf("${LocalDate.now().year} 秋季学期") }
    var startDate by remember { mutableStateOf(defaultStart.toString()) }
    var weeks by remember { mutableStateOf("20") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        BrandHeader("弦歌册", "大学诸事，尽入一册。")
        PaperCard {
            Text("先定下这学期")
            Text("教学周是课表、通知识别和提醒的时间坐标。所有数据只保存在你的手机中。")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("学期名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it },
                label = { Text("第一周周一（yyyy-MM-dd）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = weeks,
                onValueChange = { weeks = it.filter(Char::isDigit) },
                label = { Text("教学周数") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        Button(
            onClick = { onComplete(name, startDate, weeks) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("开始使用")
            Icon(Icons.Outlined.ArrowForward, contentDescription = null)
        }
    }
}

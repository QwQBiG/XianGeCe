package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.*
import win.iqwqi.xiangece.data.local.CampusEventEntity
import win.iqwqi.xiangece.data.local.PeriodTemplateEntity
import win.iqwqi.xiangece.domain.grade.GradeCalculator
import win.iqwqi.xiangece.ui.AppUiState
import win.iqwqi.xiangece.ui.components.AppFormSheet
import win.iqwqi.xiangece.ui.components.AppTextField
import win.iqwqi.xiangece.ui.components.BrandHeader
import win.iqwqi.xiangece.ui.components.InkDivider
import win.iqwqi.xiangece.ui.components.PaperCard
import win.iqwqi.xiangece.ui.components.SectionTitle

private enum class ToolboxPanel {
    GRADE, PERIOD, EXAM, CALCULATOR, POMODORO
}

@Composable
fun ToolboxScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    onParseText: (String, Boolean) -> Unit,
    onPickImage: () -> Unit,
    onSavePeriod: (Int, String, String) -> Unit,
    onAddGrade: (String, String, String) -> Unit,
    onDeleteGrade: (Long) -> Unit,
    onSaveGradePreferences: (String, String) -> Unit,
    onOpenAiSettings: () -> Unit = {},
) {
    var noticeText by remember { mutableStateOf("") }
    var useAi by remember(state.settings.aiEnabled) { mutableStateOf(state.settings.aiEnabled) }
    var panel by remember { mutableStateOf<ToolboxPanel?>(null) }
    val context = LocalContext.current

    val tools = remember(state.settings.aiEnabled, state.settings.aiProvider) {
        listOf(
            ToolItem("成绩", "GPA / 加权", Icons.Outlined.TrendingUp) { panel = ToolboxPanel.GRADE },
            ToolItem("课程时间", "同步课表", Icons.Outlined.AccessTime) { panel = ToolboxPanel.PERIOD },
            ToolItem("考试倒计时", "天数 / 提醒", Icons.Outlined.EventAvailable) { panel = ToolboxPanel.EXAM },
            ToolItem("计算器", "科学 / 换算", Icons.Outlined.Calculate) { panel = ToolboxPanel.CALCULATOR },
            ToolItem("专注番茄", "沉浸计时", Icons.Outlined.Timer) { panel = ToolboxPanel.POMODORO },
            ToolItem("记账", "收支流水", Icons.Outlined.Savings) {
                context.startActivity(Intent(context, ExpenseActivity::class.java))
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { BrandHeader("百宝", "把通知、图片和小计算收进一个工具箱", icon = Icons.Outlined.AutoAwesomeMosaic) }
        item {
            PaperCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.TextSnippet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("通知 / 图片转日程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("文字可离线整理；图片识别使用你配置的视觉 AI，原图不会上传到弦歌册。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("AI 增强", fontWeight = FontWeight.Medium)
                        Text(
                            if (state.settings.aiEnabled) "文字限制 2400 字以内，减少 token 消耗。"
                            else "未启用 AI 时仍会生成本地草稿。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onOpenAiSettings) { Text("AI 设置") }
                        Switch(useAi, { useAi = it }, enabled = state.settings.aiEnabled)
                    }
                }
                OutlinedTextField(
                    value = noticeText,
                    onValueChange = { noticeText = it },
                    label = { Text("通知文字") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onParseText(noticeText, useAi) },
                        enabled = noticeText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text("文字转日程") }
                    OutlinedButton(onClick = onPickImage, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Image, contentDescription = null)
                        Text(" 图片识别")
                    }
                }
            }
        }
        item { SectionTitle("小工具", "统一入口，点开使用完整功能") }
        item {
            // 3 列网格：图标在上，文字在下，更紧凑专业
            val columns = 3
            tools.chunked(columns).forEach { rowTools ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowTools.forEach { tool ->
                        ToolTile(
                            modifier = Modifier.weight(1f),
                            title = tool.title,
                            subtitle = tool.subtitle,
                            icon = tool.icon,
                            onClick = tool.onClick,
                        )
                    }
                    // 不足 3 个时补占位，保持等宽对齐
                    repeat(columns - rowTools.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    when (panel) {
        ToolboxPanel.GRADE -> GradeToolSheet(
            state = state,
            onAddGrade = onAddGrade,
            onDeleteGrade = onDeleteGrade,
            onSaveGradePreferences = onSaveGradePreferences,
            onDismiss = { panel = null },
        )
        ToolboxPanel.PERIOD -> PeriodEditSheet(
            periods = state.periods,
            onSavePeriod = onSavePeriod,
            onDismiss = { panel = null },
        )
        ToolboxPanel.EXAM -> ExamCountdownSheet(
            events = state.events,
            reminderFirstHours = state.settings.taskReminderHoursFirst,
            reminderSecondHours = state.settings.taskReminderHoursSecond,
            onParseText = onParseText,
            useAi = useAi,
            onDismiss = { panel = null },
        )
        ToolboxPanel.CALCULATOR -> CalculatorSheet(onDismiss = { panel = null })
        ToolboxPanel.POMODORO -> PomodoroSetupSheet(
            onDismiss = { panel = null },
        )
        null -> Unit
    }
}

private data class ToolItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun ToolTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    PaperCard(
        modifier = modifier
            .height(134.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GradeToolSheet(
    state: AppUiState,
    onAddGrade: (String, String, String) -> Unit,
    onDeleteGrade: (Long) -> Unit,
    onSaveGradePreferences: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var scheme by remember(state.settings.gradeScheme) { mutableStateOf(state.settings.gradeScheme) }
    var customRules by remember(state.settings.customGradeRules) { mutableStateOf(state.settings.customGradeRules) }
    var addingGrade by remember { mutableStateOf(false) }
    val summary = if (scheme == "自定义") {
        GradeCalculator.calculateCustom(state.grades, customRules)
    } else {
        GradeCalculator.calculate(state.grades, scheme)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("成绩与加权平均分", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("成绩数据只在本地计算。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                PaperCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("4.0", "4.3", "5.0", "自定义").forEach { value ->
                            FilterChip(
                                selected = scheme == value,
                                onClick = {
                                    scheme = value
                                    onSaveGradePreferences(value, customRules)
                                },
                                label = { Text(value) },
                            )
                        }
                    }
                    if (scheme == "自定义") {
                        OutlinedTextField(
                            value = customRules,
                            onValueChange = { customRules = it },
                            label = { Text("最低分=绩点，以逗号分隔") },
                            supportingText = { Text("例如：90=4.0, 85=3.7, 60=1.0, 0=0") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            onClick = { onSaveGradePreferences(scheme, customRules) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("保存自定义规则") }
                    }
                    InkDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryNumber("总学分", summary.totalCredits)
                        SummaryNumber("加权分", summary.weightedAverage)
                        SummaryNumber("GPA", summary.gpa)
                    }
                    Button(onClick = { addingGrade = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("添加一门成绩")
                    }
                }
            }
            items(state.grades, key = { it.id }) { grade ->
                PaperCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(grade.courseName, fontWeight = FontWeight.Medium)
                            Text("${grade.credit} 学分 · ${grade.score} 分", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeleteGrade(grade.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除成绩")
                        }
                    }
                }
            }
        }
    }
    if (addingGrade) {
        AddGradeDialog(
            onDismiss = { addingGrade = false },
            onSave = { name, credit, score ->
                onAddGrade(name, credit, score)
                addingGrade = false
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PeriodEditSheet(
    periods: List<PeriodTemplateEntity>,
    onSavePeriod: (Int, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("课程时间", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("这里修改后会同步课程页左侧节次时间。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(periods.sortedBy { it.periodIndex }.take(24), key = { it.periodIndex }) { period ->
                PeriodEditorRow(period, onSavePeriod)
            }
        }
    }
}

@Composable
private fun PeriodEditorRow(period: PeriodTemplateEntity, onSavePeriod: (Int, String, String) -> Unit) {
    var start by remember(period.startMinutes) { mutableStateOf(formatToolMinutes(period.startMinutes)) }
    var end by remember(period.endMinutes) { mutableStateOf(formatToolMinutes(period.endMinutes)) }
    PaperCard {
        Text("第 ${period.periodIndex} 节", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(start, { start = it }, label = { Text("开始") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(end, { end = it }, label = { Text("结束") }, modifier = Modifier.weight(1f), singleLine = true)
            Button(onClick = { onSavePeriod(period.periodIndex, start, end) }) {
                Text("保存")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExamCountdownSheet(
    events: List<CampusEventEntity>,
    reminderFirstHours: Int,
    reminderSecondHours: Int,
    onParseText: (String, Boolean) -> Unit,
    useAi: Boolean,
    onDismiss: () -> Unit,
) {
    var course by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    val now = remember { System.currentTimeMillis() }
    val upcomingExams = remember(events, now) {
        events
            .filter { it.startsAtEpochMillis > now && isExamTitle(it.title) }
            .sortedBy { it.startsAtEpochMillis }
            .take(4)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("考试倒计时", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "填写后生成待确认考试事项；确认收件后会按提醒设置自动通知。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (upcomingExams.isNotEmpty()) {
                Text("即将到来的考试", fontWeight = FontWeight.SemiBold)
                upcomingExams.forEach { event ->
                    val dateTime = Instant.ofEpochMilli(event.startsAtEpochMillis)
                        .atZone(ZoneId.systemDefault())
                    val days = ChronoUnit.DAYS.between(LocalDate.now(), dateTime.toLocalDate())
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        Text(event.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (days == 0L) "今天 · ${dateTime.format(examDateTimeFormatter)}"
                            else "还有 $days 天 · ${dateTime.format(examDateTimeFormatter)}",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (event.location.isNotBlank()) {
                            Text("地点：${event.location}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            AppTextField(course, { course = it }, label = "考试/课程名称")
            AppTextField(date, { date = it }, label = "日期，例如 2026-12-20")
            AppTextField(time, { time = it }, label = "时间，例如 09:00")
            AppTextField(location, { location = it }, label = "地点")
            Text(
                "当前提醒：提前 $reminderFirstHours 小时、提前 $reminderSecondHours 小时",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    onParseText("考试：$course；日期：$date；时间：$time；地点：$location", useAi)
                    onDismiss()
                },
                enabled = course.isNotBlank() && date.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("生成考试草稿")
            }
        }
    }
}

private val examDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun isExamTitle(title: String): Boolean =
    listOf("考试", "期中", "期末", "补考", "测验", "考核").any(title::contains)

private enum class CalculatorMode(val label: String) {
    BASIC("普通"),
    SCIENTIFIC("科学"),
    CONVERTER("换算"),
}

private enum class ConverterKind(val label: String) {
    LENGTH("长度"),
    AREA("面积"),
    WEIGHT("重量"),
    TEMPERATURE("温度"),
    DATA("数据"),
}

private data class ConversionOption(val label: String, val key: String)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CalculatorSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(CalculatorMode.BASIC) }
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val history = remember {
        mutableStateListOf<CalculationRecord>().also { it.addAll(loadCalculatorHistory(context)) }
    }
    var converterKind by remember { mutableStateOf(ConverterKind.LENGTH) }
    var converterInput by remember { mutableStateOf("") }
    var converterOption by remember(converterKind) {
        mutableStateOf(conversionOptions(converterKind).first())
    }
    val scrollState = rememberScrollState()

    fun press(key: String) {
        when (key) {
            "AC" -> {
                expression = ""
                result = ""
            }
            "⌫" -> expression = expression.dropLast(1)
            "=" -> {
                val calculated = evaluateExpression(expression)
                result = calculated?.let(::formatCalculatorNumber) ?: "表达式有误"
                if (calculated != null && calculated.isFinite() && expression.isNotBlank()) {
                    history.removeAll { it.expression == expression }
                    history.add(0, CalculationRecord(expression, result))
                    while (history.size > 20) history.removeAt(history.lastIndex)
                    saveCalculatorHistory(context, history)
                }
            }
            "÷" -> expression += "/"
            "×" -> expression += "*"
            "−" -> expression += "-"
            "√" -> expression += "sqrt("
            "sin" -> expression += "sin("
            "cos" -> expression += "cos("
            "tan" -> expression += "tan("
            "log" -> expression += "log("
            "ln" -> expression += "ln("
            "x²" -> expression += "^2"
            "π" -> expression += "pi"
            "e" -> expression += "e"
            "±" -> expression = if (expression.startsWith("-")) expression.drop(1) else "-($expression)"
            else -> expression += key
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("计算器", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("普通计算、科学函数和常用单位换算均在本地完成。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalculatorMode.values().forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = { mode = item },
                        label = { Text(item.label) },
                    )
                }
            }
            if (mode == CalculatorMode.CONVERTER) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConverterKind.values().forEach { item ->
                        FilterChip(
                            selected = converterKind == item,
                            onClick = {
                                converterKind = item
                                converterOption = conversionOptions(item).first()
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    conversionOptions(converterKind).forEach { item ->
                        FilterChip(
                            selected = converterOption == item,
                            onClick = { converterOption = item },
                            label = { Text(item.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = converterInput,
                    onValueChange = { converterInput = it },
                    label = { Text("输入数值") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val converted = converterInput.toDoubleOrNull()?.let {
                    convertValue(it, converterOption.key)
                }
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Text("换算结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        converted?.let(::formatCalculatorNumber) ?: "输入数值后显示结果",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        expression.ifBlank { "0" },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        result,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (mode == CalculatorMode.SCIENTIFIC) {
                    calculatorKeyRows(
                        listOf(
                            listOf("sin", "cos", "tan", "√"),
                            listOf("log", "ln", "x²", "^"),
                            listOf("π", "e", "(", ")"),
                        ),
                        ::press,
                    )
                }
                calculatorKeyRows(
                    listOf(
                        listOf("AC", "⌫", "%", "÷"),
                        listOf("7", "8", "9", "×"),
                        listOf("4", "5", "6", "−"),
                        listOf("1", "2", "3", "+"),
                        listOf("±", "0", ".", "="),
                    ),
                    ::press,
                )
                if (history.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("历史记录", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            history.clear()
                            saveCalculatorHistory(context, history)
                        }) { Text("清空") }
                    }
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        history.take(8).forEach { item ->
                            TextButton(
                                onClick = {
                                    expression = item.expression
                                    result = item.result
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(item.expression, color = MaterialTheme.colorScheme.onSurface)
                                    Text("= ${item.result}", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CalculationRecord(val expression: String, val result: String)

private const val calculatorHistoryPreferences = "calculator_history"
private const val calculatorHistoryKey = "records"

private fun loadCalculatorHistory(context: Context): List<CalculationRecord> = runCatching {
    val raw = context.getSharedPreferences(calculatorHistoryPreferences, Context.MODE_PRIVATE)
        .getString(calculatorHistoryKey, "[]") ?: "[]"
    val json = JSONArray(raw)
    List(json.length()) { index ->
        val item = json.getJSONObject(index)
        CalculationRecord(item.getString("expression"), item.getString("result"))
    }
}.getOrDefault(emptyList())

private fun saveCalculatorHistory(context: Context, history: List<CalculationRecord>) {
    val json = JSONArray()
    history.take(20).forEach { item ->
        json.put(JSONObject().put("expression", item.expression).put("result", item.result))
    }
    context.getSharedPreferences(calculatorHistoryPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(calculatorHistoryKey, json.toString())
        .apply()
}

@Composable
private fun calculatorKeyRows(rows: List<List<String>>, onPress: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = { onPress(key) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text(key) }
                }
            }
        }
    }
}

private fun conversionOptions(kind: ConverterKind): List<ConversionOption> = when (kind) {
    ConverterKind.LENGTH -> listOf(
        ConversionOption("米 → 千米", "m-km"),
        ConversionOption("千米 → 米", "km-m"),
        ConversionOption("米 → 厘米", "m-cm"),
        ConversionOption("英寸 → 厘米", "in-cm"),
    )
    ConverterKind.AREA -> listOf(
        ConversionOption("平方米 → 平方米", "m2-m2"),
        ConversionOption("平方米 → 平方英尺", "m2-ft2"),
        ConversionOption("平方英尺 → 平方米", "ft2-m2"),
    )
    ConverterKind.WEIGHT -> listOf(
        ConversionOption("千克 → 克", "kg-g"),
        ConversionOption("克 → 千克", "g-kg"),
        ConversionOption("千克 → 斤", "kg-jin"),
        ConversionOption("斤 → 千克", "jin-kg"),
    )
    ConverterKind.TEMPERATURE -> listOf(
        ConversionOption("摄氏度 → 华氏度", "c-f"),
        ConversionOption("华氏度 → 摄氏度", "f-c"),
    )
    ConverterKind.DATA -> listOf(
        ConversionOption("MB → GB", "mb-gb"),
        ConversionOption("GB → MB", "gb-mb"),
        ConversionOption("KB → MB", "kb-mb"),
    )
}

private fun convertValue(value: Double, key: String): Double = when (key) {
    "m-km" -> value / 1000.0
    "km-m" -> value * 1000.0
    "m-cm" -> value * 100.0
    "in-cm" -> value * 2.54
    "m2-ft2" -> value * 10.7639104167
    "ft2-m2" -> value / 10.7639104167
    "kg-g" -> value * 1000.0
    "g-kg" -> value / 1000.0
    "kg-jin" -> value * 2.0
    "jin-kg" -> value / 2.0
    "c-f" -> value * 9.0 / 5.0 + 32.0
    "f-c" -> (value - 32.0) * 5.0 / 9.0
    "mb-gb" -> value / 1024.0
    "gb-mb" -> value * 1024.0
    "kb-mb" -> value / 1024.0
    else -> value
}

private fun formatCalculatorNumber(value: Double): String {
    if (!value.isFinite()) return "结果无效"
    if (abs(value) < 1e-10) return "0"
    if (value == value.toLong().toDouble() && abs(value) <= Long.MAX_VALUE) return value.toLong().toString()
    return String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
}

private fun evaluateExpression(expression: String): Double? = runCatching {
    ExpressionParser(expression).parse()
}.getOrNull()

private class ExpressionParser(private val source: String) {
    private var index = 0

    fun parse(): Double {
        val value = parseExpression()
        skipSpaces()
        require(index == source.length)
        return value
    }

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            skipSpaces()
            value = when {
                match('+') -> value + parseTerm()
                match('-') -> value - parseTerm()
                else -> return value
            }
        }
    }

    private fun parseTerm(): Double {
        var value = parsePower()
        while (true) {
            skipSpaces()
            value = when {
                match('*') -> value * parsePower()
                match('/') -> value / parsePower()
                match('%') -> value % parsePower()
                else -> return value
            }
        }
    }

    private fun parsePower(): Double {
        val left = parseUnary()
        skipSpaces()
        return if (match('^')) left.pow(parsePower()) else left
    }

    private fun parseUnary(): Double {
        skipSpaces()
        if (match('+')) return parseUnary()
        if (match('-')) return -parseUnary()
        var value = parsePrimary()
        while (true) {
            skipSpaces()
            // 末尾百分号表示百分数；后面紧跟数字时由 parseTerm 作为取余运算处理。
            if (peek('%') && isPercentSuffix()) {
                index++
                value /= 100.0
            } else {
                return value
            }
        }
    }

    private fun parsePrimary(): Double {
        skipSpaces()
        if (match('(')) {
            val value = parseExpression()
            require(match(')'))
            return value
        }
        if (index < source.length && (source[index].isDigit() || source[index] == '.')) {
            val start = index
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
            return source.substring(start, index).toDouble()
        }
        val start = index
        while (index < source.length && source[index].isLetter()) index++
        require(index > start)
        val name = source.substring(start, index).lowercase(Locale.US)
        if (name == "pi") return Math.PI
        if (name == "e") return Math.E
        require(match('('))
        val argument = parseExpression()
        require(match(')'))
        return when (name) {
            "sin" -> sin(Math.toRadians(argument))
            "cos" -> cos(Math.toRadians(argument))
            "tan" -> tan(Math.toRadians(argument))
            "sqrt" -> sqrt(argument)
            "log" -> log10(argument)
            "ln" -> ln(argument)
            "abs" -> abs(argument)
            else -> error("unknown function")
        }
    }

    private fun skipSpaces() {
        while (index < source.length && source[index].isWhitespace()) index++
    }

    private fun match(character: Char): Boolean {
        if (index < source.length && source[index] == character) {
            index++
            return true
        }
        return false
    }

    private fun peek(character: Char): Boolean = index < source.length && source[index] == character

    private fun isPercentSuffix(): Boolean {
        var cursor = index + 1
        while (cursor < source.length && source[cursor].isWhitespace()) cursor++
        return cursor >= source.length || source[cursor] in ")+-*/^%"
    }
}

@Composable
private fun SummaryNumber(label: String, value: Double) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("%.2f".format(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AddGradeDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var credit by remember { mutableStateOf("") }
    var score by remember { mutableStateOf("") }
    val canSave = name.isNotBlank() && credit.toDoubleOrNull() != null && score.toDoubleOrNull() != null
    AppFormSheet(
        title = "添加成绩",
        subtitle = "用于本学期 GPA 计算，可随时删除。",
        onConfirm = { onSave(name, credit, score) },
        onDismiss = onDismiss,
        confirmLabel = "加入计算",
        confirmEnabled = canSave,
    ) {
        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = "课程",
        )
        AppTextField(
            value = credit,
            onValueChange = { credit = it },
            label = "学分",
            keyboardType = KeyboardType.Decimal,
        )
        AppTextField(
            value = score,
            onValueChange = { score = it },
            label = "百分制成绩",
            keyboardType = KeyboardType.Decimal,
        )
    }
}

private fun formatToolMinutes(value: Int): String {
    if (value <= 0) return "--:--"
    return "%02d:%02d".format(value / 60, value % 60)
}

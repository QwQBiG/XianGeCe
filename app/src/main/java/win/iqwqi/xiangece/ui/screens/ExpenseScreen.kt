package win.iqwqi.xiangece.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import win.iqwqi.xiangece.data.local.ExpenseRecordEntity
import win.iqwqi.xiangece.ui.components.AppConfirmDialog
import win.iqwqi.xiangece.ui.components.AppFormSheet
import win.iqwqi.xiangece.ui.components.AppTextField
import win.iqwqi.xiangece.ui.components.EmptyStateCard
import win.iqwqi.xiangece.ui.components.PaperCard
import win.iqwqi.xiangece.ui.theme.XiangeceTheme
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 记账分类。支出和收入共用同一组，UI 上分别展示。统一使用 Material 矢量图标。 */
enum class ExpenseCategory(val key: String, val label: String, val icon: ImageVector) {
    FOOD("food", "餐饮", Icons.Outlined.Restaurant),
    TRANSPORT("transport", "交通", Icons.Outlined.DirectionsBus),
    SHOPPING("shopping", "购物", Icons.Outlined.ShoppingBag),
    STUDY("study", "学习", Icons.Outlined.MenuBook),
    FUN("fun", "娱乐", Icons.Outlined.SportsEsports),
    DAILY("daily", "日用", Icons.Outlined.ReceiptLong),
    MEDICAL("medical", "医疗", Icons.Outlined.MedicalServices),
    OTHER("other", "其他", Icons.Outlined.MoreHoriz),
    INCOME_SCHOLARSHIP("income_scholar", "奖学金", Icons.Outlined.WorkspacePremium),
    INCOME_PARTTIME("income_parttime", "兼职", Icons.Outlined.Work),
    INCOME_TRANSFER("income_transfer", "转账", Icons.Outlined.AccountBalanceWallet),
    INCOME_OTHER("income_other", "其他收入", Icons.Outlined.Payments);

    companion object {
        fun fromKey(key: String): ExpenseCategory = entries.firstOrNull { it.key == key } ?: OTHER
        val expenseEntries get() = listOf(FOOD, TRANSPORT, SHOPPING, STUDY, FUN, DAILY, MEDICAL, OTHER)
        val incomeEntries get() = listOf(INCOME_SCHOLARSHIP, INCOME_PARTTIME, INCOME_TRANSFER, INCOME_OTHER)
    }
}

@AndroidEntryPoint
class ExpenseActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: win.iqwqi.xiangece.data.settings.AppSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsStore.settings.collectAsState(
                initial = win.iqwqi.xiangece.data.settings.AppSettings(),
            )
            XiangeceTheme(
                darkTheme = settings.darkMode,
                themeSeedId = settings.themeSeed,
            ) {
                ExpenseApp(
                    onBack = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseApp(
    onBack: () -> Unit,
    viewModel: ExpenseViewModel = viewModel(),
) {
    val scope = rememberCoroutineScope()
    val monthState by viewModel.currentMonth.collectAsState()
    val records by viewModel.monthRecords.collectAsState()
    val summary by viewModel.monthSummary.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ExpenseRecordEntity?>(null) }

    // 初始加载本月
    LaunchedEffect(Unit) { viewModel.loadCurrentMonth() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "生活费记账",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // 上月/下月切换
                OutlinedButton(onClick = { viewModel.shiftMonth(-1) }) { Text("‹") }
                Text(
                    "${monthState.year}-${"%02d".format(monthState.monthValue)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                OutlinedButton(onClick = { viewModel.shiftMonth(1) }) { Text("›") }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "记一笔",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部本月汇总卡片
            item { MonthSummaryCard(summary) }

            // 按日分组的流水
            if (records.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "本月还没有记录",
                        message = "点击右下角「+」记一笔，掌握每月收支。",
                    )
                }
            } else {
                val grouped = records.groupBy { it.occurredAtEpochMillis.toLocalDate() }
                grouped.forEach { (date, dayRecords) ->
                    item {
                        Text(
                            "${date.monthValue}月${date.dayOfMonth}日 · ${date.dayOfWeekLabel()}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(dayRecords, key = { it.id }) { record ->
                        ExpenseRow(
                            record = record,
                            onDelete = { pendingDelete = record },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddExpenseSheet(
            onDismiss = { showAdd = false },
            onSave = { amount, type, category, note ->
                scope.launch {
                    viewModel.addRecord(amount, type, category, note)
                }
                showAdd = false
            },
        )
    }

    pendingDelete?.let { record ->
        AppConfirmDialog(
            title = "删除这笔记录？",
            message = "「${ExpenseCategory.fromKey(record.category).label} · ${(record.amountCents / 100.0).formatYuan()}」会被删除，无法恢复。",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                scope.launch { viewModel.deleteRecord(record.id) }
                pendingDelete = null
            },
            confirmLabel = "删除",
            isDanger = true,
        )
    }
}

@Composable
private fun MonthSummaryCard(summary: ExpenseSummary) {
    PaperCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryColumn(
                label = "本月支出",
                amountYuan = summary.totalExpenseYuan,
                icon = Icons.Outlined.TrendingDown,
                color = MaterialTheme.colorScheme.error,
            )
            SummaryColumn(
                label = "本月收入",
                amountYuan = summary.totalIncomeYuan,
                icon = Icons.Outlined.TrendingUp,
                color = MaterialTheme.colorScheme.primary,
            )
            SummaryColumn(
                label = "结余",
                amountYuan = summary.balanceYuan,
                icon = Icons.Outlined.TrendingUp,
                color = if (summary.balanceYuan >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SummaryColumn(label: String, amountYuan: Double, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "¥${amountYuan.formatYuan()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun ExpenseRow(record: ExpenseRecordEntity, onDelete: () -> Unit) {
    val category = ExpenseCategory.fromKey(record.category)
    val isIncome = record.type == 1
    PaperCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isIncome) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    category.icon,
                    contentDescription = null,
                    tint = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (record.note.isNotBlank()) {
                    Text(
                        record.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Text(
                "${if (isIncome) "+" else "−"}${(record.amountCents / 100.0).formatYuan()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseSheet(
    onDismiss: () -> Unit,
    onSave: (amountYuan: Double, type: Int, category: String, note: String) -> Unit,
) {
    var isIncome by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var note by remember { mutableStateOf("") }

    val categories = if (isIncome) ExpenseCategory.incomeEntries else ExpenseCategory.expenseEntries
    val amountYuan = amountText.toDoubleOrNull() ?: 0.0

    // 统一使用 AppFormSheet：标题、副标题、内容滚动、底部取消/保存按钮由组件提供
    AppFormSheet(
        title = "记一笔",
        subtitle = "记录每一笔收支，掌握月度流水",
        onConfirm = { onSave(amountYuan, if (isIncome) 1 else 0, category.key, note.trim()) },
        onDismiss = onDismiss,
        confirmLabel = "保存",
        confirmEnabled = amountYuan > 0,
    ) {
        // 收支切换（统一用 Material FilterChip）
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !isIncome,
                onClick = { isIncome = false; category = ExpenseCategory.FOOD },
                label = { Text("支出") },
            )
            FilterChip(
                selected = isIncome,
                onClick = { isIncome = true; category = ExpenseCategory.INCOME_SCHOLARSHIP },
                label = { Text("收入") },
            )
        }

        // 金额
        AppTextField(
            value = amountText,
            onValueChange = { input ->
                // 只允许数字和一个小数点，最多两位小数
                val cleaned = input.filter { it.isDigit() || it == '.' }
                val parts = cleaned.split(".")
                amountText = when {
                    parts.size == 1 -> parts[0]
                    parts.size == 2 -> parts[0] + "." + parts[1].take(2)
                    else -> parts[0]
                }
            },
            label = "金额（元）",
            keyboardType = KeyboardType.Decimal,
            supportingText = if (amountText.isNotBlank()) "¥${amountYuan.formatYuan()}" else null,
        )

        // 分类（单行横向滑动）
        Text("分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        CategoryRow(
            categories = categories,
            selected = category,
            onSelect = { category = it },
        )

        // 备注
        AppTextField(
            value = note,
            onValueChange = { note = it },
            label = "备注（可选）",
            singleLine = false,
            minLines = 1,
        )
    }
}

/**
 * 分类选择：单行横向滑动，图标在上、文字在下。替代原 4 列网格，更紧凑、可容纳更多分类。
 */
@Composable
private fun CategoryRow(
    categories: List<ExpenseCategory>,
    selected: ExpenseCategory,
    onSelect: (ExpenseCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { cat ->
            val isSelected = cat == selected
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    )
                    .clickable { onSelect(cat) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    cat.icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    cat.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

// ===== 工具扩展 =====

private fun Long.toLocalDate(): LocalDate =
    java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun LocalDate.dayOfWeekLabel(): String = when (dayOfWeek.value) {
    1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
    5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> ""
}

private fun Double.formatYuan(): String {
    return if (this == this.toLong().toDouble()) {
        "%.0f".format(this)
    } else {
        "%.2f".format(this)
    }
}

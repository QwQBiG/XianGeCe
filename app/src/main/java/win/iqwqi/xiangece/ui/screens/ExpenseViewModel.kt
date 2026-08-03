package win.iqwqi.xiangece.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import win.iqwqi.xiangece.data.local.ExpenseDao
import win.iqwqi.xiangece.data.local.ExpenseRecordEntity
import java.time.LocalDate
import java.time.ZoneId

data class MonthKey(val year: Int, val monthValue: Int)

data class ExpenseSummary(
    val totalExpenseCents: Long,
    val totalIncomeCents: Long,
) {
    val totalExpenseYuan: Double get() = totalExpenseCents / 100.0
    val totalIncomeYuan: Double get() = totalIncomeCents / 100.0
    val balanceYuan: Double get() = (totalIncomeCents - totalExpenseCents) / 100.0
}

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
) : ViewModel() {

    private val _currentMonth = MutableStateFlow(MonthKey(LocalDate.now().year, LocalDate.now().monthValue))
    val currentMonth: StateFlow<MonthKey> = _currentMonth.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val monthRecords: StateFlow<List<ExpenseRecordEntity>> = _currentMonth
        .flatMapLatest { month ->
            val (start, end) = monthRange(month)
            expenseDao.observeRange(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val monthSummary: StateFlow<ExpenseSummary> = monthRecords
        .map { records ->
            ExpenseSummary(
                totalExpenseCents = records.filter { it.type == 0 }.sumOf { it.amountCents },
                totalIncomeCents = records.filter { it.type == 1 }.sumOf { it.amountCents },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, ExpenseSummary(0, 0))

    fun loadCurrentMonth() {
        val now = LocalDate.now()
        _currentMonth.value = MonthKey(now.year, now.monthValue)
    }

    fun shiftMonth(delta: Int) {
        val current = _currentMonth.value
        val newDate = LocalDate.of(current.year, current.monthValue, 1).plusMonths(delta.toLong())
        _currentMonth.value = MonthKey(newDate.year, newDate.monthValue)
    }

    fun addRecord(amountYuan: Double, type: Int, category: String, note: String) {
        if (amountYuan <= 0) return
        val cents = (amountYuan * 100).toLong()
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            expenseDao.insert(
                ExpenseRecordEntity(
                    amountCents = cents,
                    type = type,
                    category = category,
                    note = note,
                    occurredAtEpochMillis = now,
                ),
            )
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch { expenseDao.deleteById(id) }
    }
}

/** 计算某月的时间范围 [startMillis, endMillis]。 */
private fun monthRange(month: MonthKey): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val firstDay = LocalDate.of(month.year, month.monthValue, 1)
    val lastDay = firstDay.plusMonths(1).minusDays(1)
    val startMillis = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis = lastDay.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
    return startMillis to endMillis
}

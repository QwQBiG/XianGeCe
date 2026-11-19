package win.iqwqi.xiangece.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable
internal fun CalendarMonthCard(
    selected: LocalDate, today: LocalDate, marked: Set<LocalDate>, noted: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    previews: Map<LocalDate, List<CalendarDayPreview>> = emptyMap(),
    modifier: Modifier = Modifier,
    notePreviews: Map<LocalDate, String> = emptyMap(),
    onOpenDay: (LocalDate) -> Unit = {},
    onBack: (() -> Unit)? = null,
    onOpenAgenda: (() -> Unit)? = null,
    onAddTask: (() -> Unit)? = null,
    agendaLabel: String = "当日清单",
) {
    var menuOpen by remember { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = calendarMonthIndex(YearMonth.from(selected)), pageCount = { 9999 * 12 })
    val scope = rememberCoroutineScope()
    val currentSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    LaunchedEffect(pager.settledPage) {
        val month = calendarMonthAt(pager.settledPage)
        if (YearMonth.from(currentSelected) != month) {
            currentSelect(month.atDay(currentSelected.dayOfMonth.coerceAtMost(month.lengthOfMonth())))
        }
    }
    fun select(date: LocalDate) {
        onSelect(date)
        scope.launch { pager.animateScrollToPage(calendarMonthIndex(YearMonth.from(date))) }
    }
    val month = calendarMonthAt(pager.currentPage)
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                } else IconButton(enabled = pager.currentPage > 0, onClick = {
                    scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上个月") }
                BoxWithConstraints(Modifier.weight(1f)) {
                    if (onBack != null) {
                        Column {
                            Text("日历日程", fontSize = 11.sp, lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${month.year}年 ${month.monthValue}月", Modifier.testTag("calendar-month"),
                                fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else if (maxWidth < 115.dp * LocalDensity.current.fontScale) {
                        Column(Modifier.testTag("calendar-month")) {
                            Text("${month.year}年", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${month.monthValue}月", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text("${month.year}年 ${month.monthValue}月", Modifier.testTag("calendar-month"),
                            fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = { select(today) }) { Text("今天", fontSize = 12.sp) }
                if (onBack != null) Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreHoriz, "日历菜单") }
                    DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                        onOpenAgenda?.let { open ->
                            DropdownMenuItem(text = { Text(agendaLabel) },
                                modifier = Modifier.testTag("calendar-open-agenda"),
                                onClick = { menuOpen = false; open() })
                        }
                        onAddTask?.let { add ->
                            DropdownMenuItem(text = { Text("新建待办") }, onClick = { menuOpen = false; add() })
                        }
                        DropdownMenuItem(text = { Text("上个月") }, enabled = pager.currentPage > 0, onClick = {
                            menuOpen = false
                            scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                        })
                        DropdownMenuItem(text = { Text("下个月") }, enabled = pager.currentPage < 9999 * 12 - 1, onClick = {
                            menuOpen = false
                            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        })
                    }
                } else IconButton(enabled = pager.currentPage < 9999 * 12 - 1, onClick = {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下个月") }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").forEach { label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            HorizontalDivider(thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f).testTag("calendar-months")) { page ->
                val displayed = calendarMonthAt(page)
                val weeks = remember(displayed) { calendarDays(displayed).chunked(7).filter { week ->
                    week.any { YearMonth.from(it) == displayed }
                } }
                BoxWithConstraints(Modifier.fillMaxSize().testTag("calendar-grid-$displayed")) {
                    val dayHeight = maxHeight / weeks.size
                    Column(Modifier.fillMaxSize()) {
                        weeks.forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { day ->
                                    val active = day == selected
                                    val inMonth = YearMonth.from(day) == displayed
                                    CalendarDayCell(day, active, day == today, inMonth, previews[day].orEmpty(),
                                        day in noted, day in marked, dayHeight,
                                        { select(day); onOpenDay(day) }, Modifier.weight(1f), notePreviews[day].orEmpty())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

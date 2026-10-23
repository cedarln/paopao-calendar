@file:OptIn(ExperimentalMaterial3Api::class)

package com.paopao.calendar.ui.calendar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.EventEntity
import com.paopao.calendar.data.local.entity.InventoryEntity
import com.paopao.calendar.data.local.entity.MemorialEntity
import com.paopao.calendar.data.settings.AppSettings
import com.paopao.calendar.domain.calendar.ChineseCalendar
import com.paopao.calendar.domain.model.CalendarViewMode
import com.paopao.calendar.domain.model.Recurrence
import com.paopao.calendar.domain.model.RecurrenceCodec
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.common.CommonFormatters
import com.paopao.calendar.ui.navigation.Routes
import com.paopao.calendar.ui.settings.SettingsViewModel
import com.paopao.calendar.ui.theme.quadrantColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** 可选月份范围：1901—2099，够覆盖两代人的农历查询，也让 pager 页数是个常量。 */
private val FirstMonth = YearMonth.of(1901, 1)
private const val TOTAL_MONTHS = 200 * 12

private fun monthIndexOf(month: YearMonth): Int =
    (month.year - FirstMonth.year) * 12 + (month.monthValue - 1)

private fun monthAtPage(page: Int): YearMonth = FirstMonth.plusMonths(page.toLong())

@Composable
fun CalendarScreen(
    container: AppContainer,
    initialEpochDay: Long?,
    onNavigate: (String) -> Unit,
) {
    val calendarViewModel = viewModel<CalendarViewModel>(
        factory = appViewModelFactory(container) { CalendarViewModel(it, initialEpochDay) },
    )
    val settingsViewModel = viewModel<SettingsViewModel>(
        factory = appViewModelFactory(container) { SettingsViewModel(it) },
    )
    val state by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val editingEvent by calendarViewModel.editingEventState.collectAsStateWithLifecycle()
    var showMonthSettings by rememberSaveable { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = monthIndexOf(YearMonth.from(state.focusedMonth)),
        pageCount = { TOTAL_MONTHS },
    )

    // 代码侧换月（今天 / 选中跨月）驱动 pager，pager 滑动反过来驱动 VM。
    // 两边都先判等，否则两个 LaunchedEffect 会互相触发。
    LaunchedEffect(state.focusedMonth) {
        val target = monthIndexOf(state.focusedMonth)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    LaunchedEffect(pagerState.settledPage) {
        val settled = monthAtPage(pagerState.settledPage)
        if (settled != state.focusedMonth) calendarViewModel.focusMonth(settled)
    }

    val engine = remember(settings.firstDayOfWeek) { container.calendarEngine(settings.firstDayOfWeek) }

    Column(modifier = Modifier.fillMaxSize()) {
        CalendarTopBar(
            state = state,
            settings = settings,
            onViewMode = settingsViewModel::setViewMode,
            onPrev = { calendarViewModel.shiftMonth(-1) },
            onNext = { calendarViewModel.shiftMonth(1) },
            onToday = calendarViewModel::goToday,
            onOpenSettings = { showMonthSettings = true },
        )

        when (settings.viewMode) {
            CalendarViewMode.MONTH -> {
                WeekdayHeader(firstDay = settings.firstDayOfWeek, modifier = Modifier.padding(top = 4.dp))
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val month = monthAtPage(page)
                    val grid = remember(month, engine, settings.contentFlags, state.today) {
                        engine.monthGrid(month.atDay(1), state.today, settings.contentFlags)
                    }
                    Column {
                        grid.weeks.forEach { week ->
                            MonthWeekRow(week = week, state = state, onSelect = calendarViewModel::selectDate)
                        }
                    }
                }
            }

            CalendarViewMode.WEEK -> {
                WeekdayHeader(firstDay = settings.firstDayOfWeek, modifier = Modifier.padding(top = 4.dp))
                val grid = remember(state.selectedDate, engine, settings.contentFlags, state.today) {
                    engine.monthGrid(state.selectedDate, state.today, settings.contentFlags)
                }
                CalendarViewModel.visibleWeeks(grid, state.selectedDate, weekOnly = true).forEach { week ->
                    MonthWeekRow(week = week, state = state, onSelect = calendarViewModel::selectDate)
                }
            }

            CalendarViewMode.DAY -> DayHeaderBanner(date = state.selectedDate)
        }

        DayAgenda(
            state = state,
            onAddEvent = { calendarViewModel.openEventEditor(null) },
            onOpenEvent = calendarViewModel::openEventEditor,
            onToggleItem = calendarViewModel::toggleItemDone,
            onAddItem = { onNavigate(Routes.inventoryEdit(epochDay = state.selectedDate.toEpochDay())) },
            modifier = Modifier.weight(1f),
        )
    }

    if (showMonthSettings) {
        MonthSettingsSheet(
            settings = settings,
            viewModel = settingsViewModel,
            onDismiss = { showMonthSettings = false },
        )
    }

    editingEvent?.let { event ->
        EventEditorSheet(
            event = event,
            categories = state.categories,
            onDismiss = calendarViewModel::dismissEventEditor,
            onSave = calendarViewModel::saveEvent,
            onDelete = calendarViewModel::deleteEvent,
        )
    }
}

@Composable
private fun DayHeaderBanner(date: LocalDate) {
    val info = remember(date) { ChineseCalendar.infoOf(date) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.displayMedium)
        Text(
            text = "${date.format(CommonFormatters.chineseWeekday)} · ${info.shortLunarLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CalendarTopBar(
    state: CalendarUiState,
    settings: AppSettings,
    onViewMode: (CalendarViewMode) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val lunarYear = remember(state.focusedMonth) { ChineseCalendar.infoOf(state.focusedMonth.atDay(15)) }
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${state.focusedMonth.year}年${state.focusedMonth.monthValue}月",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "农历${lunarYear.yearGanZhi}年 · ${lunarYear.zodiac}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToday) {
                Text(text = "今天", style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Tune, contentDescription = "月视图设置")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalendarViewMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.viewMode == mode,
                    onClick = { onViewMode(mode) },
                    label = { Text(mode.chineseName()) },
                )
            }
        }
    }
}

@Composable
private fun DayAgenda(
    state: CalendarUiState,
    onAddEvent: () -> Unit,
    onOpenEvent: (EventEntity) -> Unit,
    onToggleItem: (InventoryEntity) -> Unit,
    onAddItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val agenda = state.agenda
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(8.dp)) {
        item {
            AgendaHeader(date = state.selectedDate, isToday = state.selectedDate == state.today)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onAddEvent,
                    label = { Text("添加日程") },
                    leadingIcon = {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                AssistChip(
                    onClick = onAddItem,
                    label = { Text("添加事项") },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        if (agenda.isEmpty) {
            item {
                Text(
                    text = "这一天是空的。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(agenda.memorials, key = { "memorial-${it.id}" }) { memorial -> MemorialRow(memorial) }
        items(agenda.events, key = { "event-${it.id}-${it.startEpochDay}" }) { event ->
            EventRow(event = event, onClick = { onOpenEvent(event) })
        }
        items(agenda.items, key = { "item-${it.id}" }) { item ->
            InventoryRow(item = item, onToggle = { onToggleItem(item) })
        }
    }
}

@Composable
private fun AgendaHeader(date: LocalDate, isToday: Boolean) {
    val info = remember(date) { ChineseCalendar.infoOf(date) }
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(date.format(CommonFormatters.chineseMonthDay), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            text = date.format(CommonFormatters.chineseWeekday),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${if (info.isLeapMonth) "闰" else ""}${info.lunarMonthName}月${info.lunarDayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (isToday) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "今天",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun EventRow(event: EventEntity, onClick: () -> Unit) {
    val start = LocalDate.ofEpochDay(event.startEpochDay)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 34.dp)
                    .clip(CircleShape)
                    .background(event.colorArgb.toColorOrPrimary()),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title.ifBlank { "未命名日程" }, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = eventSubtitle(event, start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (RecurrenceCodec.decode(event.recurrenceText) != Recurrence.None) {
                Icon(
                    Icons.Filled.Repeat,
                    contentDescription = "重复",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun eventSubtitle(event: EventEntity, start: LocalDate): String {
    val range = if (event.endEpochDay != event.startEpochDay) {
        "${start.format(CommonFormatters.chineseMonthDay)} → " +
            LocalDate.ofEpochDay(event.endEpochDay).format(CommonFormatters.chineseMonthDay)
    } else {
        ""
    }
    val time = if (event.allDay || event.startMillis == null) {
        "全天"
    } else {
        val from = event.startMillis.toLocalTime()
        val until = event.endMillis?.toLocalTime()
        if (until != null) "${from.asText()} - ${until.asText()}" else from.asText()
    }
    return listOf(range, time).filter { it.isNotBlank() }.joinToString(" ")
}

private fun Long.toLocalTime(): LocalTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime()

private fun LocalTime.asText(): String = "%02d:%02d".format(hour, minute)

@Composable
private fun InventoryRow(item: InventoryEntity, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.done, onCheckedChange = { onToggle() })
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            item.dueMinuteOfDay?.let { minute ->
                Text(
                    text = "%02d:%02d".format(minute / 60, minute % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = quadrantColor(item.quadrantIndex),
                )
            }
        }
    }
}

@Composable
private fun MemorialRow(memorial: MemorialEntity) {
    val anchor = LocalDate.ofEpochDay(memorial.anchorEpochDay)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(memorial.colorArgb.toColorOrSecondary()),
        )
        Spacer(Modifier.width(8.dp))
        Text(memorial.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = if (memorial.isLunar) "农历每年" else if (memorial.repeatYearly) "每年" else "${anchor.year}年",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Int.toColorOrPrimary(): Color =
    if (this == 0) MaterialTheme.colorScheme.primary else Color(this)

@Composable
private fun Int.toColorOrSecondary(): Color =
    if (this == 0) MaterialTheme.colorScheme.secondary else Color(this)

private fun CalendarViewMode.chineseName(): String = when (this) {
    CalendarViewMode.MONTH -> "月"
    CalendarViewMode.WEEK -> "周"
    CalendarViewMode.DAY -> "日"
}

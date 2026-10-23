package com.paopao.calendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paopao.calendar.domain.model.DayCell
import com.paopao.calendar.domain.model.MonthClockStyle
import com.paopao.calendar.domain.model.MonthColorScheme
import com.paopao.calendar.ui.theme.FestivalBadge
import com.paopao.calendar.ui.theme.HolidayBadge
import com.paopao.calendar.ui.theme.LunarText
import com.paopao.calendar.ui.theme.OutOfMonthText
import com.paopao.calendar.ui.theme.SolarTermBadge
import java.time.DayOfWeek
import java.time.LocalDate

/** 月视图行高：6 行加起来要在一屏内留出议程区，所以格子控制在 56dp。 */
private val CellHeight = 56.dp

@Composable
fun WeekdayHeader(firstDay: DayOfWeek, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CalendarViewModel.weekdaysStarting(firstDay).forEach { day ->
            Text(
                text = day.shortChineseName(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
fun MonthWeekRow(
    week: List<DayCell>,
    state: CalendarUiState,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        week.forEach { cell ->
            DayCellView(
                cell = cell,
                state = state,
                selected = cell.date == state.selectedDate,
                onClick = { onSelect(cell.date) },
                modifier = Modifier
                    .weight(1f)
                    .height(CellHeight),
            )
        }
    }
}

@Composable
fun DayCellView(
    cell: DayCell,
    state: CalendarUiState,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = state.settings.colorScheme
    val base = dayTextColor(cell, scheme)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    scheme == MonthColorScheme.PASTEL && cell.isWeekend && cell.inCurrentMonth ->
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    else -> Color.Transparent
                },
            )
            .then(
                if (selected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (cell.isToday) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    text = cell.date.dayOfMonth.toString(),
                    fontSize = if (state.settings.clockStyle == MonthClockStyle.LARGE) 20.sp else 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = if (cell.isToday || cell.holidayLabel != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (cell.isToday) MaterialTheme.colorScheme.onPrimary else base,
                    textAlign = TextAlign.Center,
                )
            }
            subContent(cell, state, base)
        }
    }
}

@Composable
private fun subContent(cell: DayCell, state: CalendarUiState, base: Color) {
    when (state.settings.clockStyle) {
        MonthClockStyle.PLAIN -> SpacerRow()
        MonthClockStyle.DENSITY_DOT -> DensityRow(
            hasEvent = cell.date in state.eventDays,
            hasItem = cell.date in state.itemDays,
        )
        MonthClockStyle.LARGE -> SpacerRow()
        MonthClockStyle.LUNAR_SUB -> Text(
            text = cell.subLabel,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = subLabelColor(cell, state),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SpacerRow() {
    Box(modifier = Modifier.size(width = 24.dp, height = 12.dp))
}

@Composable
private fun DensityRow(hasEvent: Boolean, hasItem: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(12.dp),
    ) {
        if (hasEvent) Dot(color = MaterialTheme.colorScheme.primary)
        if (hasItem) Dot(color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun dayTextColor(cell: DayCell, scheme: MonthColorScheme): Color = when {
    !cell.inCurrentMonth -> OutOfMonthText
    scheme == MonthColorScheme.HIGH_CONTRAST -> Color(0xFF101418)
    scheme == MonthColorScheme.MONOCHROME -> LunarText
    cell.holidayLabel != null -> HolidayBadge
    cell.isWeekend -> FestivalBadge
    else -> Color.Unspecified // 交回给 MaterialTheme 的 onSurface
}

@Composable
private fun subLabelColor(cell: DayCell, state: CalendarUiState): Color {
    val scheme = state.settings.colorScheme
    return when {
        !cell.inCurrentMonth -> OutOfMonthText
        scheme == MonthColorScheme.MONOCHROME -> LunarText
        cell.holidayLabel != null -> HolidayBadge
        cell.festivalLabel != null -> FestivalBadge
        cell.solarTermLabel != null -> SolarTermBadge
        else -> LunarText
    }
}

private fun DayOfWeek.shortChineseName(): String = when (this) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

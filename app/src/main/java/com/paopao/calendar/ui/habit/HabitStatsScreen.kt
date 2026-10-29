package com.paopao.calendar.ui.habit

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.data.local.entity.HabitLogEntity
import com.paopao.calendar.domain.habit.HabitMath
import com.paopao.calendar.domain.habit.HabitStat
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.common.CommonFormatters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 单个习惯的总览。
 *
 * 三个自然周期的百分比是这个页面的主结构：用户问的是「本周/本月/本年打了几次、占多少」，
 * 所以直接按日历上的周月年切，不用滚动 7/30/365 天 —— 后者分母每天在换，
 * 月底回看时对不上任何一张日历，等于把同一个问题回答了两遍还都对不上。
 */
data class HabitStatsUiState(
    val habit: HabitEntity? = null,
    val today: LocalDate = LocalDate.now(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val stats: List<HabitStat> = emptyList(),
    val total: HabitStat? = null,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val loggedDays: Set<LocalDate> = emptySet(),
    val history: List<HabitLogEntity> = emptyList(),
)

class HabitStatsViewModel(
    private val container: AppContainer,
    private val habitId: Long,
) : ViewModel() {

    private val today = LocalDate.now()

    /**
     * 取的是这个习惯自己的整段历史，而不是固定的 365 天窗口。
     *
     * 「本年」这张卡的分母是按自然年切的，1 月打卡的记录必须在里面；
     * 反过来只查三年也够 —— 一天一行的表，三年也就一千行，没什么可省的。
     */
    private val logs = container.habitRepository.observeLogs(today.minusDays(1095), today)

    val state = combine(
        container.habitRepository.observeActive(),
        logs,
        container.settingsStore.settings,
    ) { habits, logRows, settings ->
        val habit = habits.firstOrNull { it.id == habitId } ?: return@combine HabitStatsUiState(today = today)
        val days = HabitMath.daysOf(logRows, habit.id)
        HabitStatsUiState(
            habit = habit,
            today = today,
            firstDayOfWeek = settings.firstDayOfWeek,
            stats = HabitMath.statsOf(habit, days, today, settings.firstDayOfWeek),
            total = HabitMath.totalOf(habit, days, today),
            streak = HabitMath.streakOf(days, today),
            bestStreak = HabitMath.bestStreakOf(days),
            loggedDays = days,
            history = logRows
                .filter { it.habitId == habit.id }
                .sortedByDescending { it.epochDay }
                .take(HISTORY_LIMIT),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitStatsUiState(today = today))

    fun setNote(date: LocalDate, note: String) {
        viewModelScope.launch { container.habitRepository.setNote(habitId, date, note) }
    }

    /** 历史里的圆点就是撤销入口：补错了一天要能收回来。 */
    fun undo(date: LocalDate) {
        viewModelScope.launch { container.habitRepository.toggle(habitId, date) }
    }

    private companion object {
        const val HISTORY_LIMIT = 60
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitStatsScreen(
    container: AppContainer,
    habitId: Long,
    onBack: () -> Unit,
) {
    val viewModel = viewModel<HabitStatsViewModel>(
        factory = appViewModelFactory(container) { HabitStatsViewModel(it, habitId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var noteDate by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.habit?.name ?: "习惯总览") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val habit = state.habit
            if (habit == null) {
                item {
                    Text(
                        text = "这个习惯已经不在了（可能刚被归档或删除）。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            } else {
                item { SpanLine(habit) }
                items(state.stats, key = { it.period }) { stat ->
                    PeriodCard(stat = stat, accent = Color(habit.colorArgb))
                }
                item {
                    TotalCard(
                        total = state.total,
                        streak = state.streak,
                        bestStreak = state.bestStreak,
                    )
                }
                item {
                    Heatmap(
                        days = state.loggedDays,
                        today = state.today,
                        firstDayOfWeek = state.firstDayOfWeek,
                        accent = Color(habit.colorArgb),
                    )
                }
                item { Text("最近打卡", style = MaterialTheme.typography.labelLarge) }
                if (state.history.isEmpty()) {
                    item {
                        Text(
                            text = "还没有打卡记录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.history, key = { it.epochDay }) { log ->
                    val date = LocalDate.ofEpochDay(log.epochDay)
                    HistoryRow(
                        date = date,
                        note = log.note,
                        onNote = { noteDate = date },
                        onUndo = { viewModel.undo(date) },
                    )
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    val date = noteDate
    val habit = state.habit
    val row = state.history.firstOrNull { LocalDate.ofEpochDay(it.epochDay) == date }
    if (date != null && habit != null) {
        HabitNoteSheet(
            habitName = habit.name,
            date = date,
            initialNote = row?.note.orEmpty(),
            onDismiss = { noteDate = null },
            onSave = { note ->
                viewModel.setNote(date, note)
                noteDate = null
            },
        )
    }
}

@Composable
private fun SpanLine(habit: HabitEntity) {
    val start = LocalDate.ofEpochDay(habit.startEpochDay)
    val end = habit.endEpochDay?.let { LocalDate.ofEpochDay(it) }
    val target = if (habit.targetPerWeek <= 0) 7 else habit.targetPerWeek
    Text(
        text = buildString {
            append(start.format(CommonFormatters.chineseDate))
            append(" 开始")
            if (end != null) {
                append(" · ")
                append(end.format(CommonFormatters.chineseDate))
                append(" 结束")
            }
            append(" · 每周 $target 次")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun PeriodCard(stat: HabitStat, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stat.period.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(stat.rate * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
            }
            LinearProgressIndicator(
                progress = { stat.rate },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = buildString {
                    append("打卡 ${stat.done} 次 · 应打卡 ${stat.expected} 次")
                    // 分母被起止区间或「只算已过去」截断时必须写清楚，
                    // 否则用户会把「本月 12 次」当成整月总数上限。
                    if (stat.eligibleDays < ChronoUnit.DAYS.between(stat.start, stat.end).toInt() + 1) {
                        append("（已过 ${stat.eligibleDays} 天）")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TotalCard(total: HabitStat?, streak: Int, bestStreak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatColumn("累计打卡", "${total?.done ?: 0} 次")
            StatColumn("应打卡", "${total?.expected ?: 0} 次")
            StatColumn("当前连续", "$streak 天")
            StatColumn("最长连续", "$bestStreak 天")
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 最近 5 周的打卡格子，成行成列才能看出「哪周塌了」。 */
@Composable
private fun Heatmap(days: Set<LocalDate>, today: LocalDate, firstDayOfWeek: DayOfWeek, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("近 5 周", style = MaterialTheme.typography.labelLarge)
            HabitMath.weekStarts(today, firstDayOfWeek, 5).forEach { weekStart ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(7) { offset ->
                        val day = weekStart.plusDays(offset.toLong())
                        val future = day.isAfter(today)
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        day in days -> accent
                                        future -> Color.Transparent
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(date: LocalDate, note: String, onNote: () -> Unit, onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNote)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = date.format(CommonFormatters.chineseMonthDay),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = if (note.isBlank()) "没写备注" else note,
            style = MaterialTheme.typography.bodySmall,
            color = if (note.isBlank()) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        TextButton(
            onClick = onUndo,
            contentPadding = PaddingValues(6.dp, 2.dp),
        ) { Text("撤销", style = MaterialTheme.typography.bodySmall) }
    }
}

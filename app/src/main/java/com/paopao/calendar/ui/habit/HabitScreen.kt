package com.paopao.calendar.ui.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.paopao.calendar.domain.habit.HabitPeriod
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.common.CommonFormatters
import com.paopao.calendar.ui.navigation.Routes
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 习惯卡片需要的派生值。
 *
 * 连续天数与完成率都在读路径算，不额外存列：打卡表只有 (habitId, epochDay, note) 三列，
 * 派生统计存成列就要在每次打卡时同步维护，而这里的数据量根本不需要。
 * 口径本身在 [HabitMath] —— 日历、插件、总览页报的是同一个数，靠的就是各自都不重算。
 */
data class HabitRow(
    val habit: HabitEntity,
    val weekStart: LocalDate,
    val loggedDays: Set<LocalDate>,
    val notes: Map<LocalDate, String>,
    val weekDone: Int,
    val weekTarget: Int,
    val streak: Int,
    val doneToday: Boolean,
    val canLogToday: Boolean,
    val upcoming: Boolean,
) {
    val weekProgress: Float
        get() = if (weekTarget <= 0) 0f else (weekDone.toFloat() / weekTarget).coerceAtMost(1f)

    fun noteOn(date: LocalDate): String = notes[date].orEmpty()

    /** 只能往回补，不能提前打未来的天：后者会凭空造出一个还没到的完成记录。 */
    fun canLogOn(date: LocalDate, today: LocalDate): Boolean =
        !date.isAfter(today) && HabitMath.isActiveOn(habit, date)
}

data class HabitUiState(
    val today: LocalDate = LocalDate.now(),
    val rows: List<HabitRow> = emptyList(),
    val finished: List<HabitRow> = emptyList(),
    val archived: List<HabitEntity> = emptyList(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
)

class HabitViewModel(private val container: AppContainer) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    /** 一次取满 365 天的打卡记录：单用户单设备的规模下，内存里算连续天数比写 SQL 递归便宜。 */
    private val logs = container.habitRepository.observeLogs(today.minusDays(364), today)

    val state = combine(
        container.habitRepository.observeActive(),
        container.habitRepository.observeArchived(),
        logs,
        container.settingsStore.settings,
    ) { active, archived, logRows, settings ->
        val weekStart = HabitMath.weekStartOf(today, settings.firstDayOfWeek)
        val all = active.map { rowOf(it, logRows, weekStart, settings.firstDayOfWeek) }
        HabitUiState(
            today = today,
            // 过期的单列一组：一个已结束的习惯混在待打卡里，只会让人以为自己的打卡失败了
            rows = all.filter { !HabitMath.isFinished(it.habit, today) },
            finished = all.filter { HabitMath.isFinished(it.habit, today) },
            archived = archived,
            firstDayOfWeek = settings.firstDayOfWeek,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitUiState())

    private fun rowOf(
        habit: HabitEntity,
        logRows: List<HabitLogEntity>,
        weekStart: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): HabitRow {
        val days = HabitMath.daysOf(logRows, habit.id)
        val notes = logRows.asSequence()
            .filter { it.habitId == habit.id && it.note.isNotBlank() }
            .associate { LocalDate.ofEpochDay(it.epochDay) to it.note }
        val week = HabitMath.statOf(habit, days, HabitPeriod.WEEK, today, firstDayOfWeek)
        return HabitRow(
            habit = habit,
            weekStart = weekStart,
            loggedDays = days,
            notes = notes,
            weekDone = week.done,
            weekTarget = if (habit.targetPerWeek <= 0) 7 else habit.targetPerWeek,
            streak = HabitMath.streakOf(days, today),
            doneToday = today in days,
            canLogToday = HabitMath.isActiveOn(habit, today),
            upcoming = HabitMath.isUpcoming(habit, today),
        )
    }

    /** 点某天即切换：再点一次撤销，备注随记录一起消失。 */
    fun toggleOn(habitId: Long, date: LocalDate) {
        viewModelScope.launch { container.habitRepository.toggle(habitId, date) }
    }

    fun setNote(habitId: Long, date: LocalDate, note: String) {
        viewModelScope.launch { container.habitRepository.setNote(habitId, date, note) }
    }

    fun archive(habitId: Long) {
        viewModelScope.launch { container.habitRepository.archive(habitId) }
    }

    fun delete(habit: HabitEntity) {
        viewModelScope.launch { container.habitRepository.delete(habit) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit,
) {
    val viewModel = viewModel<HabitViewModel>(
        factory = appViewModelFactory(container) { HabitViewModel(it) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 记住是哪一天：补卡之后想改备注，界面得知道用户点的是哪个点
    var noteTarget by remember { mutableStateOf<Pair<Long, LocalDate>?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate(Routes.habitEdit()) }) {
                Icon(Icons.Filled.Add, contentDescription = "新建习惯")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("习惯", style = MaterialTheme.typography.titleLarge)
            }
            if (state.rows.isEmpty() && state.finished.isEmpty()) {
                item {
                    Text(
                        text = "还没有习惯。打卡类工具的价值不在完成率，而在让你看见「这周真的有人在坚持」。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(state.rows, key = { it.habit.id }) { row ->
                HabitCard(
                    row = row,
                    today = state.today,
                    firstDayOfWeek = state.firstDayOfWeek,
                    onToggleToday = { viewModel.toggleOn(row.habit.id, state.today) },
                    onToggleDay = { date -> viewModel.toggleOn(row.habit.id, date) },
                    onNote = { date -> noteTarget = row.habit.id to date },
                    onArchive = { viewModel.archive(row.habit.id) },
                    onOpen = { onNavigate(Routes.habitEdit(row.habit.id)) },
                    onStats = { onNavigate(Routes.habitStats(row.habit.id)) },
                )
            }
            if (state.finished.isNotEmpty()) {
                item { Text("已结束", style = MaterialTheme.typography.labelLarge) }
                items(state.finished, key = { "finished-${it.habit.id}" }) { row ->
                    FinishedHabitRow(
                        row = row,
                        onStats = { onNavigate(Routes.habitStats(row.habit.id)) },
                        onArchive = { viewModel.archive(row.habit.id) },
                    )
                }
            }
            if (state.archived.isNotEmpty()) {
                item { Text("已归档", style = MaterialTheme.typography.labelLarge) }
                items(state.archived, key = { it.id }) { habit ->
                    ArchivedHabitRow(
                        habit = habit,
                        onDelete = { viewModel.delete(habit) },
                    )
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    val target = noteTarget
    val row = target?.let { (habitId, _) -> (state.rows + state.finished).firstOrNull { it.habit.id == habitId } }
    if (target != null && row != null) {
        HabitNoteSheet(
            habitName = row.habit.name,
            date = target.second,
            initialNote = row.noteOn(target.second),
            onDismiss = { noteTarget = null },
            onSave = { note ->
                viewModel.setNote(row.habit.id, target.second, note)
                noteTarget = null
            },
        )
    }
}

@Composable
private fun HabitCard(
    row: HabitRow,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    onToggleToday: () -> Unit,
    onToggleDay: (LocalDate) -> Unit,
    onNote: (LocalDate) -> Unit,
    onArchive: () -> Unit,
    onOpen: () -> Unit,
    onStats: () -> Unit,
) {
    val accent = Color(row.habit.colorArgb)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { row.weekProgress },
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 5.dp,
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(text = "${row.weekDone}", style = MaterialTheme.typography.labelMedium)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = row.habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeekDots(
                    row = row,
                    today = today,
                    firstDayOfWeek = firstDayOfWeek,
                    accent = accent,
                    onDayClick = { date ->
                        if (date in row.loggedDays) onNote(date) else onToggleDay(date)
                    },
                )
                if (row.doneToday) {
                    val note = row.noteOn(today)
                    TextButton(
                        onClick = { onNote(today) },
                        contentPadding = PaddingValues(0.dp, 2.dp),
                    ) {
                        Text(
                            text = if (note.isBlank()) "加备注" else note,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(if (row.doneToday) accent else Color.Transparent)
                    .border(
                        width = if (row.doneToday) 0.dp else 2.dp,
                        color = if (row.canLogToday) accent else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable(enabled = row.canLogToday, onClick = onToggleToday),
                contentAlignment = Alignment.Center,
            ) {
                if (row.doneToday) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "今天已完成，再点一次撤销",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onStats) { Text("总览") }
                TextButton(onClick = onArchive) { Text("归档") }
            }
        }
    }
}

private fun HabitRow.subtitle(): String {
    val start = LocalDate.ofEpochDay(habit.startEpochDay)
    val end = habit.endEpochDay?.let { LocalDate.ofEpochDay(it) }
    return when {
        upcoming -> "${start.format(CommonFormatters.chineseMonthDay)} 开始 · 每周 $weekTarget 次"
        end != null -> "至 ${end.format(CommonFormatters.chineseMonthDay)} · 连续 $streak 天"
        else -> "每周 $weekTarget 次 · 连续 $streak 天"
    }
}

/**
 * 本周七个点：既是「今天落在整周的哪个位置」的目视线索，也是补卡入口。
 *
 * 打过的点再点一次开备注而不是直接撤销 —— 手滑撤销会连备注一起删（记录本身就是那行打卡），
 * 而「点开一个已经打完的点」最常见的意图是补一句今天做了什么。
 */
@Composable
private fun WeekDots(
    row: HabitRow,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    accent: Color,
    onDayClick: (LocalDate) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        repeat(7) { offset ->
            val day = HabitMath.weekStartOf(today, firstDayOfWeek).plusDays(offset.toLong())
            val logged = day in row.loggedDays
            val clickable = row.canLogOn(day, today) || logged
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            logged -> accent
                            day == today -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .then(
                        if (clickable) Modifier.clickable { onDayClick(day) } else Modifier,
                    ),
            )
        }
    }
}

@Composable
private fun FinishedHabitRow(row: HabitRow, onStats: () -> Unit, onArchive: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(row.habit.colorArgb)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = row.habit.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val end = row.habit.endEpochDay?.let { LocalDate.ofEpochDay(it) }
            Text(
                text = "已于 ${end?.format(CommonFormatters.chineseMonthDay) ?: "?"} 结束 · 共 ${row.loggedDays.size} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onStats) { Text("总览") }
        TextButton(onClick = onArchive) { Text("归档") }
    }
}

@Composable
private fun ArchivedHabitRow(habit: HabitEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = habit.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDelete) { Text("删除") }
    }
}

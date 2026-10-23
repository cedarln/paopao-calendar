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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.data.local.entity.HabitLogEntity
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.navigation.Routes
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewModelScope

/**
 * 习惯卡片需要的派生值。
 *
 * 连续天数与周完成率都在读路径上算，不额外存列：打卡表只有 (habitId, epochDay) 两列，
 * 任何派生统计存成列都要在每次打卡时同步维护，而这里的数据量根本不需要。
 */
data class HabitRow(
    val habit: HabitEntity,
    val loggedDays: Set<Long>,
    val weekDone: Int,
    val weekTarget: Int,
    val streak: Int,
    val doneToday: Boolean,
) {
    val weekProgress: Float
        get() = if (weekTarget <= 0) 0f else (weekDone.toFloat() / weekTarget).coerceAtMost(1f)
}

data class HabitUiState(
    val today: LocalDate = LocalDate.now(),
    val weekMonday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
    val rows: List<HabitRow> = emptyList(),
    val archived: List<HabitEntity> = emptyList(),
)

class HabitViewModel(private val container: AppContainer) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val weekMonday: LocalDate = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    /** 一次取满 365 天的打卡记录：单用户单设备的规模下，内存里算连续天数比写 SQL 递归便宜。 */
    private val logs = container.habitRepository.observeLogs(today.minusDays(364), today)

    val state = combine(
        container.habitRepository.observeActive(),
        container.habitRepository.observeArchived(),
        logs,
    ) { active, archived, logRows ->
        val byHabit = logRows.groupBy(HabitLogEntity::habitId)
            .mapValues { (_, rows) -> rows.map { it.epochDay }.toSet() }
        HabitUiState(
            today = today,
            weekMonday = weekMonday,
            rows = active.map { habit -> rowOf(habit, byHabit[habit.id].orEmpty()) },
            archived = archived,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitUiState())

    private fun rowOf(habit: HabitEntity, days: Set<Long>): HabitRow {
        val weekEnd = weekMonday.plusDays(6)
        val weekDone = (weekMonday.toEpochDay()..weekEnd.toEpochDay()).count { it in days }
        val target = if (habit.targetPerWeek <= 0) 7 else habit.targetPerWeek
        return HabitRow(
            habit = habit,
            loggedDays = days,
            weekDone = weekDone,
            weekTarget = target,
            streak = streakOf(days, today),
            doneToday = today.toEpochDay() in days,
        )
    }

    /** 今天没打卡不算断：从昨天往回数才是用户心里的「连续」。 */
    private fun streakOf(days: Set<Long>, today: LocalDate): Int {
        if (days.isEmpty()) return 0
        var cursor = if (today.toEpochDay() in days) today else today.minusDays(1)
        if (cursor.toEpochDay() !in days) return 0
        var count = 0
        while (cursor.toEpochDay() in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun toggleToday(habitId: Long) {
        viewModelScope.launch { container.habitRepository.toggle(habitId, today) }
    }

    fun toggleOn(habitId: Long, date: LocalDate) {
        viewModelScope.launch { container.habitRepository.toggle(habitId, date) }
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
            if (state.rows.isEmpty()) {
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
                    weekMonday = state.weekMonday,
                    today = state.today,
                    onToggle = { viewModel.toggleToday(row.habit.id) },
                    onArchive = { viewModel.archive(row.habit.id) },
                    onOpen = { onNavigate(Routes.habitEdit(row.habit.id)) },
                )
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
}

@Composable
private fun HabitCard(
    row: HabitRow,
    weekMonday: LocalDate,
    today: LocalDate,
    onToggle: () -> Unit,
    onArchive: () -> Unit,
    onOpen: () -> Unit,
) {
    val accent = Color(row.habit.colorArgb)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
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
                Text(
                    text = "${row.weekDone}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(row.habit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "每周 ${row.weekTarget} 次 · 连续 ${row.streak} 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeekDots(
                    weekMonday = weekMonday,
                    today = today,
                    loggedDays = row.loggedDays,
                    accent = accent,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(if (row.doneToday) accent else Color.Transparent)
                    .border(
                        width = if (row.doneToday) 0.dp else 2.dp,
                        color = accent,
                        shape = CircleShape,
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (row.doneToday) {
                    Text("✓", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
            TextButtonSmall(text = "归档", onClick = onArchive)
        }
    }
}

/** 本周七个小点，让「今天落在整周的哪个位置」不用读数字就能看出来。 */
@Composable
private fun WeekDots(
    weekMonday: LocalDate,
    today: LocalDate,
    loggedDays: Set<Long>,
    accent: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        repeat(7) { offset ->
            val day = weekMonday.plusDays(offset.toLong())
            val logged = day.toEpochDay() in loggedDays
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            logged -> accent
                            day == today -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

@Composable
private fun TextButtonSmall(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(text) }
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
        TextButtonSmall(text = "删除", onClick = onDelete)
    }
}

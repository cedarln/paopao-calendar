package com.paopao.calendar.ui.training

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.PlanActionEntity
import com.paopao.calendar.data.local.entity.WorkoutLogEntity
import com.paopao.calendar.data.local.entity.WorkoutPlanEntity
import com.paopao.calendar.domain.model.DayOfWeekMask
import com.paopao.calendar.domain.workout.TrainingMath
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.navigation.Routes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 训练页：计划列表 + 「这周练得怎么样」。
 *
 * 统计全部在读路径上从打卡表算出来，不另存计数列 —— 与习惯页同一个理由：
 * 打卡表一天一条，任何派生数字存成列都要在每次打卡时同步维护，而这台设备上的数据量根本不需要。
 */
data class TrainingPlanRow(
    val plan: WorkoutPlanEntity,
    val actions: List<PlanActionEntity>,
    val checkIns: Int,
    val doneToday: Boolean,
)

data class TrainingUiState(
    val today: LocalDate = LocalDate.now(),
    val rows: List<TrainingPlanRow> = emptyList(),
    val archived: List<WorkoutPlanEntity> = emptyList(),
    /** 近 35 天每天的分钟数，热力图用；键是日期，缺当天表示没练。 */
    val minutesByDay: Map<LocalDate, Int> = emptyMap(),
    val streak: Int = 0,
    val weekDays: Int = 0,
    val weekMinutes: Int = 0,
)

class TrainingViewModel(private val container: AppContainer) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val weekMonday: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 一次取满一年：连续天数、热力图、周统计都在这份内存结果上算，比写四条 SQL 便宜。 */
    private val logs = container.workoutRepository.observeLogs(today.minusDays(364), today)

    val state = combine(
        container.workoutRepository.observePlans(),
        container.workoutRepository.observeArchivedPlans(),
        logs,
    ) { plans, archived, logRows ->
        val minutesByDay = logRows.groupBy { LocalDate.ofEpochDay(it.epochDay) }
            .mapValues { (_, rows) -> rows.sumOf { it.durationSec } / 60 }
        val loggedDays = minutesByDay.keys
        val todayPlanIds = logRows.filter { it.epochDay == today.toEpochDay() }.mapNotNull { it.planId }.toSet()
        TrainingUiState(
            today = today,
            rows = plans.map { withActions ->
                TrainingPlanRow(
                    plan = withActions.plan,
                    actions = withActions.actions,
                    checkIns = logRows.count { it.planId == withActions.plan.id },
                    doneToday = withActions.plan.id in todayPlanIds,
                )
            },
            archived = archived,
            minutesByDay = minutesByDay,
            streak = TrainingMath.streak(loggedDays, today),
            weekDays = (0..6).count { weekMonday.plusDays(it.toLong()) in loggedDays },
            weekMinutes = (0..6).sumOf { minutesByDay[weekMonday.plusDays(it.toLong())] ?: 0 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingUiState(today = today))

    /** 一键补卡：时长取计划的估计值，真要认真计时应该走计时页。 */
    fun checkInToday(plan: WorkoutPlanEntity) {
        viewModelScope.launch {
            container.workoutRepository.checkIn(
                plan = plan,
                date = today,
                durationSec = TrainingMath.quickCheckInSec(plan.estimatedMinutes),
            )
        }
    }

    fun archive(planId: Long) {
        viewModelScope.launch { container.workoutRepository.archivePlan(planId) }
    }

    fun delete(plan: WorkoutPlanEntity) {
        viewModelScope.launch { container.workoutRepository.deletePlan(plan) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit,
) {
    val viewModel = viewModel<TrainingViewModel>(
        factory = appViewModelFactory(container) { TrainingViewModel(it) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate(Routes.trainingEdit()) }) {
                Icon(Icons.Filled.Add, contentDescription = "新建训练计划")
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
            item { Text("训练", style = MaterialTheme.typography.titleLarge) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onNavigate(Routes.EXERCISE_LIBRARY) },
                        label = { Text("动作库") },
                    )
                    AssistChip(
                        onClick = { onNavigate(Routes.workoutRun()) },
                        label = { Text("自由训练计时") },
                    )
                }
            }
            item {
                TrainingSummary(state)
            }
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        text = "还没有训练计划。可以先去动作库翻翻常用动作，或直接新建一个空计划再往里加。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(state.rows, key = { it.plan.id }) { row ->
                TrainingPlanCard(
                    row = row,
                    today = state.today,
                    onRun = { onNavigate(Routes.workoutRun(row.plan.id)) },
                    onEdit = { onNavigate(Routes.trainingEdit(row.plan.id)) },
                    onCheckIn = { viewModel.checkInToday(row.plan) },
                    onArchive = { viewModel.archive(row.plan.id) },
                )
            }
            if (state.archived.isNotEmpty()) {
                item { Text("已归档", style = MaterialTheme.typography.labelLarge) }
                items(state.archived, key = { it.id }) { plan ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = plan.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.delete(plan) }) { Text("删除") }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun TrainingSummary(state: TrainingUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("连续 ${state.streak} 天", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "本周 ${state.weekDays} 天 · ${state.weekMinutes} 分钟 · " +
                        "${TrainingMath.kilocalories(state.weekMinutes)} 千卡",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HeatMap(
                minutesByDay = state.minutesByDay,
                weekMonday = weekMondayOf(state.today),
                today = state.today,
            )
        }
    }
}

private fun weekMondayOf(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * 近五周热力图：一行一周天，一列一周。
 *
 * 沿用小程序 report 页的 7×5 规格。格子只分四档而不是按分钟线性着色：
 * 线性色阶在「20 分钟 vs 25 分钟」上根本看不出差别，只会让图看起来像噪声。
 */
@Composable
private fun HeatMap(minutesByDay: Map<LocalDate, Int>, weekMonday: LocalDate, today: LocalDate) {
    val accent = MaterialTheme.colorScheme.tertiary
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // 周日在这套布局里排最后，和日历默认的周一开头一致
        (0..6).forEach { dayOffset ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until HEATMAP_WEEKS).forEach { weekOffset ->
                    val date = weekMonday
                        .minusDays((HEATMAP_WEEKS - 1 - weekOffset).toLong())
                        .plusDays(dayOffset.toLong())
                    // 本周还剩几天没到：画成空格而不是「没练」，否则热力图右列永远是一排浅色格子
                    val minutes = if (date.isAfter(today)) null else minutesByDay[date] ?: 0
                    Box(
                        modifier = Modifier
                            .size(HEAT_CELL)
                            .clip(RoundedCornerShape(2.dp))
                            .background(heatColor(minutes, accent)),
                    )
                }
            }
        }
    }
}

private fun heatColor(minutes: Int?, accent: Color): Color = when {
    minutes == null -> Color.Transparent
    minutes <= 0 -> accent.copy(alpha = 0.12f)
    minutes < 15 -> accent.copy(alpha = 0.4f)
    minutes < 30 -> accent.copy(alpha = 0.65f)
    minutes < 45 -> accent.copy(alpha = 0.85f)
    else -> accent
}

private const val HEATMAP_WEEKS = 5
private val HEAT_CELL = 10.dp

@Composable
private fun TrainingPlanCard(
    row: TrainingPlanRow,
    today: LocalDate,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onCheckIn: () -> Unit,
    onArchive: () -> Unit,
) {
    val plan = row.plan
    val accent = Color(plan.colorArgb)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = plan.name.ifBlank { "未命名计划" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 圆点即「今天练过了」，与习惯页同一套手感；已打卡就不该再按第二下
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(if (row.doneToday) accent else Color.Transparent)
                        .border(
                            width = if (row.doneToday) 0.dp else 2.dp,
                            color = accent,
                            shape = CircleShape,
                        )
                        .then(if (row.doneToday) Modifier else Modifier.clickable(onClick = onCheckIn)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (row.doneToday) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "今日已打卡",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Text(
                text = "${plan.bodyPart} · ${weekMaskLabel(plan.weekMask)} · " +
                    "约 ${plan.estimatedMinutes} 分钟 · ${row.actions.size} 个动作",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (row.checkIns > 0) {
                Text(
                    text = "累计打卡 ${row.checkIns} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRun) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (TrainingMath.isScheduledOn(plan.weekMask, today)) "开始" else "还是今天练")
                }
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onArchive) { Text("归档") }
            }
        }
    }
}

/** 周计划掩码的人话版本。全周/工作日/周末三种常见组合直接说结论，其余按天列出来。 */
internal fun weekMaskLabel(mask: Int): String {
    val days = DayOfWeekMask(mask)
    return when (mask) {
        DayOfWeekMask.ALL.bits -> "每天"
        DayOfWeekMask.WORKDAYS.bits -> "工作日"
        DayOfWeekMask.WEEKEND.bits -> "周末"
        0 -> "未安排"
        else -> (0..6)
            .map { DayOfWeek.of(it + 1) }
            .filter { days.contains(it) }
            .joinToString(" ") { it.shortChineseName() }
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

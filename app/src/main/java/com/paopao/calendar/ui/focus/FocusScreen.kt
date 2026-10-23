@file:OptIn(ExperimentalMaterial3Api::class)

package com.paopao.calendar.ui.focus

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.FocusSessionEntity
import com.paopao.calendar.data.settings.AppSettings
import com.paopao.calendar.ui.appViewModelFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.combine
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FocusPhase { IDLE, WORK, REST, PAUSED }

data class FocusUiState(
    val phase: FocusPhase = FocusPhase.IDLE,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val workMinutes: Int = 25,
    val restMinutes: Int = 5,
    val sessions: List<FocusSessionEntity> = emptyList(),
    val todayMinutes: Int = 0,
    val weekMinutes: Int = 0,
) {
    val progress: Float get() = if (totalSeconds == 0) 0f else 1f - remainingSeconds.toFloat() / totalSeconds
    val clockText: String
        get() = "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)
    val isRunning: Boolean get() = phase == FocusPhase.WORK || phase == FocusPhase.REST
}

/**
 * 番茄钟。
 *
 * 计时用 `delay(1000)` 递减而不是记开始时间戳做减法：初版不打算做进程被杀后的恢复，
 * 那种情况下「按墙上时钟续算」会把用户离开的一小时也算进专注里，反而更糟。
 * 真实需要后台可靠计时时应换成 AlarmManager + 前台服务，而不是在这里加时间戳运算。
 */
class FocusViewModel(private val container: AppContainer) : ViewModel() {

    private val tick = MutableStateFlow(TimerState())
    private val _finished = MutableStateFlow<String?>(null)
    val finishedHint: StateFlow<String?> = _finished

    private var job: Job? = null

    val settingsState: StateFlow<AppSettings> = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val state: StateFlow<FocusUiState> = kotlinx.coroutines.flow.combine(
        tick,
        settingsState,
        container.focusRepository.observeRecent(),
        container.focusRepository.observeTotalMinutes(LocalDate.now(), LocalDate.now()),
        container.focusRepository.observeTotalMinutes(LocalDate.now().minusDays(6), LocalDate.now()),
    ) { timer, prefs, sessions, todayMinutes, weekMinutes ->
        FocusUiState(
            phase = timer.phase,
            remainingSeconds = timer.remaining,
            totalSeconds = timer.total,
            workMinutes = prefs.focusWorkMinutes,
            restMinutes = prefs.focusRestMinutes,
            sessions = sessions,
            todayMinutes = todayMinutes,
            weekMinutes = weekMinutes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun start() {
        if (job?.isActive == true) return
        val current = tick.value
        val phase = if (current.phase == FocusPhase.PAUSED) current.resumePhase else FocusPhase.WORK
        val total = if (current.total == 0) minutesOf(phase) else current.total
        val remaining = if (current.remaining == 0) total else current.remaining
        begin(phase, remaining, total)
    }

    private fun begin(phase: FocusPhase, remaining: Int, total: Int) {
        val startedAt = if (phase == FocusPhase.WORK) System.currentTimeMillis() else tick.value.startedAtMillis
        tick.value = TimerState(phase = phase, remaining = remaining, total = total, startedAtMillis = startedAt)
        job?.cancel()
        job = viewModelScope.launch {
            var left = remaining
            while (left > 0) {
                delay(1_000)
                left -= 1
                tick.update { it.copy(remaining = left) }
            }
            complete(phase, total)
        }
    }

    private suspend fun complete(phase: FocusPhase, plannedSeconds: Int) {
        if (phase == FocusPhase.WORK) {
            container.focusRepository.record(
                FocusSessionEntity(
                    startedAtMillis = tick.value.startedAtMillis,
                    plannedMinutes = (plannedSeconds / 60).coerceAtLeast(1),
                    actualMinutes = (plannedSeconds / 60).coerceAtLeast(1),
                    mode = FocusSessionEntity.MODE_WORK,
                    completed = true,
                ),
            )
            val rest = minutesOf(FocusPhase.REST)
            _finished.value = "一个番茄完成了，休息 $rest 分钟"
            begin(FocusPhase.REST, rest * 60, rest * 60)
        } else {
            _finished.value = "休息结束，回到专注"
            tick.value = TimerState(phase = FocusPhase.IDLE, remaining = 0, total = 0)
        }
    }

    fun pause() {
        val current = tick.value
        if (!current.phase.isRunningPhase()) return
        job?.cancel()
        tick.value = current.copy(phase = FocusPhase.PAUSED, resumePhase = current.phase)
    }

    /** 提前停止：仍然记一条「未完成」的会话，计划达成率才有意义。 */
    fun stop() {
        val current = tick.value
        job?.cancel()
        if (current.phase == FocusPhase.WORK && current.startedAtMillis > 0) {
            val spent = (current.total - current.remaining).coerceAtLeast(0) / 60
            viewModelScope.launch {
                container.focusRepository.record(
                    FocusSessionEntity(
                        startedAtMillis = current.startedAtMillis,
                        plannedMinutes = (current.total / 60).coerceAtLeast(1),
                        actualMinutes = spent,
                        completed = false,
                    ),
                )
            }
        }
        tick.value = TimerState(phase = FocusPhase.IDLE, remaining = 0, total = 0)
    }

    fun reset() {
        job?.cancel()
        tick.value = TimerState()
    }

    fun setMinutes(work: Int, rest: Int) {
        viewModelScope.launch { container.settingsStore.setFocusMinutes(work, rest) }
    }

    fun consumeHint() {
        _finished.value = null
    }

    private fun minutesOf(phase: FocusPhase): Int =
        settingsState.value.let { prefs ->
            if (phase == FocusPhase.REST) prefs.focusRestMinutes else prefs.focusWorkMinutes
        }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private data class TimerState(
        val phase: FocusPhase = FocusPhase.IDLE,
        val remaining: Int = 0,
        val total: Int = 0,
        val startedAtMillis: Long = 0L,
        val resumePhase: FocusPhase = FocusPhase.WORK,
    )
}

private fun FocusPhase.isRunningPhase(): Boolean = this == FocusPhase.WORK || this == FocusPhase.REST

@Composable
fun FocusScreen(container: AppContainer) {
    val viewModel = viewModel<FocusViewModel>(
        factory = appViewModelFactory(container) { FocusViewModel(it) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hint by viewModel.finishedHint.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("专注", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.size(220.dp),
                strokeWidth = 10.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.clockText, style = MaterialTheme.typography.displayMedium)
                Text(
                    text = when (state.phase) {
                        FocusPhase.WORK -> "专注中"
                        FocusPhase.REST -> "休息中"
                        FocusPhase.PAUSED -> "已暂停"
                        FocusPhase.IDLE -> "准备开始"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        hint?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(onClick = viewModel::consumeHint),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 12.dp)) {
            IconButton(onClick = { if (state.isRunning) viewModel.pause() else viewModel.start() }) {
                Icon(
                    imageVector = if (state.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isRunning) "暂停" else "开始",
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = viewModel::stop) {
                Icon(Icons.Filled.Stop, contentDescription = "结束")
            }
            IconButton(onClick = viewModel::reset) {
                Icon(Icons.Filled.Refresh, contentDescription = "复位")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf(15, 25, 45).forEach { minutes ->
                FilterChip(
                    selected = state.workMinutes == minutes,
                    onClick = { viewModel.setMinutes(minutes, state.restMinutes) },
                    label = { Text("$minutes 分钟") },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat(label = "今日专注", value = "${state.todayMinutes} 分钟")
            Stat(label = "近 7 天", value = "${state.weekMinutes} 分钟")
            Stat(label = "今日番茄", value = "${state.sessions.countOnToday()} 个")
        }

        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.sessions.take(12), key = { it.id }) { session ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = session.startedAtMillis.toClockText(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${session.actualMinutes}/${session.plannedMinutes} 分钟 · " +
                            if (session.completed) "完成" else "中断",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (session.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun List<FocusSessionEntity>.countOnToday(): Int {
    val today = LocalDate.now()
    return count {
        java.time.Instant.ofEpochMilli(it.startedAtMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate() == today
    }
}

private fun Long.toClockText(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

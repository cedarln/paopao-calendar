@file:OptIn(ExperimentalMaterial3Api::class)

package com.paopao.calendar.ui.training

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.WorkoutLogEntity
import com.paopao.calendar.data.local.entity.WorkoutPlanEntity
import com.paopao.calendar.domain.workout.TimerStep
import com.paopao.calendar.domain.workout.TrainingMath
import com.paopao.calendar.domain.workout.TrainingSequencer
import com.paopao.calendar.ui.appViewModelFactory
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RunPhase { IDLE, WORK, REST, PAUSED, FINISHED }

data class WorkoutRunUiState(
    val loading: Boolean = true,
    val planName: String = WorkoutLogEntity.FALLBACK_NAME,
    val steps: List<TimerStep> = emptyList(),
    val index: Int = 0,
    val phase: RunPhase = RunPhase.IDLE,
    val resumePhase: RunPhase = RunPhase.WORK,
    val remaining: Int = 0,
    val total: Int = 0,
    val elapsedSec: Int = 0,
    val freeMode: Boolean = false,
    val cueOn: Boolean = true,
    val hint: String? = null,
) {
    val current: TimerStep? get() = steps.getOrNull(index)
    val next: TimerStep? get() = steps.getOrNull(index + 1)
    val isRunning: Boolean get() = phase == RunPhase.WORK || phase == RunPhase.REST

    /** 自由训练正着数，按节拍训练倒着数。同一个字段位，两种读法。 */
    val clockText: String
        get() = if (freeMode) {
            "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
        } else {
            "%02d:%02d".format(remaining / 60, remaining % 60)
        }

    val progress: Float
        get() = when {
            freeMode -> 0f
            total <= 0 -> 0f
            else -> 1f - remaining.toFloat() / total
        }

    val phaseLabel: String
        get() = when (phase) {
            RunPhase.IDLE -> if (freeMode) "随时开始" else "准备开始"
            RunPhase.WORK -> "在做"
            RunPhase.REST -> "休息"
            RunPhase.PAUSED -> "已暂停"
            RunPhase.FINISHED -> "已结束"
        }
}

/**
 * 按节拍推进的训练计时器。
 *
 * 与番茄钟同样的取舍：用 `delay(1000)` 递减而不是记时间戳做减法。进程被杀或用户切走
 * 很久再回来时，宁可这段计时作废，也不把离开的一小时算成训练 —— 一个虚高的训练时长
 * 会同时污染打卡、连续天数和热量估算，比少记一次严重得多。
 *
 * 也因此「离开本页」等于放弃这次计时：只有按「结束」或跑完最后一组才会落一条日志。
 */
class WorkoutTimerViewModel(
    private val container: AppContainer,
    private val planId: Long?,
) : ViewModel() {

    private val state = MutableStateFlow(WorkoutRunUiState())
    val uiState: StateFlow<WorkoutRunUiState> = state.asStateFlow()

    private var plan: WorkoutPlanEntity? = null
    private var job: Job? = null
    private var startedAtMillis: Long? = null
    private var saved = false
    private val cue = WorkoutCue(container.appContext)

    init {
        viewModelScope.launch {
            val loaded = planId?.let { container.workoutRepository.findPlan(it) }
            plan = loaded?.plan
            val steps = loaded?.let { TrainingSequencer.steps(it.actions) }.orEmpty()
            val first = steps.firstOrNull()
            state.update {
                it.copy(
                    loading = false,
                    planName = loaded?.plan?.name?.ifBlank { "未命名计划" } ?: WorkoutLogEntity.FALLBACK_NAME,
                    steps = steps,
                    // 计划没配动作就退回正着数的自由计时，而不是给一个「0 段」的死页面
                    freeMode = steps.isEmpty(),
                    remaining = first?.seconds ?: 0,
                    total = first?.seconds ?: 0,
                )
            }
        }
    }

    fun setCue(enabled: Boolean) {
        state.update { it.copy(cueOn = enabled) }
    }

    fun start() {
        val current = state.value
        if (current.isRunning || current.phase == RunPhase.FINISHED || current.loading) return
        if (startedAtMillis == null) startedAtMillis = System.currentTimeMillis()
        saved = false
        val phase = when {
            current.phase == RunPhase.PAUSED -> current.resumePhase
            current.freeMode -> RunPhase.WORK
            else -> if (current.current?.isRest == true) RunPhase.REST else RunPhase.WORK
        }
        state.update { it.copy(phase = phase) }
        announce(current.current)
        job?.cancel()
        job = viewModelScope.launch { tick() }
    }

    private suspend fun tick() {
        while (true) {
            delay(1_000)
            val snapshot = state.value
            if (!snapshot.isRunning) return
            val elapsed = snapshot.elapsedSec + 1
            if (snapshot.freeMode) {
                state.value = snapshot.copy(elapsedSec = elapsed)
                continue
            }
            val left = snapshot.remaining - 1
            if (left > 0) {
                state.value = snapshot.copy(remaining = left, elapsedSec = elapsed)
                continue
            }
            val advanced = snapshot.advanceStep(elapsed)
            if (advanced == null) {
                state.value = snapshot.copy(elapsedSec = elapsed, phase = RunPhase.FINISHED)
                announce(null)
                finish()
                return
            }
            state.value = advanced
            announce(advanced.current)
        }
    }

    /** 走完一段就推进到下一个人；没有下一个人返回 null，由调用方收尾。 */
    private fun WorkoutRunUiState.advanceStep(elapsed: Int): WorkoutRunUiState? {
        val nextIndex = index + 1
        val step = steps.getOrNull(nextIndex) ?: return null
        return copy(
            index = nextIndex,
            remaining = step.seconds,
            total = step.seconds,
            phase = if (step.isRest) RunPhase.REST else RunPhase.WORK,
            elapsedSec = elapsed,
        )
    }

    fun pause() {
        val current = state.value
        if (!current.isRunning) return
        job?.cancel()
        state.value = current.copy(phase = RunPhase.PAUSED, resumePhase = current.phase)
    }

    /** 手动跳过当前段：休息太长或这组提前做完了，用户不该等秒针。 */
    fun skip() {
        val current = state.value
        if (current.freeMode || current.phase == RunPhase.FINISHED) return
        val advanced = current.advanceStep(current.elapsedSec)
        if (advanced == null) {
            state.value = current.copy(phase = RunPhase.FINISHED)
            finish()
        } else {
            state.value = advanced
            announce(advanced.current)
        }
    }

    /** 提前结束：只要已经计到一分钟就落一条日志，比「白跑一趟」符合用户预期。 */
    fun stop() {
        val current = state.value
        job?.cancel()
        state.value = current.copy(phase = RunPhase.FINISHED)
        finish()
    }

    private fun finish() {
        if (saved) return
        val current = state.value
        if (current.elapsedSec < MIN_LOGGED_SEC) {
            state.update { it.copy(hint = "不足一分钟，没有记进打卡") }
            return
        }
        saved = true
        val minutes = TrainingMath.estimatedMinutes(current.elapsedSec)
        viewModelScope.launch {
            container.workoutRepository.checkIn(
                plan = plan,
                date = LocalDate.now(),
                durationSec = current.elapsedSec,
                startedAtMillis = startedAtMillis,
            )
            state.update {
                it.copy(hint = "已记录「${current.planName}」$minutes 分钟 · 约 ${TrainingMath.kilocalories(minutes)} 千卡")
            }
        }
    }

    private fun announce(step: TimerStep?) {
        if (!state.value.cueOn) return
        val text = when {
            step == null -> "训练完成"
            step.isRest -> step.detail
            else -> "${step.label}，${step.detail}"
        }
        cue.speak(text)
    }

    override fun onCleared() {
        job?.cancel()
        cue.release()
        super.onCleared()
    }

    companion object {
        /** 短于这个数的计时不写日志：一组没做完就退出，记下来只会把当天时长稀释成噪声。 */
        const val MIN_LOGGED_SEC = 60
    }
}

@Composable
fun WorkoutTimerScreen(
    container: AppContainer,
    planId: Long?,
    onBack: () -> Unit,
) {
    val viewModel = viewModel<WorkoutTimerViewModel>(
        factory = appViewModelFactory(container) { WorkoutTimerViewModel(it, planId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.tertiary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.planName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(top = 12.dp)) {
                CircularProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.size(200.dp),
                    strokeWidth = 10.dp,
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.clockText, style = MaterialTheme.typography.displayMedium)
                    Text(
                        text = state.phaseLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!state.freeMode) {
                        Text(
                            text = "${state.index + 1}/${state.steps.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.current?.let { step ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (step.isRest) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        } else {
                            accent.copy(alpha = 0.14f)
                        },
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(step.label, style = MaterialTheme.typography.titleMedium)
                        Text(step.detail, style = MaterialTheme.typography.bodyMedium)
                        state.next?.let { upcoming ->
                            Text(
                                text = "接下来：${upcoming.label} · ${upcoming.detail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            state.hint?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (state.isRunning) viewModel.pause() else viewModel.start() }) {
                    Icon(
                        imageVector = if (state.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isRunning) "暂停" else "开始",
                        modifier = Modifier.size(40.dp),
                    )
                }
                if (!state.freeMode) {
                    IconButton(onClick = viewModel::skip) {
                        Icon(Icons.Filled.FastForward, contentDescription = "跳过这一段")
                    }
                }
                IconButton(onClick = { viewModel.stop(); onBack() }) {
                    Icon(Icons.Filled.Stop, contentDescription = "结束并记录")
                }
            }

            if (!state.freeMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "语音提示",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.cueOn, onCheckedChange = viewModel::setCue)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.steps.forEachIndexed { index, step ->
                        StepRow(
                            step = step,
                            current = index == state.index,
                            done = index < state.index,
                        )
                    }
                    Text(
                        text = "中途离开本页会丢掉这次计时；要留记录请按结束。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "没有安排动作，正着数时长。练完按结束即可打卡。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StepRow(step: TimerStep, current: Boolean, done: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        current -> MaterialTheme.colorScheme.tertiary
                        done -> MaterialTheme.colorScheme.outline
                        step.isRest -> MaterialTheme.colorScheme.surfaceVariant
                        else -> Color.Transparent
                    },
                ),
        )
        Text(
            text = step.label,
            style = MaterialTheme.typography.bodySmall,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        Text(
            text = step.detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

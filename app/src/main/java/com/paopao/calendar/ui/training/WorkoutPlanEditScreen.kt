@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.PlanActionEntity
import com.paopao.calendar.data.local.entity.WorkoutPlanEntity
import com.paopao.calendar.domain.model.DayOfWeekMask
import com.paopao.calendar.domain.workout.BodyPart
import com.paopao.calendar.domain.workout.Exercise
import com.paopao.calendar.domain.workout.ExerciseCatalog
import com.paopao.calendar.domain.workout.ParsedPlan
import com.paopao.calendar.domain.workout.TrainingMath
import com.paopao.calendar.domain.workout.TrainingTextParser
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.common.ColorSwatchField
import com.paopao.calendar.ui.common.OptionField
import com.paopao.calendar.ui.common.SectionTitle
import com.paopao.calendar.ui.navigation.Routes
import com.paopao.calendar.ui.theme.SwatchPalette
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 计划部位候选：动作库七个枚举之外，小程序原文案里还常见「臀」和「全身」。 */
private val BodyPartChoices = listOf("肩", "背", "胸", "臂", "腿", "臀", "核心", "有氧", "全身")

data class PlanEditDraft(
    val plan: WorkoutPlanEntity = WorkoutPlanEntity(
        name = "",
        bodyPart = "全身",
        colorArgb = SwatchPalette.first(),
    ),
    val actions: List<PlanActionEntity> = emptyList(),
    val importText: String = "",
    val parsed: ParsedPlan? = null,
    val loadFailed: Boolean = false,
)

/**
 * 计划编辑。
 *
 * 动作按「整版草稿」编辑，保存时一次替换：与 [com.paopao.calendar.data.local.dao.WorkoutDao.replaceActions]
 * 的粒度对齐，UI 不必区分「新增/修改/删除」三种落库路径。
 */
class WorkoutPlanEditViewModel(
    private val container: AppContainer,
    private val planId: Long?,
) : ViewModel() {

    private val draft = MutableStateFlow(PlanEditDraft())
    val uiState: StateFlow<PlanEditDraft> = draft.asStateFlow()

    init {
        if (planId != null) {
            viewModelScope.launch {
                val loaded = container.workoutRepository.findPlan(planId)
                if (loaded == null) {
                    draft.update { it.copy(loadFailed = true) }
                } else {
                    draft.update { it.copy(plan = loaded.plan, actions = loaded.actions) }
                }
            }
        }
    }

    fun updatePlan(transform: (WorkoutPlanEntity) -> WorkoutPlanEntity) {
        draft.update { it.copy(plan = transform(it.plan)) }
    }

    fun toggleDay(day: DayOfWeek) {
        val mask = DayOfWeekMask(draft.value.plan.weekMask)
        val next = if (mask.contains(day)) mask.minus(day) else mask.plus(day)
        // 全被取消等于这个计划永远不会出现，直接回到「每天」更符合预期
        updatePlan { it.copy(weekMask = if (next.isSet) next.bits else DayOfWeekMask.ALL.bits) }
    }

    fun addAction(action: PlanActionEntity) {
        draft.update { it.copy(actions = it.actions + action.copy(planId = it.plan.id, sortOrder = it.actions.size)) }
    }

    fun addFromCatalog(exercise: Exercise) {
        // 有氧与核心默认按秒计（平板支撑、开合跳），其余默认按次计（3 组 × 12 次）
        val timed = exercise.part == BodyPart.CARDIO || exercise.part == BodyPart.CORE
        addAction(
            PlanActionEntity(
                planId = 0,
                sortOrder = 0,
                name = exercise.name,
                sets = 3,
                reps = if (timed) 0 else DEFAULT_REPS,
                durationSec = if (timed) DEFAULT_TIMED_SEC else TrainingMath.estimateDurationSec(DEFAULT_REPS, 3),
                restSec = 30,
                exerciseKey = exercise.key,
            ),
        )
    }

    fun addCustomAction() {
        addAction(PlanActionEntity(planId = 0, sortOrder = 0, name = ""))
    }

    fun updateAction(index: Int, transform: (PlanActionEntity) -> PlanActionEntity) {
        draft.update { state ->
            val current = state.actions.getOrNull(index) ?: return@update state
            state.copy(actions = state.actions.mapIndexed { i, action -> if (i == index) transform(current) else action })
        }
    }

    fun moveAction(index: Int, delta: Int) {
        draft.update { state ->
            val target = index + delta
            if (index !in state.actions.indices || target !in state.actions.indices) return@update state
            val reordered = state.actions.toMutableList().apply { add(target, removeAt(index)) }
            state.copy(actions = reordered.mapIndexed { i, action -> action.copy(sortOrder = i) })
        }
    }

    fun removeAction(index: Int) {
        draft.update { state ->
            state.copy(actions = state.actions.filterIndexed { i, _ -> i != index }.mapIndexed { i, a -> a.copy(sortOrder = i) })
        }
    }

    fun setImportText(text: String) {
        draft.update { it.copy(importText = text, parsed = null) }
    }

    /** 只解析，不覆盖当前动作：解析结果先给用户看一眼再决定要不要用。 */
    fun parseImport() {
        val text = draft.value.importText
        if (text.isBlank()) return
        val parsed = TrainingTextParser.parse(text)
        draft.update { it.copy(parsed = parsed) }
    }

    fun applyImport() {
        val parsed = draft.value.parsed ?: return
        val actions = parsed.actions.mapIndexed { index, action ->
            PlanActionEntity(
                planId = draft.value.plan.id,
                sortOrder = index,
                name = action.name,
                sets = action.sets,
                reps = action.reps,
                durationSec = action.durationSec,
                restSec = action.restSec,
                exerciseKey = action.exerciseKey,
                note = if (action.estimated) "时长为估算" else "",
            )
        }
        draft.update {
            it.copy(
                actions = actions,
                plan = it.plan.copy(
                    name = it.plan.name.ifBlank { "${parsed.bodyPart}训练" },
                    // 用户没改过部位（默认「全身」）才让解析结果决定部位，否则尊重手填
                    bodyPart = it.plan.bodyPart.takeIf { part -> part.isNotBlank() && part != "全身" }
                        ?: parsed.bodyPart,
                ),
            )
        }
    }

    /**
     * 预计分钟数：有动作就从动作算，没动作才用用户手填的值。
     *
     * 这里用的是「总时长」口径（每组时长 + 一次组间休息），与计时器按组展开的节拍
     * 可能差几十秒 —— 计时器落库的是实际耗时，这个字段只用于列表上的「约 X 分钟」。
     */
    fun estimatedMinutes(): Int {
        val actions = draft.value.actions
        if (actions.isEmpty()) return draft.value.plan.estimatedMinutes.coerceAtLeast(0)
        val totalSec = TrainingMath.totalSeconds(
            actions.map { TrainingMath.actionSeconds(it.durationSec, it.restSec) },
        )
        return TrainingMath.estimatedMinutes(totalSec)
    }

    fun save(onDone: () -> Unit) {
        val current = draft.value
        val name = current.plan.name.ifBlank { "未命名计划" }
        viewModelScope.launch {
            val id = container.workoutRepository.upsertPlan(
                current.plan.copy(name = name, estimatedMinutes = estimatedMinutes()),
            )
            container.workoutRepository.saveActions(id, current.actions.map { it.copy(planId = id) })
            onDone()
        }
    }

    fun delete() {
        val current = draft.value.plan
        if (current.id == 0L) return
        viewModelScope.launch { container.workoutRepository.deletePlan(current) }
    }

    companion object {
        /** 从动作库加入时的默认计量：力量类 3 组 × 12 次，计时类 3 组 × 60 秒。 */
        const val DEFAULT_REPS = 12
        const val DEFAULT_TIMED_SEC = 60
    }
}

@Composable
fun WorkoutPlanEditScreen(
    container: AppContainer,
    planId: Long?,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val viewModel = viewModel<WorkoutPlanEditViewModel>(
        factory = appViewModelFactory(container) { WorkoutPlanEditViewModel(it, planId) },
    )
    val draft by viewModel.uiState.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (planId == null) "新建计划" else "编辑计划") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (planId != null) {
                        TextButton(onClick = { viewModel.delete(); onBack() }) { Text("删除") }
                    }
                    TextButton(onClick = { viewModel.save(onBack) }) { Text("保存") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (draft.loadFailed) {
                Text(
                    text = "这个计划已经不在了，保存会新建一个副本。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = draft.plan.name,
                onValueChange = { value -> viewModel.updatePlan { it.copy(name = value) } },
                label = { Text("计划名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            OptionField(
                label = "主要部位",
                value = draft.plan.bodyPart,
                options = BodyPartChoices,
            ) { index -> viewModel.updatePlan { it.copy(bodyPart = BodyPartChoices[index]) } }

            SectionTitle("安排在周几")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val mask = DayOfWeekMask(draft.plan.weekMask)
                WeekdayOrder.forEach { day ->
                    FilterChip(
                        selected = mask.contains(day),
                        onClick = { viewModel.toggleDay(day) },
                        label = { Text(day.chineseShort()) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                QuickMask.entries.forEach { quick ->
                    OutlinedButton(onClick = {
                        viewModel.updatePlan { it.copy(weekMask = quick.mask.bits) }
                    }) { Text(quick.label) }
                }
            }

            Spacer(Modifier.height(10.dp))
            ColorSwatchField(
                label = "颜色",
                selectedArgb = draft.plan.colorArgb,
                onSelect = { argb -> viewModel.updatePlan { it.copy(colorArgb = argb) } },
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("动作", modifier = Modifier.weight(1f))
                Text(
                    text = "约 ${viewModel.estimatedMinutes()} 分钟",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (draft.actions.isEmpty()) {
                Text(
                    text = "还没有动作。可以从动作库挑，也可以直接写一个自定义动作；不配动作的计划也能保存，计时时会退成正着数的自由训练。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            draft.actions.forEachIndexed { index, action ->
                ActionEditor(
                    index = index,
                    action = action,
                    canMoveUp = index > 0,
                    canMoveDown = index < draft.actions.lastIndex,
                    onChange = { transform -> viewModel.updateAction(index, transform) },
                    onRemove = { viewModel.removeAction(index) },
                    onMove = { delta -> viewModel.moveAction(index, delta) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                OutlinedButton(onClick = { showPicker = true }) { Text("从动作库添加") }
                OutlinedButton(onClick = viewModel::addCustomAction) { Text("自定义动作") }
            }
            TextButton(onClick = { onNavigate(Routes.EXERCISE_LIBRARY) }) {
                Text("翻动作库百科（看要领和避坑提示）")
            }

            ImportBlock(
                text = draft.importText,
                parsed = draft.parsed,
                onTextChange = viewModel::setImportText,
                onParse = viewModel::parseImport,
                onApply = viewModel::applyImport,
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = draft.plan.note,
                onValueChange = { value -> viewModel.updatePlan { it.copy(note = value) } },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.save(onBack) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (draft.plan.id == 0L) "创建计划" else "保存修改") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPicker) {
        ExercisePickerDialog(
            onDismiss = { showPicker = false },
            onPick = { exercise ->
                viewModel.addFromCatalog(exercise)
                showPicker = false
            },
        )
    }
}

/** 保存草稿时不该被输入法弹出打断，所以组/次/秒/息都收敛成步进器而不是自由输入。 */
@Composable
private fun ActionEditor(
    index: Int,
    action: PlanActionEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: ((PlanActionEntity) -> PlanActionEntity) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = action.name,
                    onValueChange = { value -> onChange { it.copy(name = value) } },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    singleLine = true,
                    placeholder = { Text("动作名") },
                )
                // 排序与删除挤在同一行：四个步进器需要整行宽度，两行放会让卡片比动作本身还高
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(Icons.Filled.ExpandLess, contentDescription = "上移")
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = "下移")
                }
                IconButton(onClick = { onRemove() }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除这个动作")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Stepper("组", action.sets) { delta -> onChange { it.copy(sets = (it.sets + delta).coerceIn(1, 20)) } }
                Stepper("次", action.reps) { delta -> onChange { it.copy(reps = (it.reps + delta).coerceIn(0, 100)) } }
                // 步进 5 秒：训练时长本来就是个估计值，一次一动 1 秒只会让人放弃调准
                Stepper("秒", action.durationSec) { delta ->
                    onChange { it.copy(durationSec = (it.durationSec + delta * 5).coerceIn(5, 1800)) }
                }
                Stepper("息", action.restSec) { delta ->
                    onChange { it.copy(restSec = (it.restSec + delta * 5).coerceIn(0, 300)) }
                }
            }
            Text(
                // 次数动作也显示「每组约几秒」：durationSec 才是计时器真正用的数，
                // 只写「12 次」会让人以为时长可以忽略，实际上它按 5 秒/次估出来的。
                text = buildString {
                    val perSet = TrainingMath.setWorkSec(action.durationSec, action.sets)
                    if (action.reps > 0) {
                        append("${action.sets} 组 × ${action.reps} 次 · 每组约 $perSet 秒")
                    } else {
                        append("${action.sets} 组 × $perSet 秒")
                    }
                    append(" · 组间 ${action.restSec} 秒")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 一个「组/次/秒/息」的微型步进器。
 *
 * 用 −/+ 而不是数字输入框：编辑计划时手键盘会把整屏表单顶掉，四个字段来回切输入法
 * 比调数值本身累得多。
 */
@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            StepperButton("−", "少一点") { onChange(-1) }
            StepperButton("+", "多一点") { onChange(1) }
        }
    }
}

@Composable
private fun StepperButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * 「粘贴训练文案」区块。
 *
 * 解析是纯规则、离线的：识别率比调大模型低，但这段文案不用离开设备。
 * 解析结果先显示成预览，用户点「应用到计划」才覆盖动作清单 —— 规则解析认错了动作时，
 * 直接写进草稿等于把用户的计划悄悄改掉。
 */
@Composable
private fun ImportBlock(
    text: String,
    parsed: ParsedPlan?,
    onTextChange: (String) -> Unit,
    onParse: () -> Unit,
    onApply: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            SectionTitle("从训练文案导入", modifier = Modifier.weight(1f))
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                )
            }
        }
        // 展开与否用条件分支而不是 return：Column 的内容不是 inline lambda，
        // 在里面写 return 会直接编译不过。
        if (expanded) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                placeholder = { Text("把健身文案粘进来，例如「热身 开合跳3组30秒 哑铃卧推4组12次 组间休息60秒」") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                OutlinedButton(onClick = onParse, enabled = text.isNotBlank()) { Text("解析") }
                Button(onClick = onApply, enabled = parsed?.isEmpty == false) { Text("应用到计划") }
            }
            ImportPreview(parsed)
        }
    }
}

/** 解析结果预览：认错了就不落库，所以这里只负责让人看清规则版读到了什么。 */
@Composable
private fun ImportPreview(parsed: ParsedPlan?) {
    when {
        parsed == null -> Unit
        parsed.isEmpty -> Text(
            text = "没认出任何动作。规则版只认常见动作名和「N组M次/N秒」这类写法，认不出来就直接留空。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp),
        )
        else -> {
            Text(
                text = "识别为「${parsed.bodyPart}」，约 ${parsed.totalDurationMin} 分钟，${parsed.actions.size} 个动作：",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            parsed.actions.forEach { action ->
                Text(
                    text = "· ${action.name} ${action.sets}组" +
                        (if (action.reps > 0) "×${action.reps}次" else "×${action.durationSec}秒") +
                        " 息${action.restSec}秒" + (if (action.estimated) "（时长估算）" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExercisePickerDialog(
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = ExerciseCatalog.filter(null, query).take(40)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("挑一个动作") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜名称或器械") },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column {
                        matches.forEach { exercise ->
                            TextButton(
                                onClick = { onPick(exercise) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(exercise.name, modifier = Modifier.weight(1f))
                                    Text(
                                        text = "${exercise.part.label}·${exercise.equipment.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (matches.isEmpty()) {
                            Text(
                                text = "库里没有这个动作，可以关掉用「自定义动作」自己写。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** 日历表头是「一二三四五六日」，这里沿用同一个顺序，与 DayOfWeekMask 的 bit0=周一 一致。 */
private val WeekdayOrder = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

private enum class QuickMask(val label: String, val mask: DayOfWeekMask) {
    EVERYDAY("每天", DayOfWeekMask.ALL),
    WORKDAY("工作日", DayOfWeekMask.WORKDAYS),
    WEEKEND("周末", DayOfWeekMask.WEEKEND),
}

private fun DayOfWeek.chineseShort(): String = when (this) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

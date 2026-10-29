package com.paopao.calendar.ui.habit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.CategoryEntity
import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.common.ColorSwatchField
import com.paopao.calendar.ui.common.DateField
import com.paopao.calendar.ui.common.OptionField
import com.paopao.calendar.ui.common.SwitchField
import com.paopao.calendar.ui.theme.SwatchPalette
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 习惯编辑。
 *
 * 起止日期是这条表单的全部时间语义：开始日决定日历从哪天起会提示这个习惯，结束日决定它哪天退场。
 * 结束日留空（存 NULL）就是「无期限」，用户不必为一个长期习惯去填一个 2099 年的假日期。
 *
 * 打卡历史与总览不在这一页，在 [HabitStatsScreen]：编辑页管定义，总览页管事实，
 * 混在一屏里会让「改个名字」和「回看上个月的备注」两种完全不同的心智来回跳转。
 */
class HabitEditViewModel(
    container: AppContainer,
    habitId: Long?,
) : ViewModel() {

    private val repository = container.habitRepository

    val draft = MutableStateFlow(
        HabitEntity(
            name = "",
            colorArgb = SwatchPalette.first(),
            iconKey = DEFAULT_ICON,
            targetPerWeek = 7,
            startEpochDay = LocalDate.now().toEpochDay(),
        ),
    )

    val categories: StateFlow<List<CategoryEntity>> = container.categoryRepository
        .observeByScope(CategoryEntity.SCOPE_HABIT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        if (habitId != null) {
            viewModelScope.launch { repository.find(habitId)?.let { draft.value = it } }
        }
    }

    fun update(transform: (HabitEntity) -> HabitEntity) {
        draft.update(transform)
    }

    fun save(onDone: () -> Unit) {
        val current = draft.value
        val name = current.name.ifBlank { "未命名习惯" }
        viewModelScope.launch {
            repository.upsert(current.normalized().copy(name = name))
            onDone()
        }
    }

    fun delete() {
        val current = draft.value
        if (current.id == 0L) return
        viewModelScope.launch { repository.delete(current) }
    }

    /**
     * 结束日早于开始日时夹到开始日。
     *
     * 用户先填了结束日、又把开始日往后挪，是表单里唯一能让区间反过来的组合。
     * 与其存进去让 [com.paopao.calendar.domain.habit.HabitMath] 的分母变成 0 ——
     * 那会造出一个「永远不可能完成、却还挂在列表里」的习惯 —— 不如保存时就修掉。
     */
    private fun HabitEntity.normalized(): HabitEntity {
        val end = endEpochDay ?: return this
        return if (end < startEpochDay) copy(endEpochDay = startEpochDay) else this
    }

    private companion object {
        const val DEFAULT_ICON = "habit"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditScreen(
    container: AppContainer,
    habitId: Long?,
    onBack: () -> Unit,
) {
    val viewModel = viewModel<HabitEditViewModel>(
        factory = appViewModelFactory(container) { HabitEditViewModel(it, habitId) },
    )
    val item by viewModel.draft.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val targets = (1..7).map { if (it == 7) "每天" else "每周 $it 次" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (habitId == null) "新建习惯" else "编辑习惯") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (habitId != null) {
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
            OutlinedTextField(
                value = item.name,
                onValueChange = { value -> viewModel.update { it.copy(name = value) } },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            DateField(
                label = "开始日期",
                value = LocalDate.ofEpochDay(item.startEpochDay),
                onValueChange = { day -> viewModel.update { it.copy(startEpochDay = day.toEpochDay()) } },
            )

            SwitchField(
                label = "设定结束日期",
                checked = item.endEpochDay != null,
                onCheckedChange = { on ->
                    viewModel.update { it.copy(endEpochDay = if (on) it.startEpochDay else null) }
                },
            )
            val endDay = item.endEpochDay
            if (endDay != null) {
                DateField(
                    label = "结束日期",
                    value = LocalDate.ofEpochDay(endDay),
                    onValueChange = { day -> viewModel.update { it.copy(endEpochDay = day.toEpochDay()) } },
                )
                Text(
                    text = if (endDay < item.startEpochDay) {
                        "结束日早于开始日，保存时会按开始日处理"
                    } else {
                        "到期后它从日历和插件里退场，打卡记录仍保留在总览里"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }

            OptionField(
                label = "每周目标",
                value = targets[(item.targetPerWeek.coerceIn(1, 7)) - 1],
                options = targets,
            ) { index ->
                viewModel.update { it.copy(targetPerWeek = index + 1) }
            }

            OptionField(
                label = "分类",
                value = categories.find { it.id == item.categoryId }?.name ?: "未分类",
                options = listOf("未分类") + categories.map { it.name },
            ) { index ->
                viewModel.update { it.copy(categoryId = if (index == 0) null else categories[index - 1].id) }
            }

            ColorSwatchField(
                label = "颜色",
                selectedArgb = item.colorArgb,
                onSelect = { argb -> viewModel.update { it.copy(colorArgb = argb) } },
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.save(onBack) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (item.id == 0L) "添加" else "保存") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.paopao.calendar.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.CategoryEntity
import com.paopao.calendar.data.local.entity.InventoryEntity
import com.paopao.calendar.domain.model.DayOfWeekMask
import com.paopao.calendar.domain.model.Importance
import com.paopao.calendar.domain.model.Quadrant
import com.paopao.calendar.domain.model.Recurrence
import com.paopao.calendar.domain.model.RecurrenceCodec
import com.paopao.calendar.domain.model.Urgency
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.common.DateField
import com.paopao.calendar.ui.common.MinuteOfDayField
import com.paopao.calendar.ui.common.OptionField
import com.paopao.calendar.ui.common.SectionTitle
import com.paopao.calendar.ui.theme.quadrantColor
import com.paopao.calendar.ui.theme.quadrantHintRes
import com.paopao.calendar.ui.theme.quadrantNameRes
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val ImportanceLabels = listOf("无所谓", "一般", "重要")
private val UrgencyLabels = listOf("不急", "近期", "紧急")
private val ItemRecurrenceLabels = listOf("不重复", "每天", "工作日", "每周", "每月", "每年")

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryEditViewModel(
    private val container: AppContainer,
    private val inventoryId: Long?,
    presetDueDay: Long?,
) : ViewModel() {

    /** 表单当前值。新建时用默认行，编辑时在 init 里被数据库行覆盖。 */
    private val form = MutableStateFlow(
        InventoryEntity(title = "", dueEpochDay = presetDueDay),
    )
    val draft: StateFlow<InventoryEntity> = form

    private val savedOnce = MutableStateFlow<Long?>(null)

    val categories: StateFlow<List<CategoryEntity>> =
        container.categoryRepository.observeByScope(CategoryEntity.SCOPE_INVENTORY)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 已存事项才能查标签；新建时用哨兵 id，保存一次后切到真实 id 的流。 */
    val tagNames: StateFlow<List<String>> = savedOnce
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.inventoryRepository.observeTagNames(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subtasks: StateFlow<List<InventoryEntity>> = savedOnce
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.inventoryRepository.observeChildren(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val id = inventoryId
        if (id != null) {
            viewModelScope.launch {
                container.inventoryRepository.findById(id)?.let {
                    form.value = it
                    savedOnce.value = it.id
                }
            }
        }
    }

    fun update(transform: (InventoryEntity) -> InventoryEntity) {
        form.value = transform(form.value)
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val current = form.value
            val id = container.inventoryRepository.upsert(
                current.copy(title = current.title.ifBlank { "未命名事项" }),
            )
            savedOnce.value = current.id.takeIf { it != 0L } ?: id
            onSaved()
        }
    }

    fun delete() {
        val id = form.value.id
        if (id != 0L) viewModelScope.launch { container.inventoryRepository.softDelete(id) }
    }

    fun attachTag(name: String) {
        val id = savedOnce.value ?: return
        viewModelScope.launch { container.inventoryRepository.attachTag(id, name) }
    }

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val parentId = savedOnce.value ?: run {
                val id = container.inventoryRepository.upsert(
                    form.value.copy(title = form.value.title.ifBlank { "未命名事项" }),
                )
                savedOnce.value = id
                id
            }
            container.inventoryRepository.upsert(InventoryEntity(title = title.trim(), parentId = parentId))
        }
    }

    fun toggleSubtask(child: InventoryEntity) {
        viewModelScope.launch { container.inventoryRepository.setDone(child.id, !child.done) }
    }
}

@Composable
fun InventoryEditScreen(
    container: AppContainer,
    inventoryId: Long?,
    presetDueDay: Long?,
    onBack: () -> Unit,
) {
    val viewModel = viewModel<InventoryEditViewModel>(
        factory = appViewModelFactory(container) { InventoryEditViewModel(it, inventoryId, presetDueDay) },
    )
    val item by viewModel.draft.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val tagNames by viewModel.tagNames.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    var subtaskTitle by remember { mutableStateOf("") }
    var tagName by remember { mutableStateOf("") }

    val startDay = item.dueEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (inventoryId == null) "新建事项" else "编辑事项") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (inventoryId != null) {
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
                value = item.title,
                onValueChange = { value -> viewModel.update { it.copy(title = value) } },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = item.note,
                onValueChange = { value -> viewModel.update { it.copy(note = value) } },
                label = { Text("备注") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("重要 × 紧急")
            ChipGroup(
                options = ImportanceLabels,
                selectedIndex = item.importance - 1,
            ) { index ->
                val importance = Importance.fromRank(index + 1)
                viewModel.update {
                    it.copy(
                        importance = importance.rank,
                        quadrantIndex = Quadrant.derive(importance, Urgency.fromRank(it.urgency)).ordinal + 1,
                    )
                }
            }
            ChipGroup(
                options = UrgencyLabels,
                selectedIndex = item.urgency - 1,
            ) { index ->
                val urgency = Urgency.fromRank(index + 1)
                viewModel.update {
                    it.copy(
                        urgency = urgency.rank,
                        quadrantIndex = Quadrant.derive(Importance.fromRank(it.importance), urgency).ordinal + 1,
                    )
                }
            }
            AssistChip(
                onClick = { },
                label = {
                    Text(
                        text = "落在：" + stringResource(quadrantNameRes(item.quadrantIndex)) +
                            " · " + stringResource(quadrantHintRes(item.quadrantIndex)),
                        color = quadrantColor(item.quadrantIndex),
                    )
                },
            )

            SectionTitle("时间")
            val dueDay = item.dueEpochDay?.let { LocalDate.ofEpochDay(it) }
            if (dueDay != null) {
                DateField(label = "到期日", value = dueDay) { date ->
                    viewModel.update { it.copy(dueEpochDay = date.toEpochDay()) }
                }
                MinuteOfDayField(label = "时刻", minuteOfDay = item.dueMinuteOfDay) { minute ->
                    viewModel.update { it.copy(dueMinuteOfDay = minute) }
                }
                TextButton(onClick = {
                    viewModel.update { it.copy(dueEpochDay = null, dueMinuteOfDay = null) }
                }) { Text("清除到期日") }
            } else {
                TextButton(onClick = {
                    viewModel.update { it.copy(dueEpochDay = LocalDate.now().toEpochDay()) }
                }) { Text("设置到期日") }
            }

            SectionTitle("重复")
            OptionField(
                label = "重复规则",
                value = ItemRecurrenceLabels[item.recurrenceIndex()],
                options = ItemRecurrenceLabels,
            ) { index ->
                viewModel.update { it.copy(recurrenceText = RecurrenceCodec.encode(itemRecurrenceFor(index, startDay))) }
            }

            SectionTitle("分类")
            ChipGroup(
                options = listOf("未分类") + categories.map { it.name },
                selectedIndex = categories.indexOfFirst { it.id == item.categoryId } + 1,
            ) { index ->
                viewModel.update { it.copy(categoryId = if (index == 0) null else categories.getOrNull(index - 1)?.id) }
            }

            SectionTitle("标签")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tagNames.forEach { name -> AssistChip(onClick = { }, label = { Text(name) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    placeholder = { Text("新标签") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    viewModel.attachTag(tagName.trim())
                    tagName = ""
                }) { Icon(Icons.Filled.Add, contentDescription = "添加标签") }
            }
            if (inventoryId == null) {
                Text(
                    text = "标签与子任务在首次保存后才能挂载。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("子任务")
            subtasks.forEach { child ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = child.done, onCheckedChange = { viewModel.toggleSubtask(child) })
                    Text(child.title, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = subtaskTitle,
                    onValueChange = { subtaskTitle = it },
                    placeholder = { Text("新增子任务") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    viewModel.addSubtask(subtaskTitle)
                    subtaskTitle = ""
                }) { Icon(Icons.Filled.Add, contentDescription = "添加子任务") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ChipGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(label) },
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

private fun InventoryEntity.recurrenceIndex(): Int = when (val rule = RecurrenceCodec.decode(recurrenceText)) {
    Recurrence.None -> 0
    is Recurrence.Weekly -> when (rule.days) {
        DayOfWeekMask.ALL -> 1
        DayOfWeekMask.WORKDAYS -> 2
        else -> 3
    }
    is Recurrence.Monthly -> 4
    Recurrence.Yearly -> 5
    is Recurrence.LunarYearly -> 6
}

private fun itemRecurrenceFor(index: Int, start: LocalDate): Recurrence = when (index) {
    0 -> Recurrence.None
    1 -> Recurrence.Weekly(DayOfWeekMask.ALL)
    2 -> Recurrence.Weekly(DayOfWeekMask.WORKDAYS)
    3 -> Recurrence.Weekly(DayOfWeekMask.of(start.dayOfWeek))
    4 -> Recurrence.Monthly(start.dayOfMonth)
    else -> Recurrence.Yearly
}

package com.paopao.calendar.ui.memorial

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.MemorialEntity
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.common.ColorSwatchField
import com.paopao.calendar.ui.common.DateField
import com.paopao.calendar.ui.common.OptionField
import com.paopao.calendar.ui.common.SectionTitle
import com.paopao.calendar.ui.theme.SwatchPalette
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 纪念日编辑。
 *
 * 「按农历重复」这个开关决定了列表页倒数日期的算法路径，也决定了 2 月 29 日那一类
 * 边界情况的处理方式，所以它必须在表单里显眼，而不是藏在高级设置里。
 */
class MemorialEditViewModel(
    container: AppContainer,
    memorialId: Long?,
) : ViewModel() {

    private val repository = container.memorialRepository

    val draft = MutableStateFlow(
        MemorialEntity(
            name = "",
            anchorEpochDay = LocalDate.now().toEpochDay(),
            colorArgb = SwatchPalette[4],
            iconKey = DEFAULT_ICON,
        ),
    )

    init {
        if (memorialId != null) {
            viewModelScope.launch { repository.find(memorialId)?.let { draft.value = it } }
        }
    }

    fun update(transform: (MemorialEntity) -> MemorialEntity) {
        draft.value = transform(draft.value)
    }

    fun save(onDone: () -> Unit) {
        val current = draft.value
        val name = current.name.ifBlank { "未命名纪念日" }
        viewModelScope.launch {
            repository.upsert(current.copy(name = name))
            onDone()
        }
    }

    fun delete() {
        val current = draft.value
        if (current.id == 0L) return
        viewModelScope.launch { repository.delete(current) }
    }

    private companion object {
        const val DEFAULT_ICON = "memorial"
    }
}

private val RemindDayLabels = listOf("当天", "提前 1 天", "提前 3 天", "提前 7 天", "提前 30 天")
private val RemindDayValues = listOf(0, 1, 3, 7, 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorialEditScreen(
    container: AppContainer,
    memorialId: Long?,
    onBack: () -> Unit,
) {
    val viewModel = viewModel<MemorialEditViewModel>(
        factory = appViewModelFactory(container) { MemorialEditViewModel(it, memorialId) },
    )
    val item by viewModel.draft.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (memorialId == null) "新建纪念日" else "编辑纪念日") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (memorialId != null) {
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
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = item.note,
                onValueChange = { value -> viewModel.update { it.copy(note = value) } },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            DateField(
                label = "发生日期",
                value = LocalDate.ofEpochDay(item.anchorEpochDay),
                onValueChange = { day -> viewModel.update { it.copy(anchorEpochDay = day.toEpochDay()) } },
            )

            SectionTitle("重复与提醒")
            SwitchRow("每年重复", item.repeatYearly) { value ->
                viewModel.update { it.copy(repeatYearly = value) }
            }
            SwitchRow("按农历", item.isLunar) { value ->
                viewModel.update { it.copy(isLunar = value) }
            }
            OptionField(
                label = "提前提醒",
                value = RemindDayLabels[RemindDayValues.indexOf(item.remindDaysBefore).coerceAtLeast(0)],
                options = RemindDayLabels,
            ) { index ->
                viewModel.update { it.copy(remindDaysBefore = RemindDayValues[index]) }
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

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

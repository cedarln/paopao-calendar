package com.paopao.calendar.ui.training

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paopao.calendar.domain.workout.BodyPart
import com.paopao.calendar.domain.workout.Difficulty
import com.paopao.calendar.domain.workout.Equipment
import com.paopao.calendar.domain.workout.Exercise
import com.paopao.calendar.domain.workout.ExerciseCatalog

/**
 * 动作库百科。
 *
 * 纯静态数据，没有 ViewModel：这份库不写数据库，加一层状态只是把一个 `filter` 调用
 * 包进它不该待的地方。搜索词与展开状态用 rememberSaveable，转屏不会把用户看的位置弄丢。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    // 用下标而不是可空枚举存筛选：rememberSaveable 的类型上界是 Any，
    // 传 BodyPart? 连编译都过不去，为了一个筛选状态写 Saver 不值得。
    var partIndex by rememberSaveable { mutableStateOf(-1) }
    val part = ExerciseCatalog.partOrder.getOrNull(partIndex)

    val results = ExerciseCatalog.filter(part, query)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("动作库") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜动作，例如「划船」或「杠铃」") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    // 「全部」+ 七个部位一行放不下，横向滚动而不是换行：
                    // 换行会把「全部」挤到第二行，用户会以为没有取消筛选这个选项。
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(selected = partIndex < 0, onClick = { partIndex = -1 }, label = { Text("全部") })
                    ExerciseCatalog.partOrder.forEachIndexed { index, option ->
                        FilterChip(
                            selected = partIndex == index,
                            onClick = { partIndex = if (partIndex == index) -1 else index },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
            item {
                Text(
                    text = "${results.size} 个动作",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        text = "没有匹配的动作。词典里没收录的自定义动作仍然可以直接写进计划。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(results, key = { it.key }) { exercise ->
                ExerciseCard(exercise)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise) {
    var expanded by rememberSaveable(exercise.key) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = listOf(exercise.part.label, exercise.equipment.label, "难度${exercise.level.label}")
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!expanded) {
                    Text(
                        text = exercise.tips,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开要领",
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                exercise.steps.forEachIndexed { index, step ->
                    Text(
                        text = "${index + 1}. $step",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Text(
                    text = "注意：${exercise.tips}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

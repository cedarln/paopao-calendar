@file:OptIn(ExperimentalMaterial3Api::class)

package com.paopao.calendar.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.InventoryEntity
import com.paopao.calendar.domain.model.Quadrant
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.common.CommonFormatters
import com.paopao.calendar.ui.navigation.Routes
import com.paopao.calendar.ui.theme.quadrantColor
import com.paopao.calendar.ui.theme.quadrantHintRes
import com.paopao.calendar.ui.theme.quadrantNameRes
import java.time.LocalDate

@Composable
fun InventoryScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit = {},
) {
    val viewModel = viewModel<InventoryViewModel>(
        factory = appViewModelFactory(container) { InventoryViewModel(it) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showQuickAdd by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (state.mode != InventoryMode.TRASH) {
                FloatingActionButton(onClick = { onNavigate(Routes.inventoryEdit()) }) {
                    Icon(Icons.Filled.Add, contentDescription = "新建事项")
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("清单", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("显示已完成", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
                Switch(checked = state.showDone, onCheckedChange = viewModel::setShowDone)
            }
            InventoryTabs(
                mode = state.mode,
                onSelect = viewModel::setMode,
                query = state.query,
                onQueryChange = viewModel::setQuery,
            )

            when (state.mode) {
                InventoryMode.BOARD -> MatrixBoard(
                    itemsByQuadrant = state.byQuadrant,
                    onToggle = viewModel::toggleDone,
                    onOpen = { onNavigate(Routes.inventoryEdit(inventoryId = it.id)) },
                    onQuadrantSelected = viewModel::focusQuadrant,
                    onQuickAdd = { showQuickAdd = true },
                    modifier = Modifier.fillMaxSize(),
                )

                InventoryMode.LIST -> ItemList(
                    items = state.roots,
                    emptyText = "还没有事项。四象限的意义在于逼你把「紧急」和「重要」分开想。",
                    onToggle = viewModel::toggleDone,
                    onOpen = { onNavigate(Routes.inventoryEdit(inventoryId = it.id)) },
                    onDelete = viewModel::moveToTrash,
                )

                InventoryMode.SEARCH -> ItemList(
                    items = state.searchResults,
                    emptyText = if (state.query.isBlank()) "输入关键字搜索事项。" else "没有匹配「${state.query}」的事项。",
                    onToggle = viewModel::toggleDone,
                    onOpen = { onNavigate(Routes.inventoryEdit(inventoryId = it.id)) },
                    onDelete = viewModel::moveToTrash,
                )

                InventoryMode.TRASH -> TrashList(
                    items = state.trash,
                    onRestore = viewModel::restore,
                    onEmpty = viewModel::emptyTrash,
                )
            }
        }
    }

    if (showQuickAdd) {
        QuickAddDialog(
            onDismiss = { showQuickAdd = false },
            onConfirm = { title ->
                viewModel.quickAdd(title, LocalDate.now())
                showQuickAdd = false
            },
        )
    }
}

@Composable
private fun InventoryTabs(
    mode: InventoryMode,
    onSelect: (InventoryMode) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    if (mode == InventoryMode.SEARCH) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("搜索事项") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    TabRow(selectedTabIndex = mode.ordinal) {
        listOf(InventoryMode.BOARD, InventoryMode.LIST, InventoryMode.TRASH, InventoryMode.SEARCH).forEach { item ->
            Tab(
                selected = mode == item,
                onClick = { onSelect(item) },
                text = { Text(item.label()) },
            )
        }
    }
}

private fun InventoryMode.label(): String = when (this) {
    InventoryMode.BOARD -> "矩阵"
    InventoryMode.LIST -> "清单"
    InventoryMode.TRASH -> "回收站"
    InventoryMode.SEARCH -> "搜索"
}

/** 2×2 矩阵板：四象限是这个应用的核心界面，所以不做成筛选器而是常驻视图。 */
@Composable
private fun MatrixBoard(
    itemsByQuadrant: Map<Quadrant, List<InventoryEntity>>,
    onToggle: (InventoryEntity) -> Unit,
    onOpen: (InventoryEntity) -> Unit,
    onQuadrantSelected: (Quadrant?) -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(listOf(Quadrant.Q1, Quadrant.Q3), listOf(Quadrant.Q2, Quadrant.Q4))
    Column(modifier = modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { quadrants ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quadrants.forEach { quadrant ->
                    QuadrantCell(
                        quadrant = quadrant,
                        items = itemsByQuadrant[quadrant].orEmpty(),
                        onToggle = onToggle,
                        onOpen = onOpen,
                        onSeeAll = { onQuadrantSelected(quadrant) },
                        onQuickAdd = onQuickAdd,
                        modifier = Modifier
                            .weight(1f)
                            .height(200.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuadrantCell(
    quadrant: Quadrant,
    items: List<InventoryEntity>,
    onToggle: (InventoryEntity) -> Unit,
    onOpen: (InventoryEntity) -> Unit,
    onSeeAll: () -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = quadrantColor(quadrant.ordinal + 1)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onSeeAll)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(quadrantNameRes(quadrant.ordinal + 1)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(quadrantHintRes(quadrant.ordinal + 1)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${items.size}", style = MaterialTheme.typography.labelLarge, color = color)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            ) {
                items.take(3).forEach { item ->
                    CompactItemRow(item = item, onToggle = { onToggle(item) }, onOpen = { onOpen(item) })
                }
                if (items.size > 3) {
                    Text(
                        text = "还有 ${items.size - 3} 项",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(onClick = onSeeAll)
                            .padding(4.dp),
                    )
                }
                if (items.isEmpty()) {
                    Text(
                        text = "空",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
            TextButton(onClick = onQuickAdd, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加")
            }
        }
    }
}

@Composable
private fun CompactItemRow(item: InventoryEntity, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.done, onCheckedChange = { onToggle() }, modifier = Modifier.size(30.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen),
        )
    }
}

@Composable
private fun ItemList(
    items: List<InventoryEntity>,
    emptyText: String,
    onToggle: (InventoryEntity) -> Unit,
    onOpen: (InventoryEntity) -> Unit,
    onDelete: (InventoryEntity) -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(8.dp)) {
        items(items, key = { it.id }) { item ->
            FullItemRow(
                item = item,
                onToggle = { onToggle(item) },
                onOpen = { onOpen(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

@Composable
private fun FullItemRow(item: InventoryEntity, onToggle: () -> Unit, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, end = 2.dp)) {
            Checkbox(checked = item.done, onCheckedChange = { onToggle() })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                )
                val subtitle = itemSubtitle(item)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = quadrantColor(item.quadrantIndex),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        }
    }
}

private fun itemSubtitle(item: InventoryEntity): String? {
    val date = item.dueEpochDay?.let { LocalDate.ofEpochDay(it).format(CommonFormatters.chineseMonthDay) }
    val time = item.dueMinuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) }
    val parts = listOfNotNull(date, time)
    return parts.joinToString(" ").takeIf { it.isNotEmpty() }
}

@Composable
private fun TrashList(items: List<InventoryEntity>, onRestore: (InventoryEntity) -> Unit, onEmpty: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "软删除保留 30 天，之后自动清理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEmpty, enabled = items.isNotEmpty()) { Text("清空") }
        }
        if (items.isEmpty()) {
            Text(
                text = "回收站是空的。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(items, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyMedium)
                                item.deletedAtMillis?.let {
                                    Text(
                                        text = "删除于 " +
                                            java.time.Instant.ofEpochMilli(it)
                                                .atZone(java.time.ZoneId.systemDefault())
                                                .toLocalDate()
                                                .format(CommonFormatters.chineseMonthDay),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(onClick = { onRestore(item) }) { Text("恢复") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快速添加") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("要做什么？") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

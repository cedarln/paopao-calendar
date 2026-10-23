package com.paopao.calendar.ui.memorial

import androidx.compose.foundation.background
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.MemorialEntity
import com.paopao.calendar.data.repository.MemorialOccurrence
import com.paopao.calendar.domain.calendar.ChineseCalendar
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.common.CommonFormatters
import com.paopao.calendar.ui.navigation.Routes
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 纪念日列表。
 *
 * 倒数天数每次读取都从锚点重算，不入库：农历纪念日的「今年是哪天」本身要过一遍历法换算，
 * 存下来就得在跨年时批量迁移，而这里的量级（几十条）根本不值得。
 */
data class MemorialRow(
    val memorial: MemorialEntity,
    val occurrence: MemorialOccurrence,
)

class MemorialViewModel(private val container: AppContainer) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val state = container.memorialRepository.observeAll()
        .map { memorials ->
            val rows = memorials.map { MemorialRow(it, container.memorialRepository.nextOccurrence(it, today)) }
            // 还没到的按由近及远，已经过去的（不重复的一次性纪念日）按由新到旧，两组不混排。
            val (upcoming, passed) = rows.partition { it.occurrence.daysUntil >= 0 }
            upcoming.sortedBy { it.occurrence.daysUntil } + passed.sortedByDescending { it.occurrence.daysUntil }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorialScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit,
) {
    val viewModel = viewModel<MemorialViewModel>(
        factory = appViewModelFactory(container) { MemorialViewModel(it) },
    )
    val rows by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate(Routes.memorialEdit()) }) {
                Icon(Icons.Filled.Add, contentDescription = "新建纪念日")
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
            item { Text("纪念日", style = MaterialTheme.typography.titleLarge) }
            if (rows.isEmpty()) {
                item {
                    Text(
                        text = "还没有纪念日。农历那一档是给长辈生日留的 —— 那才是真实需求。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(rows, key = { it.memorial.id }) { row ->
                MemorialCard(row = row, onClick = { onNavigate(Routes.memorialEdit(row.memorial.id)) })
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun MemorialCard(row: MemorialRow, onClick: () -> Unit) {
    val accent = Color(row.memorial.colorArgb)
    val anchor = LocalDate.ofEpochDay(row.memorial.anchorEpochDay)
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = row.memorial.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(anchor.format(CommonFormatters.chineseDate))
                        if (row.memorial.isLunar) {
                            val info = ChineseCalendar.infoOf(anchor)
                            append(" · 农历${info.lunarMonthName}月${info.lunarDayName}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CountdownText(row)
        }
    }
}

@Composable
private fun CountdownText(row: MemorialRow) {
    val days = row.occurrence.daysUntil
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = when {
                row.occurrence.isToday -> "就是今天"
                days > 0 -> "$days"
                else -> "${row.occurrence.passed}"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = if (row.occurrence.isToday) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        )
        Text(
            text = when {
                row.occurrence.isToday -> ""
                days > 0 -> "天后"
                else -> "天以来"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

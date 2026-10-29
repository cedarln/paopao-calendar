package com.paopao.calendar.ui.export

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.domain.export.ExportFormat
import com.paopao.calendar.domain.export.ExportPreset
import com.paopao.calendar.domain.export.ExportRange
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.common.DateField
import com.paopao.calendar.ui.common.SectionTitle
import java.time.LocalDate

/**
 * 导出页：选范围、选格式，然后「存到文件」或「分享出去」。
 *
 * 两个动作都给，是因为它们的失败模式完全不同：存文件可能被网盘 App 拒权限，
 * 分享可能在收件端丢扩展名。只留一个入口，撞上任何一种都得从头再来一遍。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel = viewModel<ExportViewModel>(
        factory = appViewModelFactory(container) { ExportViewModel(it) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val today = remember { LocalDate.now() }
    var preset by remember { mutableStateOf(ExportPreset.LAST_2_MONTHS) }
    var format by remember { mutableStateOf(ExportFormat.MARKDOWN) }
    var customFrom by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var customTo by remember { mutableStateOf(today) }
    val range = remember(preset, customFrom, customTo, today) {
        if (preset == ExportPreset.CUSTOM) safeRange(customFrom, customTo) else ExportRange.preset(preset, today)
    }

    // CreateDocument 靠 MIME 决定目标 App 给不给保存按钮，所以契约跟着格式走。
    // 契约在注册之后不可变，换格式时让 Compose 重建这个 launcher 而不是复用旧的。
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(format.mimeType),
    ) { uri ->
        uri?.let { viewModel.saveTo(it, format, range) }
    }

    LaunchedEffect(state.shareReady) {
        val uri = state.shareReady
        if (uri != null) {
            // 授权标志在外层 chooser 上再写一遍：内层 intent 的 flag 不保证被系统带到收件应用，
            // 少了它就是对端「收到一个打不开的文件」——最难复盘的那种失败。
            context.startActivity(
                Intent.createChooser(viewModel.shareIntent(uri, format), "分享导出文件")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            viewModel.shareConsumed()
        }
    }

    LaunchedEffect(state.notice) {
        val notice = state.notice
        if (notice != null) {
            snackbarHostState.showSnackbar(notice)
            viewModel.noticeShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("导出") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("时间范围")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExportPreset.entries.forEach { item ->
                    FilterChip(
                        selected = preset == item,
                        onClick = { preset = item },
                        label = { Text(item.label) },
                    )
                }
            }
            if (preset == ExportPreset.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateField(
                        label = "起始日",
                        value = customFrom,
                        modifier = Modifier.weight(1f),
                        onValueChange = { customFrom = it },
                    )
                    DateField(
                        label = "结束日",
                        value = customTo,
                        modifier = Modifier.weight(1f),
                        onValueChange = { customTo = it },
                    )
                }
            }
            Text(
                text = "范围：${range.displayText()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("文件格式")
            ExportFormat.entries.forEach { item ->
                FormatCard(format = item, selected = format == item, onClick = { format = item })
            }

            Text(
                text = hintFor(format),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { saveLauncher.launch(viewModel.fileNameFor(format, range)) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.busy,
                ) { Text("存到文件") }
                OutlinedButton(
                    onClick = { viewModel.prepareShare(format, range) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.busy,
                ) { Text("分享") }
            }

            if (state.busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("正在生成${format.label}…", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FormatCard(format: ExportFormat, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(format.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = format.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 起止选反了不拦：ExportRange 构造时就 require，界面这边先替用户把它换算过来。 */
private fun safeRange(from: LocalDate, to: LocalDate): ExportRange =
    if (from.isAfter(to)) ExportRange(to, from) else ExportRange(from, to)

private fun hintFor(format: ExportFormat): String = when (format) {
    ExportFormat.JSON -> "JSON 不受上面的范围限制：备份永远导全部数据，选了哪一段只记在文件头里。"
    ExportFormat.MARKDOWN -> "月报只列有记录的日子，空白的一天不占一行。"
    ExportFormat.ICS -> "重复日程会展开成一条条真实事件；习惯与训练不进这个文件。"
}

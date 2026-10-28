package com.paopao.calendar.ui.settings

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.calendar.AppContainer
import com.paopao.calendar.R
import com.paopao.calendar.ui.appViewModelFactory
import com.paopao.calendar.ui.calendar.MonthSettingsContent
import com.paopao.calendar.ui.common.SectionTitle

/**
 * 设置页。
 *
 * 月视图那一块直接复用日历抽屉的正文，专注时长在这里也能改：把两处入口写成两套
 * 控件的话，改一个开关要记得改两个地方，这种债一定会还得上的。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel = viewModel<SettingsViewModel>(
        factory = appViewModelFactory(container) { SettingsViewModel(it) },
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            MonthSettingsContent(settings = settings, viewModel = viewModel)

            SectionTitle("专注时长")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 25, 45).forEach { minutes ->
                    FilterChip(
                        selected = settings.focusWorkMinutes == minutes,
                        onClick = { viewModel.setFocusMinutes(minutes, settings.focusRestMinutes) },
                        label = { Text("专注 $minutes") },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 5, 10).forEach { minutes ->
                    FilterChip(
                        selected = settings.focusRestMinutes == minutes,
                        onClick = { viewModel.setFocusMinutes(settings.focusWorkMinutes, minutes) },
                        label = { Text("休息 $minutes") },
                    )
                }
            }

            SectionTitle("更新")
            UpdateCard()

            SectionTitle(stringResource(R.string.settings_privacy))
            Text(
                text = stringResource(R.string.settings_privacy_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_network_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

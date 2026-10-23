package com.paopao.calendar.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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

/**
 * 从日历抽屉里「查看全部月视图设置」进入的全屏页。
 *
 * 内容与抽屉共用 [MonthSettingsContent]：同一个配置写两遍必然漂移，
 * 而这两个入口唯一的不同就是有没有那层半透明遮罩。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSettingsScreen(
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
                title = { Text(stringResource(R.string.settings_month_view)) },
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.paopao.calendar.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paopao.calendar.data.settings.AppSettings
import com.paopao.calendar.data.settings.ThemeMode
import com.paopao.calendar.domain.model.MonthClockStyle
import com.paopao.calendar.domain.model.MonthColorScheme
import com.paopao.calendar.ui.common.SectionTitle
import com.paopao.calendar.ui.settings.SettingsViewModel
import java.time.DayOfWeek

private val ClockStyleOptions = listOf(
    MonthClockStyle.PLAIN to "纯数字",
    MonthClockStyle.LUNAR_SUB to "农历/节气副标题",
    MonthClockStyle.DENSITY_DOT to "事项密度圆点",
    MonthClockStyle.LARGE to "大字",
)

private val ColorSchemeOptions = listOf(
    MonthColorScheme.DEFAULT to "默认",
    MonthColorScheme.PASTEL to "柔和",
    MonthColorScheme.HIGH_CONTRAST to "高对比",
    MonthColorScheme.MONOCHROME to "单色",
)

/**
 * 月视图的三轴配置面板。
 *
 * 外观、颜色、内容是三个独立开关组而不是四选一的预设：原应用里用户会既要农历、
 * 又要柔和配色，组合枚举在这里必然爆炸。
 */
@Composable
fun MonthSettingsSheet(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            MonthSettingsContent(
                settings = settings,
                viewModel = viewModel,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 月视图配置本体：抽屉页与「更多 → 设置」里的全屏页共用同一套控件。 */
@Composable
fun MonthSettingsContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle("格子外观")
        ChoiceRow(
            options = ClockStyleOptions.map { it.second },
            selectedIndex = ClockStyleOptions.indexOfFirst { it.first == settings.clockStyle },
        ) { index ->
            viewModel.setClockStyle(ClockStyleOptions[index].first)
        }

        SectionTitle("配色")
        ChoiceRow(
            options = ColorSchemeOptions.map { it.second },
            selectedIndex = ColorSchemeOptions.indexOfFirst { it.first == settings.colorScheme },
        ) { index ->
            viewModel.setColorScheme(ColorSchemeOptions[index].first)
        }

        SectionTitle("显示内容")
        SwitchRow("农历", settings.showLunar, viewModel::setShowLunar)
        SwitchRow("节气", settings.showSolarTerm, viewModel::setShowSolarTerm)
        SwitchRow("法定节假日", settings.showHoliday, viewModel::setShowHoliday)

        SectionTitle("周起始日")
        ChoiceRow(
            options = listOf("星期一", "星期日"),
            selectedIndex = if (settings.firstDayOfWeek == DayOfWeek.SUNDAY) 1 else 0,
        ) { index ->
            viewModel.setFirstDayOfWeek(if (index == 1) DayOfWeek.SUNDAY else DayOfWeek.MONDAY)
        }

        SectionTitle("主题")
        ChoiceRow(
            options = listOf("跟随系统", "浅色", "深色"),
            selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
        ) { index ->
            viewModel.setThemeMode(ThemeMode.entries[index])
        }
    }
}

@Composable
private fun ChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, label ->
            androidx.compose.material3.FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(label) },
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

package com.paopao.calendar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.settings.AppSettings
import com.paopao.calendar.data.settings.ThemeMode
import com.paopao.calendar.domain.model.CalendarViewMode
import com.paopao.calendar.domain.model.MonthClockStyle
import com.paopao.calendar.domain.model.MonthColorScheme
import java.time.DayOfWeek
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页与日历页共用的偏好入口。
 *
 * 两处都要改月视图配置，各自写一份 setter 会很快漂移，所以只留这一个类。
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setThemeMode(mode: ThemeMode) = launch { container.settingsStore.setThemeMode(mode) }

    fun setViewMode(mode: CalendarViewMode) = launch { container.settingsStore.setViewMode(mode) }

    fun setClockStyle(style: MonthClockStyle) = launch { container.settingsStore.setClockStyle(style) }

    fun setColorScheme(scheme: MonthColorScheme) = launch { container.settingsStore.setColorScheme(scheme) }

    fun setShowLunar(value: Boolean) = launch { container.settingsStore.setShowLunar(value) }

    fun setShowSolarTerm(value: Boolean) = launch { container.settingsStore.setShowSolarTerm(value) }

    fun setShowHoliday(value: Boolean) = launch { container.settingsStore.setShowHoliday(value) }

    fun setFirstDayOfWeek(day: DayOfWeek) = launch { container.settingsStore.setFirstDayOfWeek(day) }

    fun setFocusMinutes(work: Int, rest: Int) = launch { container.settingsStore.setFocusMinutes(work, rest) }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}

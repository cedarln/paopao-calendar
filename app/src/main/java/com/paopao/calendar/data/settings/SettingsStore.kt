package com.paopao.calendar.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paopao.calendar.domain.model.CalendarViewMode
import com.paopao.calendar.domain.model.MonthClockStyle
import com.paopao.calendar.domain.model.MonthColorScheme
import com.paopao.calendar.domain.calendar.contentFlagsOf
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "paopao_settings")

/** 主题模式。SYSTEM 跟随系统深色设置。 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * 用户偏好。
 *
 * 月视图的三个配置轴（外观 / 颜色 / 内容）各自独立存储，与 [com.paopao.calendar.domain.model]
 * 里的三轴一一对应。存成三个 key 而不是一个组合枚举，是为了让「新增一档外观」
 * 不需要迁移历史数据 —— 组合枚举加一档要补 4×N 个旧值。
 */
class SettingsStore(private val context: Context) {

    private val keys = object {
        val themeMode = stringPreferencesKey("theme_mode")
        val viewMode = stringPreferencesKey("calendar_view_mode")
        val clockStyle = stringPreferencesKey("month_clock_style")
        val colorScheme = stringPreferencesKey("month_color_scheme")
        val showLunar = booleanPreferencesKey("show_lunar")
        val showSolarTerm = booleanPreferencesKey("show_solar_term")
        val showHoliday = booleanPreferencesKey("show_holiday")
        val firstDayOfWeek = intPreferencesKey("first_day_of_week")
        val focusWorkMinutes = intPreferencesKey("focus_work_minutes")
        val focusRestMinutes = intPreferencesKey("focus_rest_minutes")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            viewMode = prefs[keys.viewMode]?.let { runCatching { CalendarViewMode.valueOf(it) }.getOrNull() }
                ?: CalendarViewMode.MONTH,
            clockStyle = prefs[keys.clockStyle]?.let { runCatching { MonthClockStyle.valueOf(it) }.getOrNull() }
                ?: MonthClockStyle.LUNAR_SUB,
            colorScheme = prefs[keys.colorScheme]?.let { runCatching { MonthColorScheme.valueOf(it) }.getOrNull() }
                ?: MonthColorScheme.DEFAULT,
            showLunar = prefs[keys.showLunar] ?: true,
            showSolarTerm = prefs[keys.showSolarTerm] ?: true,
            showHoliday = prefs[keys.showHoliday] ?: true,
            firstDayOfWeek = prefs[keys.firstDayOfWeek]?.let { DayOfWeek.of(it) } ?: DayOfWeek.MONDAY,
            focusWorkMinutes = prefs[keys.focusWorkMinutes] ?: DEFAULT_WORK_MINUTES,
            focusRestMinutes = prefs[keys.focusRestMinutes] ?: DEFAULT_REST_MINUTES,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[keys.themeMode] = mode.name }
    suspend fun setViewMode(mode: CalendarViewMode) = edit { it[keys.viewMode] = mode.name }
    suspend fun setClockStyle(style: MonthClockStyle) = edit { it[keys.clockStyle] = style.name }
    suspend fun setColorScheme(scheme: MonthColorScheme) = edit { it[keys.colorScheme] = scheme.name }
    suspend fun setShowLunar(value: Boolean) = edit { it[keys.showLunar] = value }
    suspend fun setShowSolarTerm(value: Boolean) = edit { it[keys.showSolarTerm] = value }
    suspend fun setShowHoliday(value: Boolean) = edit { it[keys.showHoliday] = value }
    suspend fun setFirstDayOfWeek(day: DayOfWeek) = edit { it[keys.firstDayOfWeek] = day.value }
    suspend fun setFocusMinutes(work: Int, rest: Int) = edit {
        it[keys.focusWorkMinutes] = work
        it[keys.focusRestMinutes] = rest
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    companion object {
        const val DEFAULT_WORK_MINUTES = 25
        const val DEFAULT_REST_MINUTES = 5
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val clockStyle: MonthClockStyle = MonthClockStyle.LUNAR_SUB,
    val colorScheme: MonthColorScheme = MonthColorScheme.DEFAULT,
    val showLunar: Boolean = true,
    val showSolarTerm: Boolean = true,
    val showHoliday: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val focusWorkMinutes: Int = SettingsStore.DEFAULT_WORK_MINUTES,
    val focusRestMinutes: Int = SettingsStore.DEFAULT_REST_MINUTES,
) {
    val contentFlags = contentFlagsOf(showLunar, showSolarTerm, showHoliday)
}

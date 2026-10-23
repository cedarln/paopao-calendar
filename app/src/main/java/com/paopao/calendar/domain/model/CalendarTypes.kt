package com.paopao.calendar.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/** 日历的三种主体视图，对应设置面板里的「内容」轴。 */
enum class CalendarViewMode {
    MONTH, WEEK, DAY
}

/**
 * 月视图外观轴 —— 决定日期数字之外还显示什么。
 *
 * 拆成三个独立可配轴（外观/颜色/内容）而不是一个大开关：一个开关组合会爆炸。
 */
enum class MonthClockStyle {
    /** 纯数字 */
    PLAIN,

    /** 数字 + 农历/节气 */
    LUNAR_SUB,

    /** 圆点标记事件密度 */
    DENSITY_DOT,

    /** 大字居中，适合平板与大屏插件 */
    LARGE,
}

enum class MonthColorScheme {
    DEFAULT,
    PASTEL,
    HIGH_CONTRAST,
    MONOCHROME,
}

enum class MonthContentFlags {
    SHOW_LUNAR,
    SHOW_SOLAR_TERM,
    SHOW_HOLIDAY_BADGE,
    SHOW_LUNAR_DAY_IN_SUB,
}

/**
 * 月视图的一个格子。跨月补位也用同一个类型，靠 [inCurrentMonth] 区分。
 */
data class DayCell(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val lunarLabel: String,
    val solarTermLabel: String?,
    val holidayLabel: String?,
    val isWeekend: Boolean,
    val isToday: Boolean,
    val festivalLabel: String?,
) {
    /** 主标签：节假日 > 节气 > 农历，只显示一个，否则格子会挤。 */
    val subLabel: String
        get() = holidayLabel ?: festivalLabel ?: solarTermLabel ?: lunarLabel

    companion object {
        fun isWeekendOf(date: LocalDate): Boolean =
            date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    }
}

/** 月视图网格，固定 6 行以容纳最长跨月组合（如 1 月 31 天 + 周六起始）。 */
data class MonthGrid(
    val month: LocalDate,
    val weeks: List<List<DayCell>>,
) {
    val cells: List<DayCell> get() = weeks.flatten()
}

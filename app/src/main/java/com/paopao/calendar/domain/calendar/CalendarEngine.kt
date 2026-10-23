package com.paopao.calendar.domain.calendar

import com.paopao.calendar.domain.model.DayCell
import com.paopao.calendar.domain.model.MonthColorScheme
import com.paopao.calendar.domain.model.MonthContentFlags
import com.paopao.calendar.domain.model.MonthGrid
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 日历网格生成。纯函数、无 Android 依赖，所以可以在 JVM 上直接单测。
 *
 * 月视图固定 6 行：月首日为周六且该月 31 天时，5 行会差一格
 * （如 2026 年 10 月 1 日是周四、10 月 31 日周六，跨到 11 月 7 日 = 第 6 行）。
 * 用不固定行数会让月视图高度在滑动时跳动，这是日历类应用最常见的体验问题。
 */
class CalendarEngine(
    private val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    private val holidays: HolidayResolver = StatutoryHolidayResolver(),
) {

    fun monthGrid(month: LocalDate, today: LocalDate, flags: Set<MonthContentFlags>): MonthGrid {
        val firstOfMonth = month.withDayOfMonth(1)
        val gridStart = backToWeekStart(firstOfMonth)

        val cells = ArrayList<DayCell>(42)
        var cursor = gridStart
        repeat(42) {
            cells += cellOf(cursor, firstOfMonth.month == cursor.month &&
                firstOfMonth.year == cursor.year, today, flags)
            cursor = cursor.plusDays(1)
        }
        return MonthGrid(
            month = firstOfMonth,
            weeks = cells.chunked(7),
        )
    }

    /** 周视图：锚点 [date] 所在周的 7 格。 */
    fun weekGrid(date: LocalDate, today: LocalDate, flags: Set<MonthContentFlags>): List<DayCell> {
        val start = backToWeekStart(date)
        return (0L until 7L).map { offset ->
            val day = start.plusDays(offset)
            cellOf(day, inCurrentMonth = true, today = today, flags = flags)
        }
    }

    /** 日视图：单日详情，跨月标记恒为 true，因为这时有且只有它自己。 */
    fun dayCell(date: LocalDate, today: LocalDate, flags: Set<MonthContentFlags>): DayCell =
        cellOf(date, inCurrentMonth = true, today = today, flags = flags)

    private fun cellOf(
        date: LocalDate,
        inCurrentMonth: Boolean,
        today: LocalDate,
        flags: Set<MonthContentFlags>,
    ): DayCell {
        val info = ChineseCalendar.infoOf(date)
        val holiday = holidays.holidayOn(date)
        return DayCell(
            date = date,
            inCurrentMonth = inCurrentMonth,
            lunarLabel = info.shortLunarLabel,
            solarTermLabel = info.solarTerm,
            holidayLabel = holiday,
            isWeekend = ChineseCalendar.isWeekend(date),
            isToday = date == today,
            festivalLabel = (info.solarFestivals + info.lunarFestivals).firstOrNull(),
        )
    }

    private fun backToWeekStart(date: LocalDate): LocalDate {
        val delta = (date.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        return date.minusDays(delta.toLong())
    }
}

/**
 * 月视图三轴配置 → 实际显示位图。
 *
 * 拆成集合而不是枚举笛卡尔积：外观、颜色、内容三个轴独立生效，
 * 组合起来 4×4×N 的枚举会在每次加一档时指数膨胀。
 */
fun MonthColorScheme.isHighContrast(): Boolean = this == MonthColorScheme.HIGH_CONTRAST

fun contentFlagsOf(showLunar: Boolean, showSolarTerm: Boolean, showHoliday: Boolean):
    Set<MonthContentFlags> = buildSet {
    if (showLunar) add(MonthContentFlags.SHOW_LUNAR)
    if (showSolarTerm) add(MonthContentFlags.SHOW_SOLAR_TERM)
    if (showHoliday) add(MonthContentFlags.SHOW_HOLIDAY_BADGE)
}

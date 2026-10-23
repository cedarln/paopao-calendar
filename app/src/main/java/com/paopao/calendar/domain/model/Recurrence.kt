package com.paopao.calendar.domain.model

import com.paopao.calendar.domain.calendar.ChineseCalendar
import com.paopao.calendar.domain.calendar.lunarDayNumeric
import com.paopao.calendar.domain.calendar.lunarMonthNumeric
import java.time.LocalDate

/**
 * 重复规则。
 *
 * 不直接依赖 RRULE 字符串：初版只需要中文日历常见的几档，
 * 自绘一套语义再在导入/导出系统日历时映射到 RRULE，比反过来解析简单。
 */
sealed interface Recurrence {

    data object None : Recurrence

    data class Weekly(val days: DayOfWeekMask) : Recurrence

    data class Monthly(val dayOfMonth: Int) : Recurrence

    /** 按农历重复：用于生日、纪念日这类「今年又是什么时候」的场景。 */
    data class LunarYearly(val lunarMonth: Int, val lunarDay: Int, val leapFallback: Boolean) : Recurrence

    data object Yearly : Recurrence
}

/**
 * 星期掩码，bit0 = 周一。用位掩码而非 Set<DayOfWeek> 是为了能原样存进一列 Int。
 */
@JvmInline
value class DayOfWeekMask(val bits: Int) {

    val isSet: Boolean get() = bits != 0

    fun contains(day: java.time.DayOfWeek): Boolean =
        (bits and (1 shl (day.value - 1))) != 0

    fun plus(day: java.time.DayOfWeek): DayOfWeekMask =
        DayOfWeekMask(bits or (1 shl (day.value - 1)))

    fun minus(day: java.time.DayOfWeek): DayOfWeekMask =
        DayOfWeekMask(bits and (1 shl (day.value - 1)).inv())

    companion object {
        val NONE = DayOfWeekMask(0)
        val ALL = DayOfWeekMask(0b111_1111)
        val WORKDAYS = DayOfWeekMask(0b001_1111)
        val WEEKEND = DayOfWeekMask(0b110_0000)

        fun of(vararg days: java.time.DayOfWeek): DayOfWeekMask =
            days.fold(NONE) { acc, d -> acc.plus(d) }
    }
}

/**
 * 判断某天是否命中重复规则。
 *
 * 只处理「从 [anchor] 开始」的展开，避免为了一个无限规则把整张表搬进内存。
 */
fun Recurrence.occursOn(date: LocalDate, anchor: LocalDate): Boolean {
    if (date.isBefore(anchor)) return false
    return when (this) {
        Recurrence.None -> date == anchor
        is Recurrence.Weekly -> days.contains(date.dayOfWeek)
        is Recurrence.Monthly -> date.dayOfMonth == dayOfMonth
        is Recurrence.LunarYearly -> {
            val info = ChineseCalendar.infoOf(date)
            info.lunarMonthNumeric == lunarMonth &&
                info.lunarDayNumeric == lunarDay &&
                (!info.isLeapMonth || leapFallback)
        }
        Recurrence.Yearly -> date.month == anchor.month && date.dayOfMonth == anchor.dayOfMonth
    }
}

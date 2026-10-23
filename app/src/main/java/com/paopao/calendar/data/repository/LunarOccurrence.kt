package com.paopao.calendar.data.repository

import com.paopao.calendar.data.local.entity.MemorialEntity
import com.paopao.calendar.domain.calendar.ChineseCalendar
import com.paopao.calendar.domain.calendar.lunarDayNumeric
import com.paopao.calendar.domain.calendar.lunarMonthNumeric
import java.time.LocalDate
import java.time.YearMonth

/**
 * 农历纪念日的公历落点。
 *
 * 「农历八月十五」在公历里每年都不是同一天，所以按农历重复的纪念日必须在目标年
 * 反查一次。反查窗口取 400 天：一个农历年最长 13 个朔望月 ≈ 384 天，400 天有余量。
 */
object LunarOccurrence {

    fun dateFor(memorial: MemorialEntity, gregorianYear: Int): LocalDate? {
        val anchor = LocalDate.ofEpochDay(memorial.anchorEpochDay)
        val anchorLunar = ChineseCalendar.infoOf(anchor)
        val targetMonth = anchorLunar.lunarMonthNumeric
        val targetDay = anchorLunar.lunarDayNumeric
        if (targetMonth == 0 || targetDay == 0) return null

        val spring = ChineseCalendar.springFestivalOf(gregorianYear) ?: return null
        var cursor = spring.minusDays(30)
        repeat(430) {
            val info = ChineseCalendar.infoOf(cursor)
            val hit = !info.isLeapMonth &&
                info.lunarMonthNumeric == targetMonth &&
                info.lunarDayNumeric == targetDay &&
                cursor.year == gregorianYear
            if (hit) return cursor
            cursor = cursor.plusDays(1)
        }
        return null
    }
}

/**
 * 公历纪念日的周年落点。
 *
 * 2 月 29 日出生的人在平年怎么办，是唯一需要决定的地方。这里选 2 月 28 日 ——
 * 与《民法典》对「按周年计算期间」的处理一致，也比提前到 2 月 28 日的对立意见保守。
 */
fun solarAnniversary(anchor: LocalDate, year: Int): LocalDate {
    val safeDay = minOf(anchor.dayOfMonth, YearMonth.of(year, anchor.monthValue).lengthOfMonth())
    return LocalDate.of(year, anchor.monthValue, safeDay)
}

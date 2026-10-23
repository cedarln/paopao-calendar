package com.paopao.calendar.domain.calendar

import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 一天上的农历/节气/节日信息。
 *
 * 这一层把第三方农历库挡住，UI 只认 [ChineseDayInfo]：将来换库或改成自带数据表，
 * 影响面止于本文件。
 *
 * 农历本身委托给 `cn.6tail:lunar`。自己算过一版定朔+定气的实现，27 个已知春节日期的
 * 回归测试里错了 7 个，全部偏一天 —— 朔的计算误差到了 0.5 天量级。日历应用里
 * 「农历写错一天」是硬伤，所以这里用被广泛验证过的库，并把回归测试留在
 * [ChineseCalendarTest] 里，依赖升级时能立刻发现退化。
 */
data class ChineseDayInfo(
    val date: LocalDate,
    val lunarMonthName: String,
    val lunarDayName: String,
    val isLeapMonth: Boolean,
    val yearGanZhi: String,
    val zodiac: String,
    val solarTerm: String?,
    val lunarFestivals: List<String>,
    val solarFestivals: List<String>,
) {

    /** 格子底部的短标签：闰月要让位，否则「闰五初五」这种会误导。 */
    val shortLunarLabel: String
        get() = if (lunarDayName == "初一") lunarMonthName + "月" else lunarDayName

    /**
     * 月视图副标签的取值优先级：节日 > 节气 > 农历。
     * 一个格子放两行已经够挤，三个都显示就没人看得清。
     */
    fun subLabel(showLunar: Boolean, showSolarTerm: Boolean, showHolidayBadge: Boolean): String {
        if (showHolidayBadge) {
            solarFestivals.firstOrNull()?.let { return it }
            lunarFestivals.firstOrNull()?.let { return it }
        }
        if (showSolarTerm) solarTerm?.let { return it }
        return if (showLunar) shortLunarLabel else ""
    }

    companion object {
        val EMPTY = ChineseDayInfo(
            date = LocalDate.EPOCH,
            lunarMonthName = "",
            lunarDayName = "",
            isLeapMonth = false,
            yearGanZhi = "",
            zodiac = "",
            solarTerm = null,
            lunarFestivals = emptyList(),
            solarFestivals = emptyList(),
        )
    }
}

/**
 * 中国历法查询入口。
 *
 * [infoOf] 带 LRU 缓存：月视图一次要 42 个格子，且横滑时同一批日期会被反复请求，
 * 每次都新建 Solar/Lunar 对象在低端机上足以掉帧。
 */
object ChineseCalendar {

    private val cache = object : LinkedHashMap<LocalDate, ChineseDayInfo>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalDate, ChineseDayInfo>) =
            size > MAX_CACHE
    }

    private const val MAX_CACHE = 800

    fun infoOf(date: LocalDate): ChineseDayInfo = synchronized(cache) {
        cache.getOrPut(date) { compute(date) }
    }

    private fun compute(date: LocalDate): ChineseDayInfo {
        val solar: Solar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth)
        val lunar: Lunar = solar.getLunar()

        val monthName = lunar.getMonthInChinese()
        val isLeap = monthName.startsWith(LEAP_PREFIX)

        return ChineseDayInfo(
            date = date,
            lunarMonthName = if (isLeap) monthName.removePrefix(LEAP_PREFIX) else monthName,
            lunarDayName = lunar.getDayInChinese(),
            isLeapMonth = isLeap,
            yearGanZhi = lunar.getYearInGanZhi(),
            zodiac = lunar.getYearShengXiao(),
            solarTerm = lunar.getJieQi().takeIf { it.isNotEmpty() },
            lunarFestivals = lunar.getFestivals(),
            solarFestivals = solar.getFestivals(),
        )
    }

    /**
     * [year] 农历年的春节（正月初一）公历日期。
     *
     * 在 1 月 1 日起的 80 天里找第一个「正月且非闰月且初一」：
     * 春节最晚落在 2 月下旬，80 天窗口足够且远小于半年。
     */
    fun springFestivalOf(year: Int): LocalDate? {
        var cursor = LocalDate.of(year, 1, 1)
        repeat(80) {
            val solar = Solar.fromYmd(cursor.year, cursor.monthValue, cursor.dayOfMonth)
            val lunar = solar.getLunar()
            if (lunar.getMonth() == 1 &&
                !lunar.getMonthInChinese().startsWith(LEAP_PREFIX) &&
                lunar.getDay() == 1
            ) {
                return cursor
            }
            cursor = cursor.plusDays(1)
        }
        return null
    }

    /** 该农历年的除夕（腊月的最后一天），用于「除夕」这个比春节更被在意的日子。 */
    fun eveOfSpringFestival(year: Int): LocalDate? =
        springFestivalOf(year)?.minusDays(1)

    fun isWeekend(date: LocalDate): Boolean {
        val dow = date.dayOfWeek
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
    }

    private const val LEAP_PREFIX = "闰"
}

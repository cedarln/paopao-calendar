package com.paopao.calendar.domain.calendar

import java.time.LocalDate

/**
 * 法定节假日的日期推导。
 *
 * 只生成「规则可推导」的部分：公历固定节日 + 由农历/节气确定的节日。
 * 这三类是历法事实，可以算准。
 *
 * 刻意**不做**的一件事是把「调休补班」写死进代码：放假安排由国务院办公厅每年 11 月
 * 左右发布，任何硬编码的调休表都会在下一年变成错误信息。日历上把要上班的日子标成假，
 * 比不标更糟。[WorkdayAdjustmentSource] 因此留成可替换的数据源，初版返回空，
 * 后续接一个每年更新的数据文件即可，不需要改这里的逻辑。
 */
interface HolidayResolver {
    /** 返回该日的法定节日名，或 null。 */
    fun holidayOn(date: LocalDate): String?

    /** 该日是否为法定假期（放假）。 */
    fun isHoliday(date: LocalDate): Boolean

    /** 该日是否为调休补班日。 */
    fun isMakeupWorkday(date: LocalDate): Boolean
}

/** 法定节日与具体日期的映射，按年缓存。 */
class StatutoryHolidayResolver(
    private val workdayAdjustments: WorkdayAdjustmentSource = EmptyWorkdayAdjustmentSource,
) : HolidayResolver {

    private val yearCache = HashMap<Int, Map<LocalDate, String>>()

    override fun holidayOn(date: LocalDate): String? =
        holidaysOfYear(date.year)[date]

    override fun isHoliday(date: LocalDate): Boolean =
        holidaysOfYear(date.year).containsKey(date) ||
            (ChineseCalendar.isWeekend(date) && !workdayAdjustments.isMakeupWorkday(date))

    override fun isMakeupWorkday(date: LocalDate): Boolean =
        workdayAdjustments.isMakeupWorkday(date)

    /**
     * 某年的全部法定节日。
     *
     * 依《全国年节及纪念日放假办法》：春节 4 天（除夕 + 正月初一至初三，2025 年修订后
     * 把除夕纳入法定假），国庆 3 天（10 月 1—3 日），劳动节 2 天（5 月 1—2 日），
     * 元旦/清明/端午/中秋各 1 天。
     */
    fun holidaysOfYear(year: Int): Map<LocalDate, String> = yearCache.getOrPut(year) {
        val map = LinkedHashMap<LocalDate, String>()

        map[LocalDate.of(year, 1, 1)] = "元旦"

        val spring = ChineseCalendar.springFestivalOf(year)
        val eve = ChineseCalendar.eveOfSpringFestival(year)
        if (eve != null && eve.year == year) map[eve] = "除夕"
        spring?.let { repeat(3) { i -> map[it.plusDays(i.toLong())] = "春节" } }

        findSolarTermDate(year, SOLAR_TERM_QINGMING)?.let { map[it] = "清明节" }

        // 2025 年修订后劳动节为 2 天
        map[LocalDate.of(year, 5, 1)] = "劳动节"
        map[LocalDate.of(year, 5, 2)] = "劳动节"

        map[LocalDate.of(year, 10, 1)] = "国庆节"
        map[LocalDate.of(year, 10, 2)] = "国庆节"
        map[LocalDate.of(year, 10, 3)] = "国庆节"

        lunarDateOf(year, 5, 5)?.let { map[it] = "端午节" }
        lunarDateOf(year, 8, 15)?.let { map[it] = "中秋节" }

        map
    }

    /** 农历 [lunarMonth] 月 [lunarDay] 日 落在 [year] 农历年内的公历日期。 */
    private fun lunarDateOf(year: Int, lunarMonth: Int, lunarDay: Int): LocalDate? {
        val anchor = ChineseCalendar.springFestivalOf(year) ?: return null
        // 从正月初一起最多走 13 个朔望月
        var cursor = anchor
        repeat(390) {
            val info = ChineseCalendar.infoOf(cursor)
            if (!info.isLeapMonth && info.lunarMonthNumeric == lunarMonth && info.lunarDayNumeric == lunarDay) {
                return cursor
            }
            cursor = cursor.plusDays(1)
        }
        return null
    }

    private fun findSolarTermDate(year: Int, term: String): LocalDate? {
        // 清明固定在 4 月 4—6 日，扫这一周比扫全年便宜
        var cursor = LocalDate.of(year, 4, 3)
        repeat(5) {
            if (ChineseCalendar.infoOf(cursor).solarTerm == term) return cursor
            cursor = cursor.plusDays(1)
        }
        return null
    }

    companion object {
        const val SOLAR_TERM_QINGMING = "清明"
    }
}

/** 调休补班数据源，留给每年一次的官方安排更新。 */
interface WorkdayAdjustmentSource {
    fun isMakeupWorkday(date: LocalDate): Boolean
}

object EmptyWorkdayAdjustmentSource : WorkdayAdjustmentSource {
    override fun isMakeupWorkday(date: LocalDate): Boolean = false
}

/**
 * 农历月/日的数值形式。字符串名（「正」「廿九」）适合展示，
 * 但按数字匹配节日时字符串会把「十五」和「五十」这类同形情况搞得很难读。
 */
val ChineseDayInfo.lunarMonthNumeric: Int
    get() = LUNAR_MONTH_NAMES.indexOf(lunarMonthName) + 1

val ChineseDayInfo.lunarDayNumeric: Int
    get() = LUNAR_DAY_NAMES.indexOf(lunarDayName) + 1

private val LUNAR_MONTH_NAMES = listOf(
    "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊",
)

private val LUNAR_DAY_NAMES = buildList {
    addAll(listOf("初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十"))
    addAll(listOf("十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十"))
    addAll(listOf("廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"))
}

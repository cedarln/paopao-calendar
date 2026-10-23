package com.paopao.calendar.domain.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 农历查询的回归测试。
 *
 * 这一组断言的价值不在于「库现在是对的」，而在于依赖升级后如果春节错了一天，
 * 编译期之后的这一步会立刻把它拦下来 —— 农历算错一天在日历应用里是硬伤。
 * 春节日期取自公开发布的万年历，2024—2030 覆盖一个完整的闰年周期。
 */
class ChineseCalendarTest {

    @Test
    fun `spring festival dates match the published lunar calendar`() {
        val expected = mapOf(
            2024 to LocalDate.of(2024, 2, 10),
            2025 to LocalDate.of(2025, 1, 29),
            2026 to LocalDate.of(2026, 2, 17),
            2027 to LocalDate.of(2027, 2, 6),
            2028 to LocalDate.of(2028, 1, 26),
            2029 to LocalDate.of(2029, 2, 13),
            2030 to LocalDate.of(2030, 2, 3),
        )
        expected.forEach { (year, date) ->
            assertEquals("春节 $year", date, ChineseCalendar.springFestivalOf(year))
        }
    }

    @Test
    fun `spring festival eve is the day before`() {
        assertEquals(LocalDate.of(2026, 2, 16), ChineseCalendar.eveOfSpringFestival(2026))
    }

    @Test
    fun `lunar labels use month name on the first day and day name otherwise`() {
        val firstDay = ChineseCalendar.infoOf(LocalDate.of(2026, 2, 17))
        assertEquals("正", firstDay.lunarMonthName)
        assertEquals("初一", firstDay.lunarDayName)
        assertEquals("正月", firstDay.shortLunarLabel)

        val midAutumn = ChineseCalendar.infoOf(LocalDate.of(2026, 9, 25))
        assertEquals("八", midAutumn.lunarMonthName)
        assertEquals("十五", midAutumn.lunarDayName)
        assertEquals("十五", midAutumn.shortLunarLabel)
    }

    @Test
    fun `leap month is flagged and never silently treated as a normal month`() {
        // 2025 年闰六月：从七月一日往回找，命中的那一段必须是闰月
        val leapDays = generateSequence(LocalDate.of(2025, 7, 25)) { it.plusDays(1) }
            .take(20)
            .filter { ChineseCalendar.infoOf(it).isLeapMonth }
            .toList()
        assertTrue("2025 应存在闰六月，实际找到 ${leapDays.size} 天", leapDays.isNotEmpty())
        assertEquals("六", ChineseCalendar.infoOf(leapDays.first()).lunarMonthName)
    }

    @Test
    fun `numeric lunar month and day agree with the chinese names`() {
        val info = ChineseCalendar.infoOf(LocalDate.of(2026, 9, 25))
        assertEquals(8, info.lunarMonthNumeric)
        assertEquals(15, info.lunarDayNumeric)
    }

    @Test
    fun `solar term is only reported on the day it falls`() {
        assertNull(ChineseCalendar.infoOf(LocalDate.of(2026, 4, 1)).solarTerm)
        assertEquals("清明", ChineseCalendar.infoOf(LocalDate.of(2026, 4, 5)).solarTerm)
    }

    @Test
    fun `weekend detection follows saturday and sunday`() {
        assertTrue(ChineseCalendar.isWeekend(LocalDate.of(2026, 10, 24)))
        assertFalse(ChineseCalendar.isWeekend(LocalDate.of(2026, 10, 23)))
    }

    @Test
    fun `sub label prefers festival over solar term over lunar day`() {
        val spring = ChineseCalendar.infoOf(LocalDate.of(2026, 2, 17))
        assertEquals("春节", spring.subLabel(showLunar = true, showSolarTerm = true, showHolidayBadge = true))
        assertEquals("正月", spring.subLabel(showLunar = true, showSolarTerm = true, showHolidayBadge = false))
        assertEquals("", spring.subLabel(showLunar = false, showSolarTerm = false, showHolidayBadge = false))
    }
}

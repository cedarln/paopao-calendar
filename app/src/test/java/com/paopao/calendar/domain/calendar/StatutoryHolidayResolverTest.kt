package com.paopao.calendar.domain.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 法定节假日推导。
 *
 * 只断言「规则可推导」的部分。调休补班不在断言范围内 —— 那一版安排由官方每年发布，
 * 写进测试就等于把它冻结在某一年的错误上。
 */
class StatutoryHolidayResolverTest {

    private val resolver = StatutoryHolidayResolver()

    @Test
    fun `2026 fixed-date holidays`() {
        val holidays = resolver.holidaysOfYear(2026)
        assertEquals("元旦", holidays[LocalDate.of(2026, 1, 1)])
        assertEquals("劳动节", holidays[LocalDate.of(2026, 5, 1)])
        assertEquals("劳动节", holidays[LocalDate.of(2026, 5, 2)])
        assertEquals("国庆节", holidays[LocalDate.of(2026, 10, 1)])
        assertEquals("国庆节", holidays[LocalDate.of(2026, 10, 3)])
        assertNull(holidays[LocalDate.of(2026, 10, 4)])
    }

    @Test
    fun `spring festival covers the eve and the first three days`() {
        val holidays = resolver.holidaysOfYear(2026)
        assertEquals("除夕", holidays[LocalDate.of(2026, 2, 16)])
        listOf(17, 18, 19).forEach { day ->
            assertEquals("春节", holidays[LocalDate.of(2026, 2, day)])
        }
        assertNull(holidays[LocalDate.of(2026, 2, 20)])
    }

    @Test
    fun `lunar and solar-term holidays land where the calendar says`() {
        val holidays = resolver.holidaysOfYear(2026)
        assertEquals("清明节", holidays[LocalDate.of(2026, 4, 5)])
        assertEquals("端午节", holidays[LocalDate.of(2026, 6, 19)])
        assertEquals("中秋节", holidays[LocalDate.of(2026, 9, 25)])
    }

    @Test
    fun `holiday lookup is the same map the year cache builds`() {
        assertEquals("春节", resolver.holidayOn(LocalDate.of(2026, 2, 17)))
        assertNull(resolver.holidayOn(LocalDate.of(2026, 10, 24)))
    }

    @Test
    fun `weekends count as rest days and ordinary workdays do not`() {
        assertTrue(resolver.isHoliday(LocalDate.of(2026, 10, 25)))  // 周六
        assertFalse(resolver.isHoliday(LocalDate.of(2026, 10, 23))) // 周五，且不是节日
    }

    @Test
    fun `makeup workday source is empty by default`() {
        assertFalse(resolver.isMakeupWorkday(LocalDate.of(2026, 10, 10)))
    }
}

package com.paopao.calendar.data.repository

import com.paopao.calendar.data.local.entity.MemorialEntity
import com.paopao.calendar.domain.calendar.ChineseCalendar
import com.paopao.calendar.domain.calendar.lunarDayNumeric
import com.paopao.calendar.domain.calendar.lunarMonthNumeric
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 农历纪念日的公历落点与公历周年的闰日处理。 */
class LunarOccurrenceTest {

    @Test
    fun `the same lunar date lands on a different solar day each year`() {
        val midAutumn = memorial(LocalDate.of(2026, 9, 25), isLunar = true) // 八月十五
        assertEquals(LocalDate.of(2026, 9, 25), LunarOccurrence.dateFor(midAutumn, 2026))

        val nextYear = LunarOccurrence.dateFor(midAutumn, 2027)
        assertNotNull(nextYear)
        assertTrue("八月十五不会还落在公历同一天", nextYear != LocalDate.of(2027, 9, 25))
        assertEquals(8, nextYear!!.lunarMonthNumericOf())
        assertEquals(15, nextYear.lunarDayNumericOf())
    }

    @Test
    fun `the resolved date really belongs to the requested gregorian year`() {
        val birthday = memorial(LocalDate.of(2026, 2, 17), isLunar = true) // 正月初一
        (2024..2030).forEach { year ->
            val date = LunarOccurrence.dateFor(birthday, year)
            assertNotNull("$year 应该能算出正月初一的落点", date)
            assertEquals(year, date!!.year)
            assertEquals(1, date.lunarMonthNumericOf())
            assertEquals(1, date.lunarDayNumericOf())
        }
    }

    @Test
    fun `a solar anniversary stays on the same month and day`() {
        assertEquals(LocalDate.of(2030, 3, 14), solarAnniversary(LocalDate.of(2000, 3, 14), 2030))
    }

    @Test
    fun `february 29 anniversaries fall back to february 28 in common years`() {
        val leapBirthday = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDate.of(2026, 2, 28), solarAnniversary(leapBirthday, 2026))
        assertEquals(LocalDate.of(2028, 2, 29), solarAnniversary(leapBirthday, 2028))
    }

    private fun LocalDate.lunarMonthNumericOf() = ChineseCalendar.infoOf(this).lunarMonthNumeric

    private fun LocalDate.lunarDayNumericOf() = ChineseCalendar.infoOf(this).lunarDayNumeric

    private fun memorial(anchor: LocalDate, isLunar: Boolean) = MemorialEntity(
        name = "测试纪念日",
        anchorEpochDay = anchor.toEpochDay(),
        isLunar = isLunar,
        repeatYearly = true,
        colorArgb = 0,
        iconKey = "memorial",
    )
}

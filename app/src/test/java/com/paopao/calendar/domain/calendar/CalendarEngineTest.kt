package com.paopao.calendar.domain.calendar

import com.paopao.calendar.domain.model.MonthContentFlags
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网格生成的形状约定。
 *
 * 固定 42 格是界面不跳动的前提，所以这一组断言盯的是「行数永远是 6」而不是
 * 「这个月刚好排得下」。
 */
class CalendarEngineTest {

    private val engine = CalendarEngine(firstDayOfWeek = DayOfWeek.MONDAY)
    private val today = LocalDate.of(2026, 10, 24)

    @Test
    fun `month grid is always six weeks long`() {
        listOf(
            LocalDate.of(2026, 2, 1),   // 28 天的最短月
            LocalDate.of(2026, 10, 1),  // 首日周四、末日周六
            LocalDate.of(2027, 1, 1),   // 首日周五、31 天
        ).forEach { month ->
            val grid = engine.monthGrid(month, today, emptySet())
            assertEquals("$month 应有 6 行", 6, grid.weeks.size)
            assertEquals("$month 应有 42 格", 42, grid.cells.size)
            grid.weeks.forEach { week -> assertEquals(7, week.size) }
        }
    }

    @Test
    fun `grid starts on the configured first weekday and covers the whole month`() {
        val grid = engine.monthGrid(LocalDate.of(2026, 10, 1), today, emptySet())
        assertEquals(LocalDate.of(2026, 9, 28), grid.cells.first().date)
        assertEquals(DayOfWeek.MONDAY, grid.cells.first().date.dayOfWeek)
        assertEquals(31, grid.cells.count { it.inCurrentMonth })
        assertTrue(grid.cells.first().date.isBefore(LocalDate.of(2026, 10, 1)))
        assertFalse(grid.cells.last().date.isBefore(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun `first day of week reshapes the grid without changing its size`() {
        val sundayFirst = CalendarEngine(firstDayOfWeek = DayOfWeek.SUNDAY)
        val grid = sundayFirst.monthGrid(LocalDate.of(2026, 10, 1), today, emptySet())
        assertEquals(LocalDate.of(2026, 9, 27), grid.cells.first().date)
        assertEquals(42, grid.cells.size)
    }

    @Test
    fun `week grid is the selected week and never trims out-of-month days`() {
        val week = engine.weekGrid(LocalDate.of(2026, 10, 24), today, emptySet())
        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 10, 19), week.first().date)
        assertEquals(LocalDate.of(2026, 10, 25), week.last().date)
        // 周视图里每天都「属于当前视图」，所以补位不该被淡显
        assertTrue(week.all { it.inCurrentMonth })
    }

    @Test
    fun `today marker only lands on today`() {
        val grid = engine.monthGrid(LocalDate.of(2026, 10, 1), today, emptySet())
        assertEquals(1, grid.cells.count { it.isToday })
        assertEquals(today, grid.cells.single { it.isToday }.date)
    }

    @Test
    fun `day cell is standalone and marked as current`() {
        val cell = engine.dayCell(LocalDate.of(2026, 2, 17), today, emptySet())
        assertTrue(cell.inCurrentMonth)
        assertEquals("正月", cell.lunarLabel)
    }

    @Test
    fun `content flags are the union of three switches`() {
        assertEquals(
            setOf(MonthContentFlags.SHOW_LUNAR, MonthContentFlags.SHOW_HOLIDAY_BADGE),
            contentFlagsOf(showLunar = true, showSolarTerm = false, showHoliday = true),
        )
        assertTrue(contentFlagsOf(false, false, false).isEmpty())
    }

    @Test
    fun `holiday label comes from the resolver and is null on ordinary days`() {
        val grid = engine.monthGrid(LocalDate.of(2026, 2, 1), today, emptySet())
        val spring = grid.cells.single { it.date == LocalDate.of(2026, 2, 17) }
        assertEquals("春节", spring.holidayLabel)
        assertEquals(
            null,
            grid.cells.single { it.date == LocalDate.of(2026, 2, 20) }.holidayLabel,
        )
    }
}

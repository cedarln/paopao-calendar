package com.paopao.calendar.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 重复规则的编解码与命中判断。 */
class RecurrenceCodecTest {

    @Test
    fun `every rule survives a round trip`() {
        val rules = listOf(
            Recurrence.None,
            Recurrence.Weekly(DayOfWeekMask.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            Recurrence.Weekly(DayOfWeekMask.ALL),
            Recurrence.Monthly(31),
            Recurrence.Yearly,
            Recurrence.LunarYearly(lunarMonth = 8, lunarDay = 15, leapFallback = true),
            Recurrence.LunarYearly(lunarMonth = 12, lunarDay = 8, leapFallback = false),
        )
        rules.forEach { rule ->
            assertEquals(rule, RecurrenceCodec.decode(RecurrenceCodec.encode(rule)))
        }
    }

    @Test
    fun `unreadable input degrades to none instead of throwing`() {
        listOf(null, "", "   ", "weekly", "weekly:abc", "unknown:1", "monthly:0").forEach { raw ->
            assertEquals("「$raw」应退回 None", Recurrence.None, RecurrenceCodec.decode(raw))
        }
    }

    @Test
    fun `nothing happens before the anchor`() {
        val anchor = LocalDate.of(2026, 10, 19)
        assertFalse(Recurrence.Weekly(DayOfWeekMask.ALL).occursOn(anchor.minusDays(1), anchor))
        assertFalse(Recurrence.Yearly.occursOn(LocalDate.of(2025, 10, 19), anchor))
    }

    @Test
    fun `weekly matches the masked weekdays only`() {
        val anchor = LocalDate.of(2026, 10, 19) // 周一
        val monAndThu = Recurrence.Weekly(DayOfWeekMask.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        assertTrue(monAndThu.occursOn(anchor, anchor))
        assertTrue(monAndThu.occursOn(LocalDate.of(2026, 10, 22), anchor))
        assertFalse(monAndThu.occursOn(LocalDate.of(2026, 10, 20), anchor))
    }

    @Test
    fun `monthly on the 31st skips months without one`() {
        val anchor = LocalDate.of(2026, 10, 31)
        val rule = Recurrence.Monthly(31)
        assertTrue(rule.occursOn(LocalDate.of(2026, 12, 31), anchor))
        assertFalse(rule.occursOn(LocalDate.of(2027, 2, 28), anchor))
    }

    @Test
    fun `yearly on the leap day only matches in leap years`() {
        val anchor = LocalDate.of(2024, 2, 29)
        assertTrue(Recurrence.Yearly.occursOn(LocalDate.of(2028, 2, 29), anchor))
        assertFalse(Recurrence.Yearly.occursOn(LocalDate.of(2027, 2, 28), anchor))
    }

    @Test
    fun `lunar yearly follows the lunar date not the solar one`() {
        val anchor = LocalDate.of(2026, 9, 25) // 丙午年八月十五
        val rule = Recurrence.LunarYearly(lunarMonth = 8, lunarDay = 15, leapFallback = true)
        assertTrue(rule.occursOn(anchor, anchor))
        assertFalse(rule.occursOn(LocalDate.of(2026, 9, 26), anchor))
        assertEquals(LocalDate.of(2027, 9, 15), nextLunarOccurrence(rule, anchor))
    }

    private fun nextLunarOccurrence(rule: Recurrence.LunarYearly, after: LocalDate): LocalDate {
        var cursor = after.plusDays(1)
        while (!rule.occursOn(cursor, after)) cursor = cursor.plusDays(1)
        return cursor
    }
}

/** 四象限派生：界面只存两个轴，象限是算出来的。 */
class QuadrantTest {

    @Test
    fun `the four corners map to themselves`() {
        assertEquals(Quadrant.Q1, Quadrant.derive(Importance.CRITICAL, Urgency.NOW))
        assertEquals(Quadrant.Q2, Quadrant.derive(Importance.CRITICAL, Urgency.LATER))
        assertEquals(Quadrant.Q3, Quadrant.derive(Importance.TRIVIAL, Urgency.NOW))
        assertEquals(Quadrant.Q4, Quadrant.derive(Importance.TRIVIAL, Urgency.LATER))
    }

    @Test
    fun `middle values fall toward the more worthwhile cell`() {
        // 宁可信其重要：NORMAL 一律按重要处理，只有「不重要 + 稍后」才落到 Q4
        assertEquals(Quadrant.Q1, Quadrant.derive(Importance.NORMAL, Urgency.NOW))
        assertEquals(Quadrant.Q2, Quadrant.derive(Importance.NORMAL, Urgency.LATER))
        assertEquals(Quadrant.Q2, Quadrant.derive(Importance.CRITICAL, Urgency.SOON))
        // 不重要时「稍后」按紧急处理，避免琐事被抬进 Q2
        assertEquals(Quadrant.Q3, Quadrant.derive(Importance.TRIVIAL, Urgency.SOON))
        assertEquals(Quadrant.Q2, Quadrant.derive(Importance.NORMAL, Urgency.SOON))
    }

    @Test
    fun `every pair lands in exactly one cell`() {
        Importance.entries.forEach { importance ->
            Urgency.entries.forEach { urgency ->
                val quadrant = Quadrant.derive(importance, urgency)
                assertEquals(quadrant, Quadrant.of(quadrant.importance, quadrant.urgency))
            }
        }
    }

    @Test
    fun `out of range stored index falls back to q2 instead of crashing`() {
        assertEquals(Quadrant.Q1, Quadrant.fromIndex(1))
        assertEquals(Quadrant.Q4, Quadrant.fromIndex(4))
        assertEquals(Quadrant.Q2, Quadrant.fromIndex(0))
        assertEquals(Quadrant.Q2, Quadrant.fromIndex(99))
    }

    @Test
    fun `weekday mask bit layout matches its helpers`() {
        val mask = DayOfWeekMask.of(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)
        assertTrue(mask.contains(DayOfWeek.MONDAY))
        assertTrue(mask.contains(DayOfWeek.SUNDAY))
        assertFalse(mask.contains(DayOfWeek.SATURDAY))
        assertEquals(DayOfWeekMask.NONE, mask.minus(DayOfWeek.SUNDAY).minus(DayOfWeek.MONDAY))
        assertTrue(DayOfWeekMask.WORKDAYS.contains(DayOfWeek.FRIDAY))
        assertFalse(DayOfWeekMask.WORKDAYS.contains(DayOfWeek.SATURDAY))
    }
}

package com.paopao.calendar.domain.habit

import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.data.local.entity.HabitLogEntity
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 习惯口径的回归。
 *
 * 这些数字是要给用户看「这个月你到底坚持了几次」的，一条边界算错就是整个统计失真 ——
 * 而它们全都只依赖纯函数，不需要数据库也不需要设备。
 */
class HabitMathTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 29) // 周四
    private val habit: HabitEntity = habit(id = 1L, start = 2026, end = null, target = 7)

    @Test
    fun `起止都含端点，无期限的永远有效`() {
        val fromMay = habit(start = LocalDate.of(2026, 5, 1), end = null)
        assertTrue(HabitMath.isActiveOn(fromMay, LocalDate.of(2026, 5, 1)))
        assertFalse(HabitMath.isActiveOn(fromMay, LocalDate.of(2026, 4, 30)))
        assertTrue(HabitMath.isActiveOn(fromMay, LocalDate.of(2099, 1, 1)))

        val bounded = habit(start = LocalDate.of(2026, 5, 1), end = LocalDate.of(2026, 5, 31))
        assertTrue(HabitMath.isActiveOn(bounded, LocalDate.of(2026, 5, 31)))
        assertFalse(HabitMath.isActiveOn(bounded, LocalDate.of(2026, 6, 1)))
        assertTrue(HabitMath.isFinished(bounded, today))
        assertFalse(HabitMath.isFinished(fromMay, today))
    }

    @Test
    fun `还没开始的习惯既不算今天也不该露脸`() {
        val future = habit(start = LocalDate.of(2026, 12, 1), end = null)
        assertTrue(HabitMath.isUpcoming(future, today))
        assertFalse(HabitMath.isActiveOn(future, today))
    }

    @Test
    fun `日历只列当天打过卡的，并带上备注`() {
        val running = habit(id = 1L, start = LocalDate.of(2026, 1, 1), end = null)
        val reading = habit(id = 2L, start = LocalDate.of(2026, 1, 1), end = null)
        val habits = listOf(running, reading)
        val logs = listOf(
            log(running.id, today, "5 公里"),
            log(reading.id, today.minusDays(1)),
        )

        val day = HabitMath.completionsOn(habits, logs, today)
        assertEquals(1, day.size)
        assertEquals(running.id, day.single().habit.id)
        assertEquals("5 公里", day.single().note)

        val yesterday = HabitMath.completionsOn(habits, logs, today.minusDays(1))
        assertEquals(reading.id, yesterday.single().habit.id)
        assertEquals("", yesterday.single().note)
    }

    @Test
    fun `月视图标记取区间内的打卡日`() {
        val logs = listOf(
            log(1L, today),
            log(1L, today.minusDays(1)),
            log(1L, today.minusDays(10)), // 区间前
            log(1L, today.plusDays(3)),   // 区间后
        )
        val days = HabitMath.loggedDaysInRange(listOf(habit), logs, today.minusDays(3), today)
        assertEquals(setOf(today, today.minusDays(1)), days)
    }

    @Test
    fun `本周区间按用户设定的周起始日排`() {
        val (monStart, monEnd) = HabitMath.periodRange(HabitPeriod.WEEK, today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 26), monStart) // 本周一
        assertEquals(LocalDate.of(2026, 11, 1), monEnd)

        val (sunStart, sunEnd) = HabitMath.periodRange(HabitPeriod.WEEK, today, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 10, 25), sunStart) // 周日开头
        assertEquals(LocalDate.of(2026, 10, 31), sunEnd)
    }

    @Test
    fun `跨年那一周仍按真实日期算`() {
        val janFirst = LocalDate.of(2027, 1, 1) // 周五
        val (start, end) = HabitMath.periodRange(HabitPeriod.WEEK, janFirst, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 12, 28), start)
        assertEquals(LocalDate.of(2027, 1, 3), end)
    }

    @Test
    fun `闰年二月整月是 29 天`() {
        val february = LocalDate.of(2028, 2, 15)
        val (start, end) = HabitMath.periodRange(HabitPeriod.MONTH, february, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2028, 2, 1), start)
        assertEquals(LocalDate.of(2028, 2, 29), end)
    }

    @Test
    fun `分母只算已经过去的天`() {
        val stat = HabitMath.statOf(habit, setOf(today), HabitPeriod.MONTH, today, DayOfWeek.MONDAY)
        // 10 月 1 日到 29 日，共 29 天；剩下的 2 天不该把百分比稀释掉
        assertEquals(29, stat.eligibleDays)
        assertEquals(29, stat.expected)
        assertEquals(1, stat.done)
        assertEquals(1f / 29f, stat.rate, 1e-4f)
    }

    @Test
    fun `分母还要被习惯自己的起止截断`() {
        val startedMidMonth = habit(start = LocalDate.of(2026, 10, 20), end = null)
        val stat = HabitMath.statOf(startedMidMonth, emptySet(), HabitPeriod.MONTH, today, DayOfWeek.MONDAY)
        assertEquals(10, stat.eligibleDays) // 20 到 29

        val endsTomorrow = habit(start = LocalDate.of(2026, 1, 1), end = LocalDate.of(2026, 10, 30))
        val yearStat = HabitMath.statOf(endsTomorrow, emptySet(), HabitPeriod.YEAR, today, DayOfWeek.MONDAY)
        assertEquals(302, yearStat.eligibleDays) // 1 月 1 日到 10 月 29 日（今天），结束日 30 日还没到

        val alreadyOver = habit(start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 10))
        val overStat = HabitMath.statOf(alreadyOver, emptySet(), HabitPeriod.MONTH, today, DayOfWeek.MONDAY)
        assertEquals(10, overStat.eligibleDays) // 只算它真实存在的那 10 天
    }

    @Test
    fun `未来才开始的周期分母是 0 而不是负数或 NaN`() {
        val nextYear = habit(start = LocalDate.of(2027, 3, 1), end = null)
        val stat = HabitMath.statOf(nextYear, emptySet(), HabitPeriod.YEAR, today, DayOfWeek.MONDAY)
        assertEquals(0, stat.eligibleDays)
        assertEquals(0, stat.expected)
        assertEquals(0f, stat.rate, 0f)
    }

    @Test
    fun `每周三次的习惯按 7 折算而不是每天都算`() {
        val thrice = habit(start = LocalDate.of(2026, 1, 1), end = null, target = 3)
        val stat = HabitMath.statOf(thrice, emptySet(), HabitPeriod.MONTH, today, DayOfWeek.MONDAY)
        assertEquals(29, stat.eligibleDays)
        assertEquals(12, stat.expected) // 29 * 3 / 7 = 12.43，四舍五入
    }

    @Test
    fun `折算至少给 1 次，别让刚起步的习惯显示 0%`() {
        val weekly = habit(start = today, end = null, target = 1)
        val stat = HabitMath.statOf(weekly, setOf(today), HabitPeriod.WEEK, today, DayOfWeek.MONDAY)
        assertEquals(1, stat.eligibleDays)
        assertEquals(1, stat.expected)
        assertEquals(1f, stat.rate, 0f)
    }

    @Test
    fun `超额完成截到 100%，但次数照常报`() {
        val twice = habit(start = LocalDate.of(2026, 10, 1), end = null, target = 2)
        val days = (0L..28L).map { LocalDate.of(2026, 10, 1).plusDays(it) }.toSet()
        val stat = HabitMath.statOf(twice, days, HabitPeriod.MONTH, today, DayOfWeek.MONDAY)
        assertEquals(29, stat.done)
        assertTrue(stat.expected < stat.done)
        assertEquals(1f, stat.rate, 0f)
    }

    @Test
    fun `非法的每周目标退回每天`() {
        assertEquals(29, HabitMath.expectedCount(0, 29))
        assertEquals(29, HabitMath.expectedCount(9, 29))
        assertEquals(0, HabitMath.expectedCount(7, 0))
    }

    @Test
    fun `今天没打卡不算断连续`() {
        val days = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(3))
        assertEquals(3, HabitMath.streakOf(days, today))
        assertEquals(0, HabitMath.streakOf(days, today.plusDays(2))) // 断了一天就从 0 起
        assertEquals(4, HabitMath.streakOf(days + today, today))
        assertEquals(0, HabitMath.streakOf(emptySet(), today))
    }

    @Test
    fun `最长连续看的是历史峰值`() {
        val days = (0L..4L).map { today.minusDays(40 + it) }.toSet() +
            (0L..2L).map { today.minusDays(it) }.toSet()
        assertEquals(5, HabitMath.bestStreakOf(days))
        assertEquals(3, HabitMath.streakOf(days, today))
        assertEquals(0, HabitMath.bestStreakOf(emptySet()))
    }

    @Test
    fun `合计取整段起止`() {
        val bounded = habit(start = LocalDate.of(2026, 1, 1), end = LocalDate.of(2026, 3, 31))
        val days = setOf(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 1))
        val total = HabitMath.totalOf(bounded, days, today)
        assertEquals(1, total.done) // 6 月那条在结束日之后，不算
        assertEquals(90, total.eligibleDays) // 1 月 1 日到 3 月 31 日
        assertEquals(90, total.expected)
    }

    @Test
    fun `总览一次给三个周期`() {
        val stats = HabitMath.statsOf(habit, emptySet(), today, DayOfWeek.MONDAY)
        assertEquals(listOf(HabitPeriod.WEEK, HabitPeriod.MONTH, HabitPeriod.YEAR), stats.map { it.period })
        assertEquals("本周", stats.first().period.label)
    }

    @Test
    fun `取数辅助把日志折成日期与备注`() {
        val logs = listOf(log(1L, today, "游泳 40 分钟"), log(2L, today), log(1L, today.minusDays(1)))
        assertEquals(setOf(today, today.minusDays(1)), HabitMath.daysOf(logs, 1L))
        assertEquals("游泳 40 分钟", HabitMath.noteOn(logs, 1L, today))
        assertEquals("", HabitMath.noteOn(logs, 1L, today.minusDays(1)))
        assertEquals("", HabitMath.noteOn(logs, 2L, today.minusDays(1)))
    }

    @Test
    fun `热力图按周成行，最新一周在最后`() {
        val weeks = HabitMath.weekStarts(today, DayOfWeek.MONDAY, 5)
        assertEquals(5, weeks.size)
        assertEquals(LocalDate.of(2026, 10, 26), weeks.last())
        assertEquals(LocalDate.of(2026, 9, 28), weeks.first())
        assertTrue(weeks.zipWithNext().all { (a, b) -> a.plusWeeks(1) == b })
    }

    private fun habit(
        id: Long = 1L,
        start: Int,
        end: LocalDate?,
        target: Int = 7,
    ): HabitEntity = habit(
        id = id,
        start = LocalDate.of(start, 1, 1),
        end = end,
        target = target,
    )

    private fun habit(
        id: Long = 1L,
        start: LocalDate,
        end: LocalDate?,
        target: Int = 7,
    ): HabitEntity = HabitEntity(
        id = id,
        name = "跑步",
        colorArgb = 0xFF4CAF7D.toInt(),
        iconKey = "habit",
        targetPerWeek = target,
        startEpochDay = start.toEpochDay(),
        endEpochDay = end?.toEpochDay(),
    )

    private fun log(habitId: Long, date: LocalDate, note: String = "") =
        HabitLogEntity(habitId = habitId, epochDay = date.toEpochDay(), note = note)
}

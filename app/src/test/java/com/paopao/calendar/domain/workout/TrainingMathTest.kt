package com.paopao.calendar.domain.workout

import com.paopao.calendar.data.local.entity.WorkoutLogEntity
import com.paopao.calendar.data.local.entity.WorkoutPlanEntity
import com.paopao.calendar.domain.model.DayOfWeekMask
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 时长估算、连续天数与周几口径转换。 */
class TrainingMathTest {

    @Test
    fun `duration estimate matches the mini program formula`() {
        // 4 组 × 15 次 ≈ 300 秒，是 parsePlan 原文里写明的例子
        assertEquals(300, TrainingMath.estimateDurationSec(reps = 15, sets = 4))
        assertEquals(0, TrainingMath.estimateDurationSec(reps = 0, sets = 3))
        assertEquals(330, TrainingMath.actionSeconds(durationSec = 300, restSec = 30))
    }

    @Test
    fun `minutes round up so a short session is never zero`() {
        assertEquals(1, TrainingMath.estimatedMinutes(0))
        assertEquals(1, TrainingMath.estimatedMinutes(30))
        assertEquals(1, TrainingMath.estimatedMinutes(59))
        assertEquals(2, TrainingMath.estimatedMinutes(90))
        assertEquals(5, TrainingMath.estimatedMinutes(300))
    }

    @Test
    fun `kilocalories use the six point five per minute rate`() {
        assertEquals(0, TrainingMath.kilocalories(0))
        assertEquals(195, TrainingMath.kilocalories(30))
        assertEquals(390, TrainingMath.kilocalories(60))
    }

    @Test
    fun `streak continues through a day not yet logged today`() {
        val today = LocalDate.of(2026, 10, 27)
        val logged = setOf(today.minusDays(2), today.minusDays(1))
        // 今天还没练：链子从昨天往前数，不清零
        assertEquals(2, TrainingMath.streak(logged, today))
    }

    @Test
    fun `streak counts today when present and stops at the first gap`() {
        val today = LocalDate.of(2026, 10, 27)
        val logged = setOf(today, today.minusDays(1), today.minusDays(3))
        assertEquals(2, TrainingMath.streak(logged, today))
        assertEquals(0, TrainingMath.streak(emptySet(), today))
        // 昨天和今天都没有：链子早就断了
        assertEquals(0, TrainingMath.streak(setOf(today.minusDays(5)), today))
    }

    @Test
    fun `streak is capped by the lookback window`() {
        val today = LocalDate.of(2026, 10, 27)
        val logged = (0..400L).map { today.minusDays(it) }.toSet()
        assertEquals(365, TrainingMath.streak(logged, today, lookbackDays = 365))
        assertEquals(7, TrainingMath.streak(logged, today, lookbackDays = 7))
    }

    @Test
    fun `js day numbers map sunday to the last bit`() {
        // JS getDay(): 0=周日, 1=周一 … 6=周六；掩码是 bit0=周一 … bit6=周日
        val sunday = TrainingMath.weekMaskFromJsDayNumbers(listOf(0))
        assertTrue(sunday.contains(DayOfWeek.SUNDAY))
        assertFalse(sunday.contains(DayOfWeek.MONDAY))

        val weekdays = TrainingMath.weekMaskFromJsDayNumbers(listOf(1, 2, 3, 4, 5))
        assertEquals(DayOfWeekMask.WORKDAYS, weekdays)

        val weekend = TrainingMath.weekMaskFromJsDayNumbers(listOf(0, 6))
        assertEquals(DayOfWeekMask.WEEKEND, weekend)

        assertEquals(DayOfWeekMask.ALL, TrainingMath.weekMaskFromJsDayNumbers((0..6).toList()))
    }

    @Test
    fun `out of range day numbers are dropped instead of widening the mask`() {
        assertEquals(DayOfWeekMask.NONE, TrainingMath.weekMaskFromJsDayNumbers(listOf(7, -1, 99)))
        assertEquals(DayOfWeekMask.of(DayOfWeek.MONDAY), TrainingMath.weekMaskFromJsDayNumbers(listOf(1, 8)))
    }

    @Test
    fun `schedule check reads the same mask the calendar uses`() {
        val monday = LocalDate.of(2026, 10, 26)     // 周一
        val sunday = LocalDate.of(2026, 11, 1)      // 周日
        val workdays = DayOfWeekMask.WORKDAYS.bits
        assertTrue(TrainingMath.isScheduledOn(workdays, monday))
        assertFalse(TrainingMath.isScheduledOn(workdays, sunday))
        assertTrue(TrainingMath.isScheduledOn(DayOfWeekMask.ALL.bits, sunday))
        // 掩码为 0 是没排任何一天，不是「每天」——坏数据不该在日历上天天冒出来
        assertFalse(TrainingMath.isScheduledOn(0, monday))
    }

    @Test
    fun `day view lists scheduled plans and drops the checked-in one`() {
        val monday = LocalDate.of(2026, 10, 26)
        val everyday = plan(id = 1, weekMask = DayOfWeekMask.ALL.bits)
        val weekendOnly = plan(id = 2, weekMask = DayOfWeekMask.WEEKEND.bits)
        val unscheduled = plan(id = 3, weekMask = 0)

        assertEquals(
            listOf(1L),
            TrainingMath.plansToCheckIn(listOf(everyday, weekendOnly, unscheduled), emptyList(), monday)
                .map { it.id },
        )
        // 打过卡就不再冒出「待打卡」，否则手滑按两次会把当天时长翻倍
        assertTrue(
            TrainingMath.plansToCheckIn(
                listOf(everyday),
                listOf(log(planId = 1L, epochDay = monday.toEpochDay())),
                monday,
            ).isEmpty(),
        )
    }

    @Test
    fun `free training log does not hide a scheduled plan`() {
        val monday = LocalDate.of(2026, 10, 26)
        val everyday = plan(id = 1, weekMask = DayOfWeekMask.ALL.bits)
        // 自由训练的日志 planId 为空，它不该被当成「今天这个计划已经练过」
        val logs = listOf(log(planId = null, epochDay = monday.toEpochDay()))
        assertEquals(listOf(1L), TrainingMath.plansToCheckIn(listOf(everyday), logs, monday).map { it.id })
        // 昨天的打卡不影响今天
        assertEquals(
            listOf(1L),
            TrainingMath.plansToCheckIn(listOf(everyday), listOf(log(planId = 1L, epochDay = monday.minusDays(1).toEpochDay())), monday)
                .map { it.id },
        )
    }

    private fun plan(id: Long, weekMask: Int) = WorkoutPlanEntity(
        id = id,
        name = "计划$id",
        bodyPart = "全身",
        weekMask = weekMask,
        estimatedMinutes = 20,
        colorArgb = 0,
    )

    private fun log(planId: Long?, epochDay: Long) = WorkoutLogEntity(
        epochDay = epochDay,
        planId = planId,
        planName = planId?.let { "计划$it" } ?: WorkoutLogEntity.FALLBACK_NAME,
    )
}

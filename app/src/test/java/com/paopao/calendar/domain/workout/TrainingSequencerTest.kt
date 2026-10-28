package com.paopao.calendar.domain.workout

import com.paopao.calendar.data.local.entity.PlanActionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 计时节拍的展开：段数、休息位置、单组时长的口径。 */
class TrainingSequencerTest {

    private fun action(
        name: String,
        sets: Int,
        reps: Int = 0,
        durationSec: Int = 60,
        restSec: Int = 30,
    ) = PlanActionEntity(
        planId = 1,
        sortOrder = 0,
        name = name,
        sets = sets,
        reps = reps,
        durationSec = durationSec,
        restSec = restSec,
    )

    @Test
    fun `one set work phase is the whole duration`() {
        assertEquals(60, TrainingMath.setWorkSec(durationSec = 60, sets = 1))
        assertEquals(90, TrainingMath.setWorkSec(durationSec = 180, sets = 2))
        // 除不尽按四舍五入给每组，且不给 0 秒的组
        assertEquals(17, TrainingMath.setWorkSec(durationSec = 100, sets = 6))
        assertEquals(1, TrainingMath.setWorkSec(durationSec = 2, sets = 10))
        assertEquals(0, TrainingMath.setWorkSec(durationSec = 0, sets = 3))
    }

    @Test
    fun `three sets produce three work phases with two rests between`() {
        val steps = TrainingSequencer.steps(listOf(action("哑铃卧推", sets = 3, reps = 12, durationSec = 90)))
        assertEquals(5, steps.size)
        assertEquals(listOf(false, true, false, true, false), steps.map { it.isRest })
        assertEquals(30, steps.first().seconds)
        assertEquals(30, steps.last().seconds)
        assertEquals("第 1/3 组 · 12 次", steps[0].detail)
        assertEquals("休息 30 秒", steps[1].detail)
    }

    @Test
    fun `no rest is appended after the very last work phase`() {
        val steps = TrainingSequencer.steps(listOf(action("平板支撑", sets = 1, durationSec = 60, restSec = 30)))
        assertEquals(1, steps.size)
        assertFalse(steps.last().isRest)

        val twoActions = TrainingSequencer.steps(
            listOf(action("深蹲", sets = 1, durationSec = 30), action("箭步蹲", sets = 1, durationSec = 30)),
        )
        // 30 工作 + 30 休息 + 30 工作：第一个动作的休息要保留，第二个的不该有尾巴
        assertEquals(3, twoActions.size)
        assertFalse(twoActions.last().isRest)
        assertTrue(twoActions[1].isRest)
    }

    @Test
    fun `identical consecutive actions each keep their rest`() {
        // 两条完全同形的动作在 data class 下 equals 为真，收尾判断必须靠下标
        val steps = TrainingSequencer.steps(
            listOf(action("开合跳", sets = 2, durationSec = 60, restSec = 20), action("开合跳", sets = 2, durationSec = 60, restSec = 20)),
        )
        assertEquals(7, steps.size)
        assertEquals(listOf(false, true, false, true, false, true, false), steps.map { it.isRest })
    }

    @Test
    fun `zero rest collapses to back to back work`() {
        val steps = TrainingSequencer.steps(listOf(action("高抬腿", sets = 3, durationSec = 60, restSec = 0)))
        assertEquals(3, steps.size)
        assertTrue(steps.none { it.isRest })
    }

    @Test
    fun `blank name still gets a readable label`() {
        val steps = TrainingSequencer.steps(listOf(action("   ", sets = 1, durationSec = 45)))
        assertEquals("未命名动作", steps.single().label)
    }

    @Test
    fun `total seconds match the sum of phases`() {
        val actions = listOf(action("波比跳", sets = 4, durationSec = 120, restSec = 30))
        val steps = TrainingSequencer.steps(actions)
        assertEquals(TrainingMath.totalSeconds(steps.map { it.seconds }), steps.sumOf { it.seconds })
        // 4 组：30+30+30+30 工作 + 3 段 30 休息
        assertEquals(210, TrainingSequencer.totalSeconds(actions))
    }

    @Test
    fun `empty plan has no steps`() {
        assertTrue(TrainingSequencer.steps(emptyList()).isEmpty())
        assertEquals(0, TrainingSequencer.totalSeconds(emptyList()))
    }
}

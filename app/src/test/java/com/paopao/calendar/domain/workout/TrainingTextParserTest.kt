package com.paopao.calendar.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 训练文案的规则解析：窗口切分、数字优先级、噪声清理。 */
class TrainingTextParserTest {

    @Test
    fun `strength copy splits into one action per exercise`() {
        val plan = TrainingTextParser.parse("练肩日：哑铃侧平举 4组15次，推举 3组12次，前平举 3组15次，组间休息30秒")

        assertEquals("肩", plan.bodyPart)
        assertEquals(3, plan.actions.size)
        assertEquals(listOf("哑铃侧平举", "哑铃推举", "哑铃前平举"), plan.actions.map { it.name })
        assertEquals(listOf(4, 3, 3), plan.actions.map { it.sets })
        assertEquals(listOf(15, 12, 15), plan.actions.map { it.reps })
        // 只有次数时按 次数×5秒×组数 估算：4×15≈300、3×12≈180、3×15≈225
        assertEquals(listOf(300, 180, 225), plan.actions.map { it.durationSec })
        assertEquals(listOf(30, 30, 30), plan.actions.map { it.restSec })
        assertTrue(plan.actions.all { it.estimated })
        assertEquals(listOf("sh_lateral_raise", "sh_overhead_press", "sh_front_raise"), plan.actions.map { it.exerciseKey })
        // 300+180+225 加三次休息 90，共 795 秒 ≈ 13 分钟
        assertEquals(13, plan.totalDurationMin)
    }

    @Test
    fun `interval copy takes seconds and rest verbatim`() {
        val plan = TrainingTextParser.parse("开合跳40秒 歇20秒 高抬腿40秒 歇20秒 波比跳40秒 歇20秒")

        assertEquals(3, plan.actions.size)
        assertEquals(listOf(40, 40, 40), plan.actions.map { it.durationSec })
        assertEquals(listOf(20, 20, 20), plan.actions.map { it.restSec })
        assertEquals(listOf(1, 1, 1), plan.actions.map { it.sets })
        assertEquals(listOf(0, 0, 0), plan.actions.map { it.reps })
        // 时长是原文写明的，不该标「估」
        assertTrue(plan.actions.none { it.estimated })
        assertEquals(3, plan.totalDurationMin)
    }

    @Test
    fun `later numbers never leak into an earlier action`() {
        val plan = TrainingTextParser.parse("卧推4组12次 划船3组10次")

        assertEquals(listOf("杠铃卧推", "哑铃划船"), plan.actions.map { it.name })
        assertEquals(listOf(4, 3), plan.actions.map { it.sets })
        assertEquals(listOf(12, 10), plan.actions.map { it.reps })
        assertEquals(listOf(240, 150), plan.actions.map { it.durationSec })
    }

    @Test
    fun `explicit seconds and minutes beat the rep based estimate`() {
        val seconds = TrainingTextParser.parse("平板支撑 3组 60秒").actions.single()
        assertEquals(60, seconds.durationSec)
        assertEquals(3, seconds.sets)
        assertEquals(0, seconds.reps)
        assertFalse(seconds.estimated)
        assertEquals("co_plank", seconds.exerciseKey)

        val minutes = TrainingTextParser.parse("臀桥 3组 2分钟").actions.single()
        assertEquals(120, minutes.durationSec)
        assertFalse(minutes.estimated)
    }

    @Test
    fun `count unit ge also reads as reps`() {
        val action = TrainingTextParser.parse("登山跑 3组 10个").actions.single()
        assertEquals(10, action.reps)
        assertEquals(150, action.durationSec)
        assertEquals("cd_mountain_climber", action.exerciseKey)
    }

    @Test
    fun `longer keyword wins when two start at the same position`() {
        // 「深蹲」是「深蹲跳」的前缀，词典插入顺序在前；原实现会把用户写的深蹲跳认成深蹲
        val action = TrainingTextParser.parse("深蹲跳 3组 10个").actions.single()
        assertEquals("深蹲跳", action.name)
        assertEquals("cd_squat_jump", action.exerciseKey)
    }

    @Test
    fun `share noise is stripped before matching`() {
        val plan = TrainingTextParser.parse(
            "Oo3@抖音 #小程序:// 复制打开抖音，看看【某某】练背日的干货 https://v.douyin.com/abc123/ #健身 #练背 🔥划船4组12次",
        )

        assertEquals(1, plan.actions.size)
        assertEquals("哑铃划船", plan.actions.single().name)
        assertEquals(4, plan.actions.single().sets)
        // bodyPart 按原实现在未清洗的原文上找「练X」
        assertEquals("背", plan.bodyPart)
    }

    @Test
    fun `text without any known exercise yields an empty plan`() {
        val plan = TrainingTextParser.parse("今天加班，不练了")

        assertTrue(plan.isEmpty)
        assertTrue(plan.actions.isEmpty())
        assertEquals("未知", plan.bodyPart)
        assertEquals(0, plan.totalDurationMin)
        assertEquals(0, plan.cycleCount)
    }

    @Test
    fun `unknown dictionary names keep a blank catalog key`() {
        // 词典里有几个动作不在百科（硬拉、夹胸、腿举），识别出来但不该硬编一个 key
        val action = TrainingTextParser.parse("硬拉 5组 5次").actions.single()
        assertEquals("杠铃硬拉", action.name)
        assertEquals("", action.exerciseKey)
    }

    @Test
    fun `part falls back to full body when no cue is present`() {
        val plan = TrainingTextParser.parse("俯卧撑 3组 20次 平板支撑 3组 60秒")
        assertEquals("全身", plan.bodyPart)
        assertEquals(2, plan.actions.size)
    }
}

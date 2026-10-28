package com.paopao.calendar.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 动作库的完整性：条数、标识唯一、部位覆盖、检索。 */
class ExerciseCatalogTest {

    /**
     * 条数钉死，是因为这份库是从 fit-miniapp/data/exercises.js 逐条搬过来的：
     * 少一条就意味着搬运时漏了动作，而这种漏在编译期毫无征兆。
     * 30 = 肩 5 + 背 4 + 胸 4 + 臂 3 + 腿 5 + 核心 4 + 有氧 5。
     */
    @Test
    fun `catalog carries all thirty exercises from the mini program`() {
        assertEquals(30, ExerciseCatalog.all.size)
        val perPart = ExerciseCatalog.all.groupingBy { it.part }.eachCount()
        assertEquals(
            mapOf(
                BodyPart.SHOULDER to 5,
                BodyPart.BACK to 4,
                BodyPart.CHEST to 4,
                BodyPart.ARMS to 3,
                BodyPart.LEGS to 5,
                BodyPart.CORE to 4,
                BodyPart.CARDIO to 5,
            ),
            perPart,
        )
    }

    @Test
    fun `keys and names are unique`() {
        assertEquals(ExerciseCatalog.all.size, ExerciseCatalog.all.map { it.key }.distinct().size)
        assertEquals(ExerciseCatalog.all.size, ExerciseCatalog.all.map { it.name }.distinct().size)
    }

    @Test
    fun `every listed part has at least one exercise and no exercise is off the list`() {
        val listed = ExerciseCatalog.partOrder.toSet()
        assertEquals(BodyPart.entries.toSet(), listed)
        ExerciseCatalog.partOrder.forEach { part ->
            assertTrue("$part 没有动作", ExerciseCatalog.all.any { it.part == part })
        }
        assertTrue(ExerciseCatalog.all.all { it.part in listed })
        assertTrue(ExerciseCatalog.all.all { it.steps.isNotEmpty() })
    }

    @Test
    fun `lookup by name finds the catalog entry`() {
        val plank = ExerciseCatalog.findByName("平板支撑")
        assertNotNull(plank)
        assertEquals("co_plank", plank!!.key)
        assertEquals(BodyPart.CORE, plank.part)
        assertNull(ExerciseCatalog.findByName("倒立划船"))
        // 复制粘贴进来的文案常带空格
        assertEquals("co_plank", ExerciseCatalog.findByName(" 平板支撑 ")?.key)
    }

    @Test
    fun `filter narrows by part and matches name label or equipment`() {
        assertEquals(
            ExerciseCatalog.all.count { it.part == BodyPart.SHOULDER },
            ExerciseCatalog.filter(BodyPart.SHOULDER, "").size,
        )
        assertEquals(1, ExerciseCatalog.filter(null, "波比跳").size)

        // 「蹲」在腿部只命中深蹲、箭步蹲与保加利亚分腿蹲，用它验证筛选确实收窄了结果
        val legSquats = ExerciseCatalog.filter(BodyPart.LEGS, "蹲")
        assertTrue(legSquats.isNotEmpty())
        assertTrue(legSquats.size < ExerciseCatalog.all.count { it.part == BodyPart.LEGS })

        assertTrue(ExerciseCatalog.filter(null, "杠铃").all { it.equipment == Equipment.BARBELL })
        assertEquals(ExerciseCatalog.all.size, ExerciseCatalog.filter(null, "   ").size)
    }
}

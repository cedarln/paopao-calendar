package com.paopao.calendar.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 月报。
 *
 * 这份是给人读的，所以断言盯的是「会不会被埋掉」那一层：空白天不占行、
 * 备注跟在记录后面而不是另起一段、每个月的计数对得上样本条数。
 */
class MarkdownExportTest {

    private val md = MarkdownExport.build(ExportFixtures.snapshot, ExportFixtures.zone)
    private val lines = md.lines()

    @Test
    fun `title and one section per month`() {
        assertTrue(lines.first().startsWith("# 泡泡日历 · 2026 年 3 月"))
        assertEquals(1, lines.count { it.startsWith("## 2026 年") })
        assertEquals(1, lines.count { it.startsWith("**本月小结**") })
    }

    @Test
    fun `a single month range does not print the month twice`() {
        val single = MarkdownExport.build(
            ExportFixtures.snapshot.copy(
                range = ExportRange(java.time.LocalDate.of(2026, 3, 2), java.time.LocalDate.of(2026, 3, 20)),
            ),
            ExportFixtures.zone,
        )
        assertEquals("# 泡泡日历 · 2026 年 3 月", single.lines().first())
        assertFalse(single.contains("2026 年 3 月 —"))
    }

    @Test
    fun `a range crossing months gets one section each`() {
        val two = MarkdownExport.build(
            ExportFixtures.snapshot.copy(
                range = ExportRange(java.time.LocalDate.of(2026, 2, 15), java.time.LocalDate.of(2026, 4, 3)),
            ),
            ExportFixtures.zone,
        )
        assertEquals(
            listOf("## 2026 年 2 月", "## 2026 年 3 月", "## 2026 年 4 月"),
            two.lines().filter { it.startsWith("## 2026") },
        )
        assertEquals("# 泡泡日历 · 2026 年 2 月 — 2026 年 4 月", two.lines().first())
        assertEquals(3, two.lines().count { it.startsWith("**本月小结**") })
        assertTrue(two.contains("2月28日"))
    }

    @Test
    fun `a month with nothing in it keeps its section and says so`() {
        // 取样本锚点之前的一年：重复日程的展开不会走到这里，才剩得下一个真空段
        val quiet = MarkdownExport.build(
            ExportFixtures.snapshot.copy(
                range = ExportRange(java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2025, 2, 28)),
            ),
            ExportFixtures.zone,
        )
        assertEquals(2, quiet.lines().count { it.startsWith("## 2025") })
        assertEquals(2, quiet.lines().count { it == "这段时间里没有记录。" })
        // 空月份不给小结：一行「日程 0 条 · 事项 0/0 完成…」没有任何信息量，
        // 还会把真正有记录的那节挤下去
        assertEquals(0, quiet.lines().count { it.startsWith("**本月小结**") })
    }

    @Test
    fun `only days with records get a heading`() {
        val days = lines.filter { it.startsWith("### ") }
        // 样本里有记录的日子：3/5 周会、3/9 一整套、3/20-3/22 出差、3/12 与 3/19、3/26 周会
        assertEquals(
            listOf("3月5日", "3月9日", "3月12日", "3月19日", "3月20日", "3月21日", "3月22日", "3月26日"),
            days.map { it.removePrefix("### ").substringBefore(" ") },
        )
        assertFalse("2 月不在范围内", md.contains("### 2月"))
    }

    @Test
    fun `the day carries events, items, habits, workouts and focus in one place`() {
        val index = lines.indexOfFirst { it.startsWith("### 3月9日") }
        assertTrue(index > 0)
        val day = lines.drop(index + 1).takeWhile { !it.startsWith("### ") && !it.startsWith("## ") }
        assertEquals(listOf("日程", "日程", "事项", "事项", "习惯", "习惯", "训练", "训练", "专注"),
            day.filter { it.startsWith("- ") }.map { it.substringAfter("- ").substringBefore("：") })
        assertTrue(day.any { it.startsWith("- 习惯：跑步 —— 5 公里 配速6:10") })
        assertTrue(day.any { it.startsWith("- 习惯：阅读") })
        assertTrue(day.any { it.contains("组会 10:00–11:15") })
    }

    @Test
    fun `reading shows on 3-09 but not after its end date`() {
        val index = lines.indexOfFirst { it.startsWith("### 3月12日") }
        val day = lines.drop(index + 1).takeWhile { !it.startsWith("### ") && !it.startsWith("## ") }
        // 3/12 只有周会；阅读习惯到 3/15 止，但那天没打卡，所以不会凭空出现
        assertTrue(day.none { it.startsWith("- 习惯：") })
    }

    @Test
    fun `out of range logs never leak into the report`() {
        assertFalse(md.contains("二月的那一次"))
    }

    @Test
    fun `monthly summary counts each kind`() {
        val summary = lines.first { it.startsWith("**本月小结**") }
        assertTrue(summary, summary.contains("日程 9 条"))
        assertTrue(summary, summary.contains("事项 1/2 完成"))
        assertTrue(summary, summary.contains("习惯打卡 2 次"))
        assertTrue(summary, summary.contains("训练 2 次 55 分钟"))
        assertTrue(summary, summary.contains("专注 1 段 25 分钟"))
    }

    @Test
    fun `tags follow the item they belong to`() {
        val item = lines.first { it.startsWith("- 事项：写周报") }
        assertTrue(item, item.contains("＃工作"))
        assertTrue(lines.first { it.startsWith("- 事项：买猫粮") }.contains("（已完成）"))
    }

    @Test
    fun `footnote points at what the report deliberately leaves out`() {
        assertTrue(md.contains("纪念日和已归档的习惯/计划不在这份报告的时间轴上"))
        assertTrue(md.contains("共 1 条纪念日定义"))
    }
}

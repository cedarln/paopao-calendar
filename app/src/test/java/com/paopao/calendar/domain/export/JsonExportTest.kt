package com.paopao.calendar.domain.export

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON 全量备份。
 *
 * 断言用 org.json 把文件解析回来再查，而不是在字符串里 indexOf —— 后者验的是「长得像」，
 * 前者验的是「另一台设备上的解析器读得回去」。备份文件唯一的存在理由就是被读回去。
 */
class JsonExportTest {

    private val text = JsonExport.build(ExportFixtures.snapshot, ExportFixtures.zone)
    private val root = JSONObject(text)

    @Test
    fun `header records schema, version and the requested range`() {
        assertEquals(1, root.getInt("schema"))
        assertEquals("com.paopao.calendar", root.getString("package"))
        assertEquals("0.3.0", root.getString("version"))
        assertEquals("Asia/Shanghai", root.getString("timeZone"))
        val range = root.getJSONObject("requestedRange")
        assertEquals("2026-03-01", range.getString("from"))
        assertEquals("2026-03-31", range.getString("until"))
        assertEquals(31, range.getInt("days"))
    }

    @Test
    fun `generatedAt is an iso timestamp in the export zone`() {
        assertEquals("2026-04-01T08:30+08:00", root.getString("generatedAt"))
    }

    @Test
    fun `counts match the table lengths`() {
        val counts = root.getJSONObject("counts")
        root.keys().forEach { key ->
            val table = root.optJSONArray(key) ?: return@forEach
            assertEquals("$key 的行数", table.length(), counts.getInt(key))
        }
        assertEquals(4, counts.getInt("events"))
        assertEquals(2, counts.getInt("inventories"))
        assertEquals(3, counts.getInt("habitLogs"))
    }

    @Test
    fun `an emptied table still appears as an empty array`() {
        val empty = JsonExport.build(ExportFixtures.snapshot.copy(memorials = emptyList()), ExportFixtures.zone)
        assertEquals(0, JSONObject(empty).getJSONArray("memorials").length())
        assertTrue(empty.contains("\"memorials\": []"))
    }

    @Test
    fun `dates appear as both epochDay and iso text`() {
        val event = root.getJSONArray("events").getJSONObject(0)
        assertEquals(1L, event.getLong("id"))
        assertEquals(ExportFixtures.day(2026, 3, 9), event.getLong("startEpochDay"))
        assertEquals("2026-03-09", event.getString("startDate"))
    }

    @Test
    fun `nullable columns stay null instead of becoming zero`() {
        val trip = table("events").first { it.getString("title").startsWith("出差") }
        trip.expectNull("startMillis")
        trip.expectNull("categoryId")
        // 自由训练的 planId 是 null；落成 0 会让导入侧凭空指向一个不存在的计划
        val free = table("workoutLogs").first { it.getString("planName") == "自由训练" }
        free.expectNull("planId")
    }

    @Test
    fun `trash and archived rows are not filtered out`() {
        val deleted = table("inventories").first { it.getString("title") == "买猫粮" }
        assertTrue(deleted.getBoolean("done"))
        assertTrue(deleted.get("deletedAtMillis") !== JSONObject.NULL)
        val archived = table("workoutPlans").first { it.getString("name") == "推" }
        assertTrue(archived.get("archivedAtMillis") !== JSONObject.NULL)
        val habit = table("habits").first { it.getString("name") == "阅读" }
        assertEquals("2026-03-15", habit.getString("endDate"))
    }

    @Test
    fun `quotes and control characters survive the round trip`() {
        val nasty = ExportFixtures.snapshot.copy(
            events = listOf(
                ExportFixtures.allDayEvent.copy(title = "他说\"带走\"，\t制表\n与\u0000"),
            ),
        )
        val parsed = JSONObject(JsonExport.build(nasty, ExportFixtures.zone))
            .getJSONArray("events").getJSONObject(0)
        assertEquals("他说\"带走\"，\t制表\n与\u0000", parsed.getString("title"))
    }

    @Test
    fun `json ignores the requested range and dumps everything`() {
        // 2 月的那一条打卡在范围外：备份要是缺一截，需要它的那天它就不在
        val narrow = ExportFixtures.snapshot.copy(range = ExportRange(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9)))
        val logs = JSONObject(JsonExport.build(narrow, ExportFixtures.zone)).getJSONArray("habitLogs")
        assertEquals(3, logs.length())
        assertTrue(logs.toString().contains("2026-02-28"))
    }

    /** 缺列与 JSON null 是两件事：前者说明导出漏写了字段，后者才是这一列真的没值。 */
    private fun JSONObject.expectNull(key: String) {
        assertTrue("$key 应存在", has(key))
        assertEquals("$key 应为 null", JSONObject.NULL, get(key))
    }

    private fun table(name: String): List<JSONObject> =
        (0 until root.getJSONArray(name).length()).map { root.getJSONArray(name).getJSONObject(it) }
}

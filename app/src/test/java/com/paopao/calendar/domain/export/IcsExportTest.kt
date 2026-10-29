package com.paopao.calendar.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ICS 导出。
 *
 * 这个格式是三种里唯一「错了就没人告诉你」的：JSON 少个字段自己还能看出来，
 * Markdown 排错了还能读，而 ICS 一旦不合规，别的日历应用的做法是**默默跳过这条事件**。
 * 所以断言贴着 RFC 5545 的硬要求写：CRLF、75 字节折行、DTEND 不含端点、文本转义。
 */
class IcsExportTest {

    private val ics = IcsExport.build(ExportFixtures.snapshot, ExportFixtures.zone)
    private val lines = ics.lines()

    @Test
    fun `every line ends with crlf`() {
        // 裸 LF 在 RFC 5545 里不是行终止符：对端会把整份文件读成一行，然后报「没有事件」
        assertEquals(ics.count { it == '\n' }, ics.count { it == '\r' })
        assertTrue(ics.endsWith("\r\n"))
    }

    @Test
    fun `envelope is a published gregorian calendar`() {
        assertTrue(lines.first().startsWith("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("VERSION:2.0"))
        assertTrue(ics.contains("CALSCALE:GREGORIAN"))
        assertTrue(lines.last { it.isNotBlank() }.startsWith("END:VCALENDAR"))
    }

    @Test
    fun `no line exceeds 75 octets`() {
        ics.split("\r\n").forEach { line ->
            val bytes = line.toByteArray(Charsets.UTF_8).size
            assertTrue("超长行（$bytes 字节）：$line", bytes <= 75)
        }
    }

    @Test
    fun `folded lines start with a single space`() {
        val continuation = ics.split("\r\n").filter { it.startsWith(" ") }
        continuation.forEach { assertFalse(it.startsWith("  ")) }
        // 「出差, 上海; 三天」+ 备注里的换行必然产生折行，否则这组断言什么都没验到
        assertTrue("样本没有触发折行，换个更长的标题再来测", continuation.isNotEmpty())
    }

    @Test
    fun `all day event spans with exclusive end`() {
        val body = eventBlock("体检")
        assertTrue(body.toString(), body.any { it == "DTSTART;VALUE=DATE:20260309" })
        assertTrue(body.any { it == "DTEND;VALUE=DATE:20260310" })
        assertTrue(body.any { it.startsWith("UID:paopao-1-2026-03-09@paopao") })
    }

    @Test
    fun `timed event is written in utc`() {
        val body = eventBlock("组会")
        assertTrue(body.any { it == "DTSTART:20260309T020000Z" })
        assertTrue(body.any { it == "DTEND:20260309T031500Z" })
    }

    @Test
    fun `reminder becomes a display alarm with a minute trigger`() {
        val body = eventBlock("组会")
        assertTrue(body.any { it == "BEGIN:VALARM" })
        assertTrue(body.any { it == "TRIGGER:-PT30M" })
        assertTrue(body.any { it == "END:VALARM" })
        assertFalse("全天无提醒的体检不该带闹钟", eventBlock("体检").any { it == "BEGIN:VALARM" })
    }

    @Test
    fun `recurring event is expanded instead of carrying an rrule`() {
        assertFalse(ics.contains("RRULE"))
        val uids = lines.filter { it.startsWith("UID:paopao-3-") }
        assertEquals("周四各一次", listOf(
            "UID:paopao-3-2026-03-05@paopao",
            "UID:paopao-3-2026-03-12@paopao",
            "UID:paopao-3-2026-03-19@paopao",
            "UID:paopao-3-2026-03-26@paopao",
        ), uids.map { it.trim() })
    }

    @Test
    fun `multi day event is one vevent not three`() {
        assertEquals(1, lines.count { it.startsWith("UID:paopao-4-") })
        val body = eventBlock("出差")
        assertTrue(body.any { it.contains("DTSTART;VALUE=DATE:20260320") })
        assertTrue(body.any { it.contains("DTEND;VALUE=DATE:20260323") })
    }

    @Test
    fun `commas semicolons and newlines in text are escaped`() {
        val summary = lines.first { it.startsWith("SUMMARY:") && it.contains("出差") }
        assertTrue(summary, summary.contains("\\,"))
        assertTrue(summary, summary.contains("\\;"))
        val description = lines.first { it.startsWith("DESCRIPTION:") && it.contains("空腹") }
        assertFalse("备注里的真换行必须变成 \\n", description.contains('\n'))
        val tripDescription = lines.first { it.startsWith("DESCRIPTION:") && it.contains("逗号") }
        assertTrue(tripDescription, tripDescription.contains("\\n"))
    }

    @Test
    fun `category name rides along as a categories property`() {
        assertTrue(eventBlock("体检").any { it.startsWith("CATEGORIES:") && it.contains("日程") })
    }

    private fun eventBlock(titleFragment: String): List<String> {
        val flat = ics.split("\r\n").map { it.trimStart() }
        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        for (line in flat) {
            when {
                line.startsWith("BEGIN:VEVENT") -> current = mutableListOf(line)
                current != null -> {
                    current.add(line)
                    if (line.startsWith("END:VEVENT")) {
                        blocks += current
                        current = null
                    }
                }
            }
        }
        // SUMMARY 可能被折行，折出去的那一段也在块里，所以拼起来找
        return blocks.filter { block ->
            block.joinToString("").contains(titleFragment)
        }.also { assertTrue("没找到包含「$titleFragment」的 VEVENT", it.isNotEmpty()) }.first()
    }
}

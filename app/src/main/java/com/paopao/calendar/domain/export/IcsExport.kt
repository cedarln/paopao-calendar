package com.paopao.calendar.domain.export

import com.paopao.calendar.data.local.entity.EventEntity
import com.paopao.calendar.domain.calendar.EventOccurrences
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ICS（iCalendar）导出。
 *
 * 只导日程，不导习惯打卡与训练：VEVENT 的语义是「占据一段时间的一件事」，
 * 而「今天跑了步」是一条完成记录，硬塞进日历会变成一堆别人看不懂的全天事件。
 * 纪念日同理 —— 它的下一次发生日要靠农历换算，交给导入方去解释规则不现实，
 * 所以它跟着 JSON 备份走。
 *
 * 重复日程**展开成一条条真实事件**，不写 RRULE。这是这份导出里最贵也最值的一个决定：
 * RRULE 能让文件更小，但本应用的规则里有 `lunaryear`（农历生日这种），RFC 5545 没有对应物，
 * 导出去必然是错的；而展开出来的 VEVENT 在任何日历软件里的表现都和我们的日视图一致 ——
 * 反正导入方也不需要「改所有次」，那是本应用内的概念。
 */
object IcsExport {

    /** RFC 5545：单行内容不得超过 75 字节（不含 CRLF）。 */
    private const val MAX_OCTETS = 75
    private const val CRLF = "\r\n"

    private val utcStamp: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(java.time.ZoneOffset.UTC)

    fun build(snapshot: ExportSnapshot, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        emit("BEGIN:VCALENDAR")
        emit("PRODID:-//com.paopao.calendar//Paopao Calendar ${snapshot.appVersion}//CN")
        emit("VERSION:2.0")
        emit("CALSCALE:GREGORIAN")
        emit("METHOD:PUBLISH")
        emit("X-WR-CALNAME:泡泡日历 ${snapshot.range.from}至${snapshot.range.until}")
        emit("X-WR-TIMEZONE:${zone.id}")

        for (event in occurrencesIn(snapshot, zone)) {
            emit("BEGIN:VEVENT")
            val day = LocalDate.ofEpochDay(event.startEpochDay)
            emit("UID:paopao-${event.id}-$day@paopao")
            emit("DTSTAMP:${snapshot.generatedAtMillis.toUtcStamp()}")
            if (event.allDay || event.startMillis == null) {
                val end = LocalDate.ofEpochDay(event.endEpochDay).plusDays(1)
                emit("DTSTART;VALUE=DATE:${day.basic()}")
                emit("DTEND;VALUE=DATE:${end.basic()}")
            } else {
                val start = event.startMillis!!
                // 结束时间缺失或早于开始时间时按一小时记：ICS 不接受只有 DTSTART 的定时事件。
                val end = event.endMillis?.takeIf { it > start } ?: start + 60 * 60 * 1000L
                emit("DTSTART:${start.toUtcStamp()}")
                emit("DTEND:${end.toUtcStamp()}")
            }
            emit("SUMMARY:${text(event.title.ifBlank { "未命名日程" })}")
            val description = buildString {
                if (event.recurrenceText != "none") append("重复规则：").append(event.recurrenceText).append('\n')
                append(event.note)
            }.trim()
            if (description.isNotEmpty()) emit("DESCRIPTION:${text(description)}")
            snapshot.categoryName(event.categoryId)?.let { emit("CATEGORIES:${text(it)}") }
            emit("STATUS:CONFIRMED")
            if (event.remindMinutes >= 0) {
                emit("BEGIN:VALARM")
                emit("ACTION:DISPLAY")
                emit("DESCRIPTION:${text(event.title.ifBlank { "未命名日程" })}")
                emit("TRIGGER:-PT${event.remindMinutes}M")
                emit("END:VALARM")
            }
            emit("END:VEVENT")
        }
        emit("END:VCALENDAR")
    }

    /**
     * 与导出范围有任何重叠的事件实例，按开始时间排序。
     *
     * 按天调 [EventOccurrences.on] 而不是直接用规则的 `occursOn`：展开逻辑在
     * 日视图、提醒与这里必须是同一份，否则「App 里那天有、导出去那天没有」这种
     * 问题永远说不清是谁的错。跨天的事件只取它自己那个实例（key 含起始日），
     * 所以一条三天出差只会写成一个 VEVENT，而不是三个。
     */
    private fun occurrencesIn(snapshot: ExportSnapshot, zone: ZoneId): List<EventEntity> {
        val from = snapshot.range.from
        val until = snapshot.range.until
        val seen = LinkedHashMap<Pair<Long, Long>, EventEntity>()
        var cursor = from
        while (cursor <= until) {
            for (instance in EventOccurrences.on(snapshot.events, cursor)) {
                seen.putIfAbsent(instance.id to instance.startEpochDay, instance)
            }
            cursor = cursor.plusDays(1)
        }
        return seen.values.sortedWith(
            compareBy({ it.startMillis ?: LocalDate.ofEpochDay(it.startEpochDay).atStartOfDay(zone).toInstant().toEpochMilli() }, { it.title }),
        )
    }

    private fun text(raw: String): String = buildString {
        raw.replace("\r\n", "\n").replace('\r', '\n').forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                else -> append(ch)
            }
        }
    }

    private fun StringBuilder.emit(line: String) {
        var rest = line
        var isHead = true
        do {
            val cap = if (isHead) MAX_OCTETS else MAX_OCTETS - 1
            val cut = cutAt(rest, cap)
            if (isHead) append(rest.substring(0, cut)) else append(' ').append(rest.substring(0, cut))
            append(CRLF)
            rest = rest.substring(cut)
            isHead = false
        } while (rest.isNotEmpty())
    }

    /**
     * 返回 [value] 从开头最多能占 [maxOctets] 字节的下标。
     *
     * 代理对（emoji）按 4 字节整体处理：把半个字符甩到续行上，多数日历软件会直接
     * 把后面整个属性解析失败，代价是「这条日程导出去就不见了」，不能冒这个险。
     */
    private fun cutAt(value: String, maxOctets: Int): Int {
        var octets = 0
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            val paired = ch.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate()
            val width = when {
                paired -> 4
                ch.code < 0x80 -> 1
                ch.code < 0x800 -> 2
                else -> 3
            }
            if (octets + width > maxOctets) return index
            octets += width
            index += if (paired) 2 else 1
        }
        return value.length
    }

    private fun Long.toUtcStamp(): String =
        utcStamp.format(java.time.Instant.ofEpochMilli(this))

    private fun LocalDate.basic(): String = format(DateTimeFormatter.BASIC_ISO_DATE)
}

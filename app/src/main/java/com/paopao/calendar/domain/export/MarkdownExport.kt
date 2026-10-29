package com.paopao.calendar.domain.export

import com.paopao.calendar.data.local.entity.EventEntity
import com.paopao.calendar.data.local.entity.FocusSessionEntity
import com.paopao.calendar.domain.calendar.ChineseCalendar
import com.paopao.calendar.domain.calendar.EventOccurrences
import com.paopao.calendar.domain.habit.HabitMath
import com.paopao.calendar.domain.workout.TrainingMath
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Markdown 月报。
 *
 * 结构是「按月分节 → 按天平铺 → 每月一段汇总」，而不是按类型分区（所有日程一节、所有习惯一节）：
 * 导出这份报告的人要回答的问题是「那段时间我干了什么」，答案的时间坐标比类型坐标重要。
 *
 * 只写有内容的日子。空白天在三个月里有一百多天，把它们列出来等于让真正的那几行被埋掉。
 */
object MarkdownExport {

    fun build(snapshot: ExportSnapshot, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        val from = snapshot.range.from
        val until = snapshot.range.until
        // 同一个月里选 1 日到 31 日，标题不该写成「3 月 — 3 月」：比较的是月份，不是首末日期
        val spanTitle = if (YearMonth.from(from) != YearMonth.from(until)) " — ${monthTitle(until)}" else ""
        append("# 泡泡日历 · ").append(monthTitle(from)).append(spanTitle).append('\n')
        append('\n')
        append("导出时间 ${snapshot.generatedAtMillis.instantText(zone)}，范围 ${from} 至 $until（${snapshot.range.days} 天）。\n")
        append('\n')

        for (month in snapshot.range.months()) {
            val monthFrom = maxOf(month.atDay(1), from)
            val monthTo = minOf(month.atEndOfMonth(), until)
            val days = contentDays(snapshot, monthFrom, monthTo, zone)
            append("## ").append(monthTitle(month.atDay(1))).append('\n').append('\n')
            if (days.isEmpty()) {
                append("这段时间里没有记录。\n\n")
                continue
            }
            for (date in days) {
                append("### ").append(date.textHeader()).append('\n')
                appendDay(snapshot, date, zone)
                append('\n')
            }
            appendSummary(snapshot, monthFrom, monthTo, zone)
        }
        appendFootnotes(snapshot)
    }

    /** 只留下真的有记录的日子。 */
    private fun contentDays(
        snapshot: ExportSnapshot,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): List<LocalDate> {
        val days = sortedSetOf<LocalDate>()
        var cursor = from
        while (cursor <= to) {
            if (hasContent(snapshot, cursor, zone)) days += cursor
            cursor = cursor.plusDays(1)
        }
        return days.toList()
    }

    private fun hasContent(snapshot: ExportSnapshot, date: LocalDate, zone: ZoneId): Boolean =
        EventOccurrences.on(snapshot.events, date).isNotEmpty() ||
            snapshot.inventoriesDueOn(date).isNotEmpty() ||
            snapshot.habitLogsOn(date).isNotEmpty() ||
            snapshot.workoutLogsOn(date).isNotEmpty() ||
            snapshot.focusOn(date, zone).isNotEmpty()

    private fun StringBuilder.appendDay(snapshot: ExportSnapshot, date: LocalDate, zone: ZoneId) {
        val info = ChineseCalendar.infoOf(date)
        val tags = buildString {
            info.solarFestivals.forEach { append(" · ").append(it) }
            info.lunarFestivals.forEach { append(" · ").append(it) }
            info.solarTerm?.let { append(" · ").append(it) }
        }
        append('（').append(info.shortLunarLabel).append(tags).append("）\n\n")

        EventOccurrences.on(snapshot.events, date).forEach { event ->
            append("- 日程：").append(event.line(zone)).append('\n')
        }
        snapshot.inventoriesDueOn(date).forEach { item ->
            append("- 事项：").append(item.title.ifBlank { "未命名事项" })
            if (item.done) append("（已完成）")
            val tags = snapshot.tagNamesOf(item.id)
            if (tags.isNotEmpty()) append(" ＃").append(tags.joinToString(" ＃"))
            if (item.note.isNotBlank()) append(" —— ").append(item.note.lineBreak())
            append('\n')
        }
        HabitMath.completionsOn(snapshot.habits, snapshot.habitLogs, date).forEach { completion ->
            append("- 习惯：").append(completion.habit.name)
            if (completion.note.isNotBlank()) append(" —— ").append(completion.note.lineBreak())
            append('\n')
        }
        snapshot.workoutLogsOn(date).forEach { log ->
            val minutes = (log.durationSec / 60).coerceAtLeast(1)
            append("- 训练：").append(log.planName).append(" · ").append(minutes).append(" 分钟 · ")
                .append(TrainingMath.kilocalories(minutes)).append(" 千卡")
            if (log.note.isNotBlank()) append(" —— ").append(log.note.lineBreak())
            append('\n')
        }
        snapshot.focusOn(date, zone).forEach { session ->
            append("- 专注：").append(session.actualMinutes).append(" 分钟")
            if (!session.completed) append("（未完成，计划 ").append(session.plannedMinutes).append(" 分钟）")
            append('\n')
        }
    }

    private fun StringBuilder.appendSummary(
        snapshot: ExportSnapshot,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ) {
        var events = 0
        var items = 0
        var itemsDone = 0
        var habitLogs = 0
        var workouts = 0
        var workoutMinutes = 0
        var focus = 0
        var focusMinutes = 0
        var cursor = from
        while (cursor <= to) {
            events += EventOccurrences.on(snapshot.events, cursor).size
            val due = snapshot.inventoriesDueOn(cursor)
            items += due.size
            itemsDone += due.count { it.done }
            habitLogs += snapshot.habitLogsOn(cursor).size
            val logs = snapshot.workoutLogsOn(cursor)
            workouts += logs.size
            workoutMinutes += logs.sumOf { it.durationSec } / 60
            val sessions = snapshot.focusOn(cursor, zone)
            focus += sessions.size
            focusMinutes += sessions.sumOf { it.actualMinutes }
            cursor = cursor.plusDays(1)
        }
        append("**本月小结**：日程 ").append(events).append(" 条 · 事项 ").append(itemsDone)
            .append('/').append(items).append(" 完成 · 习惯打卡 ").append(habitLogs).append(" 次 · 训练 ")
            .append(workouts).append(" 次 ").append(workoutMinutes).append(" 分钟 · 专注 ").append(focus)
            .append(" 段 ").append(focusMinutes).append(" 分钟。\n\n")
    }

    private fun StringBuilder.appendFootnotes(snapshot: ExportSnapshot) {
        append("---\n\n")
        append("纪念日和已归档的习惯/计划不在这份报告的时间轴上，它们完整记在 JSON 备份里。\n")
        val memorials = snapshot.memorials.size
        if (memorials > 0) {
            append('\n').append("共 ").append(memorials).append(" 条纪念日定义，随 JSON 一起导出。\n")
        }
    }

    private fun EventEntity.line(zone: ZoneId): String = buildString {
        append(title.ifBlank { "未命名日程" })
        if (!allDay && startMillis != null) {
            val start = startMillis!!.instantTextHM(zone)
            val end = endMillis?.instantTextHM(zone)
            append(" ").append(start).append(if (end != null) "–$end" else "")
        } else if (startEpochDay != endEpochDay) {
            append("（持续到 ").append(LocalDate.ofEpochDay(endEpochDay)).append('）')
        }
        if (recurrenceText != "none") append(" · 重复 ").append(recurrenceText)
        if (note.isNotBlank()) append(" —— ").append(note.lineBreak())
    }

    private fun LocalDate.textHeader(): String =
        "${monthValue}月${dayOfMonth}日 " + dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)

    private fun monthTitle(date: LocalDate): String = "${date.year} 年 ${date.monthValue} 月"

    /** 换行压成空格：Markdown 的列表项里嵌一个真换行会把后面几行甩出列表。 */
    private fun String.lineBreak(): String = replace("\n", " ").replace("\r", " ").trim()

    /** 导出时间写成「2026-04-01 08:30」：这份文件是人读的，别把 ISO 的 T 原样端上去。 */
    private fun Long.instantText(zone: ZoneId): String =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime().toLocalDate()
            .toString() + " " + Instant.ofEpochMilli(this).atZone(zone).toLocalTime().withSecond(0).withNano(0)

    private fun Long.instantTextHM(zone: ZoneId): String =
        Instant.ofEpochMilli(this).atZone(zone).toLocalTime().withSecond(0).withNano(0).toString()

    private fun ExportSnapshot.focusOn(date: LocalDate, zone: ZoneId): List<FocusSessionEntity> =
        focusSessions.filter {
            Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate() == date
        }
}

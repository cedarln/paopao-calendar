package com.paopao.calendar.domain.calendar

import com.paopao.calendar.data.local.entity.EventEntity
import com.paopao.calendar.domain.model.Recurrence
import com.paopao.calendar.domain.model.RecurrenceCodec
import com.paopao.calendar.domain.model.occursOn
import java.time.LocalDate

/**
 * 日程的重复展开：把带规则的母记录摊成「某一天上的那一次」。
 *
 * 库里的母记录不能直接画到日历上，界面、提醒调度与导出三个调用方都需要展开后的实例，
 * 所以这段逻辑从 EventRepository 里搬出来成为纯函数 —— 留在仓储里时，
 * 导出侧要复用就得先拿到一个仓储实例，而它明明一个查询都不发。
 *
 * 展开出的实例只改跨度、不改 id，所以点进虚拟实例编辑仍然回到母记录 ——
 * 这是「改这一次 / 改所有次」对话框的前提。
 */
object EventOccurrences {

    /**
     * 把一批日程展开到 [date] 这一天。
     *
     * 输入允许是「跨度命中的 + 全部母记录」的并集，重复的普通日程靠 `distinctBy` 收敛，
     * 所以既服务挂起的单日查询，也服务界面侧的持续流。
     */
    fun on(events: List<EventEntity>, date: LocalDate): List<EventEntity> {
        val epochDay = date.toEpochDay()
        val plain = events.filter {
            RecurrenceCodec.decode(it.recurrenceText) == Recurrence.None &&
                it.startEpochDay <= epochDay && it.endEpochDay >= epochDay
        }
        val expanded = events.mapNotNull { instanceOnDay(it, date) }
        return (plain + expanded)
            .distinctBy { it.id to it.startEpochDay }
            .sortedWith(compareBy({ it.startMillis ?: Long.MAX_VALUE }, { it.title }))
    }

    /** 区间内哪些天有日程（含重复展开出来的那些天），月视图标记与导出汇总都用它。 */
    fun daysWithData(events: List<EventEntity>, from: LocalDate, to: LocalDate): Set<LocalDate> {
        val days = LinkedHashSet<LocalDate>()
        var cursor = from
        while (cursor <= to) {
            if (on(events, cursor).isNotEmpty()) days += cursor
            cursor = cursor.plusDays(1)
        }
        return days
    }

    /**
     * 单条日程在 [date] 的实例；不命中返回 null。
     *
     * 多天重复事件（「每周一，连着三天」）的判断是把 [date] 往回平移整个跨度，
     * 只要任一平移位落在规则命中日就算命中 —— 否则这条日程只会出现在它的第一天。
     */
    private fun instanceOnDay(event: EventEntity, date: LocalDate): EventEntity? {
        val recurrence = RecurrenceCodec.decode(event.recurrenceText)
        if (recurrence == Recurrence.None) return null
        val anchor = LocalDate.ofEpochDay(event.startEpochDay)
        val durationDays = (event.endEpochDay - event.startEpochDay).coerceAtLeast(0L)
        val until = event.repeatUntilEpochDay?.let { LocalDate.ofEpochDay(it) }
        for (offset in 0L..durationDays) {
            val occurrenceStart = date.minusDays(offset)
            if (occurrenceStart.isBefore(anchor)) break
            if (until != null && occurrenceStart.isAfter(until)) continue
            if (recurrence.occursOn(occurrenceStart, anchor)) {
                return event.copy(
                    startEpochDay = occurrenceStart.toEpochDay(),
                    endEpochDay = occurrenceStart.plusDays(durationDays).toEpochDay(),
                )
            }
        }
        return null
    }
}

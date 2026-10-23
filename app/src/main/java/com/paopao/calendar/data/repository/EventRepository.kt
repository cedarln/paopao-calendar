package com.paopao.calendar.data.repository

import com.paopao.calendar.data.local.dao.EventDao
import com.paopao.calendar.data.local.entity.EventEntity
import com.paopao.calendar.domain.model.Recurrence
import com.paopao.calendar.domain.model.RecurrenceCodec
import com.paopao.calendar.domain.model.occursOn
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 日程仓储。
 *
 * 重复日程的展开发生在**读取路径**而不是写入路径：库里只存一条带规则的母记录。
 * 写入时展开成 N 条独立记录会带来两个后果 —— 「只改这次还是改所有次」要跨行批量更新，
 * 以及母规则本身没有落脚点了。
 */
class EventRepository(private val dao: EventDao) {

    fun observeOverlapping(from: LocalDate, to: LocalDate): Flow<List<EventEntity>> =
        dao.observeOverlapping(from.toEpochDay(), to.toEpochDay())

    suspend fun findById(id: Long): EventEntity? = dao.findById(id)

    suspend fun upsert(event: EventEntity): Long = if (event.id == 0L) {
        dao.insert(event)
    } else {
        dao.update(event)
        event.id
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /**
     * 某天命中的日程列表。
     *
     * 两路取并集：区间查询命中的普通事件，以及重复规则在该日展开的实例。
     * 展开出的实例只改跨度、不改 id，所以点进虚拟实例编辑仍然回到母记录 ——
     * 这是「改这一次 / 改所有次」对话框的前提。
     */
    suspend fun eventsOn(date: LocalDate): List<EventEntity> {
        val epochDay = date.toEpochDay()
        val pool = dao.findOverlapping(epochDay, epochDay) + dao.findRecurring()
        return occurrencesOn(pool, date)
    }

    /**
     * 把一批日程展开到 [date] 这一天。纯函数，输入允许是「跨度命中的 + 全部母记录」的并集，
     * 所以既服务挂起的单日查询，也服务界面侧的持续流。
     */
    fun occurrencesOn(events: List<EventEntity>, date: LocalDate): List<EventEntity> {
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

    /** 月视图用：网格区间内哪些天有事。 */
    fun daysWithEvents(events: List<EventEntity>, from: LocalDate, to: LocalDate): Set<LocalDate> {
        val days = LinkedHashSet<LocalDate>()
        var cursor = from
        while (cursor <= to) {
            if (occurrencesOn(events, cursor).isNotEmpty()) days += cursor
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

    /** 月视图用：一次取回整段网格区间内有事的日期，避免 42 次单日查询。 */
    suspend fun eventDaysInRange(from: LocalDate, to: LocalDate): Set<LocalDate> =
        daysWithEvents(allInRange(from, to), from, to)

    /** 网格区间内可能出现的全部日程（普通跨度命中 + 所有重复母记录）。 */
    suspend fun allInRange(from: LocalDate, to: LocalDate): List<EventEntity> =
        dao.findOverlapping(from.toEpochDay(), to.toEpochDay()) + dao.findRecurring()

    /** 持续流版本：同 [allInRange]，但会在表变化时重新发射。 */
    fun observeInRange(from: LocalDate, to: LocalDate): Flow<List<EventEntity>> =
        combine(dao.observeOverlapping(from.toEpochDay(), to.toEpochDay()), dao.observeRecurring()) { span, recurring ->
            span + recurring
        }
}

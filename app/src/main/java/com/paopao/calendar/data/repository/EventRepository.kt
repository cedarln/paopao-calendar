package com.paopao.calendar.data.repository

import com.paopao.calendar.data.local.dao.EventDao
import com.paopao.calendar.data.local.entity.EventEntity
import com.paopao.calendar.domain.calendar.EventOccurrences
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
     * 展开规则本身在 `EventOccurrences`，这里只负责喂给它一个候选池。
     */
    suspend fun eventsOn(date: LocalDate): List<EventEntity> {
        val epochDay = date.toEpochDay()
        val pool = dao.findOverlapping(epochDay, epochDay) + dao.findRecurring()
        return occurrencesOn(pool, date)
    }

    /** 纯函数转发，见 [EventOccurrences.on]。输入可以是「跨度命中的 + 全部母记录」的并集。 */
    fun occurrencesOn(events: List<EventEntity>, date: LocalDate): List<EventEntity> =
        EventOccurrences.on(events, date)

    /** 月视图用：网格区间内哪些天有事。 */
    fun daysWithEvents(events: List<EventEntity>, from: LocalDate, to: LocalDate): Set<LocalDate> =
        EventOccurrences.daysWithData(events, from, to)

    /** 月视图用：一次取回整段网格区间内有事的日期，避免 42 次单日查询。 */
    suspend fun eventDaysInRange(from: LocalDate, to: LocalDate): Set<LocalDate> =
        daysWithEvents(allInRange(from, to), from, to)

    /** 网格区间内可能出现的全部日程（普通跨度命中 + 所有重复母记录）。 */
    suspend fun allInRange(from: LocalDate, to: LocalDate): List<EventEntity> =
        dao.findOverlapping(from.toEpochDay(), to.toEpochDay()) + dao.findRecurring()

    /** 导出用：含已过期历史在内的整表快照。 */
    suspend fun findAllIncludingHistory(): List<EventEntity> = dao.findAll()

    /** 持续流版本：同 [allInRange]，但会在表变化时重新发射。 */
    fun observeInRange(from: LocalDate, to: LocalDate): Flow<List<EventEntity>> =
        combine(dao.observeOverlapping(from.toEpochDay(), to.toEpochDay()), dao.observeRecurring()) { span, recurring ->
            span + recurring
        }
}

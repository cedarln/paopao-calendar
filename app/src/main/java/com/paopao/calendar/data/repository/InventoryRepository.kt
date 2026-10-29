package com.paopao.calendar.data.repository

import com.paopao.calendar.data.local.dao.InventoryDao
import com.paopao.calendar.data.local.entity.InventoryEntity
import com.paopao.calendar.data.local.entity.InventoryTagCrossRef
import com.paopao.calendar.data.local.entity.TagEntity
import com.paopao.calendar.domain.model.Importance
import com.paopao.calendar.domain.model.Quadrant
import com.paopao.calendar.domain.model.Recurrence
import com.paopao.calendar.domain.model.RecurrenceCodec
import com.paopao.calendar.domain.model.Urgency
import com.paopao.calendar.domain.model.occursOn
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 事项的读写入口。
 *
 * [upsert] 里重算 [InventoryEntity.quadrantIndex] 是这个类存在的理由：
 * 保证「物化列」永远和双轴一致，任何调用方忘了算也不会写出脏数据。
 */
class InventoryRepository(private val dao: InventoryDao) {

    fun observeActiveRoots(showDone: Boolean = false): Flow<List<InventoryEntity>> =
        dao.observeByQuadrant(quadrant = 0, showDone = showDone)

    fun observeQuadrant(quadrant: Quadrant, showDone: Boolean = false): Flow<List<InventoryEntity>> =
        dao.observeByQuadrant(quadrant = quadrant.ordinal + 1, showDone = showDone)

    fun observeDueOn(epochDay: Long): Flow<List<InventoryEntity>> =
        dao.observeDueOn(epochDay)

    /**
     * 日历视图用：某段日期内「会露面」的事项 = 到期日落在区间内的 + 重复规则需要展开的母记录。
     *
     * 重复事项与日程共用同一套 [com.paopao.calendar.domain.model.occursOn] 规则，
     * 所以这里只把母记录捞出来，命中判定交给 [itemsOn]。
     */
    fun observeScheduledBetween(from: LocalDate, to: LocalDate): Flow<List<InventoryEntity>> =
        combine(
            dao.observeDueBetween(from.toEpochDay(), to.toEpochDay()),
            dao.observeRecurring(),
        ) { due, recurring -> (due + recurring).distinctBy { it.id } }

    /** [items] 在 [date] 当天出现的事项。 */
    fun itemsOn(items: List<InventoryEntity>, date: LocalDate): List<InventoryEntity> {        val epochDay = date.toEpochDay()
        return items.filter { item ->
            val recurrence = RecurrenceCodec.decode(item.recurrenceText)
            if (recurrence == Recurrence.None) {
                item.dueEpochDay == epochDay
            } else {
                recurrence.occursOn(date, LocalDate.ofEpochDay(item.dueEpochDay ?: item.createdAtEpochDay()))
            }
        }.sortedWith(compareBy({ it.done }, { it.dueMinuteOfDay ?: Int.MAX_VALUE }))
    }

    /** 月视图密度：区间内哪些天有事项。 */
    fun scheduledDays(items: List<InventoryEntity>, from: LocalDate, to: LocalDate): Set<LocalDate> {
        val days = LinkedHashSet<LocalDate>()
        var cursor = from
        while (cursor <= to) {
            if (itemsOn(items, cursor).isNotEmpty()) days += cursor
            cursor = cursor.plusDays(1)
        }
        return days
    }

    private fun InventoryEntity.createdAtEpochDay(): Long =
        java.time.Instant.ofEpochMilli(createdAtMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

    /** 桌面插件的一次性快照，取数规则与 [observeScheduledBetween] 一致。 */
    suspend fun scheduledSnapshot(from: LocalDate, to: LocalDate): List<InventoryEntity> =
        dao.findScheduled(from.toEpochDay(), to.toEpochDay())

    suspend fun activeRootSnapshot(): List<InventoryEntity> = dao.findActiveRoots()

    fun observeChildren(parentId: Long): Flow<List<InventoryEntity>> =
        dao.observeChildren(parentId)

    fun observeTrash(): Flow<List<InventoryEntity>> = dao.observeTrash()

    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    fun search(query: String): Flow<List<InventoryEntity>> = dao.search(query)

    suspend fun findById(id: Long): InventoryEntity? = dao.findById(id)

    /** 导出用：整表快照，含回收站里的软删除事项。 */
    suspend fun findAllIncludingTrash(): List<InventoryEntity> = dao.findAllIncludingDeleted()

    /** 导出用：标签与事项-标签关系，两条一起取才不会在备份里留下「事项引用了不存在的标签」。 */
    suspend fun findAllTags(): List<TagEntity> = dao.findAllTags()

    suspend fun findAllTagLinks(): List<InventoryTagCrossRef> = dao.findAllLinks()

    suspend fun upsert(inventory: InventoryEntity): Long {
        val normalized = inventory.copy(
            quadrantIndex = deriveQuadrant(inventory).ordinal + 1,
            updatedAtMillis = System.currentTimeMillis(),
        )
        return if (inventory.id == 0L) {
            dao.insert(normalized)
        } else {
            dao.update(normalized)
            inventory.id
        }
    }

    suspend fun setDone(id: Long, done: Boolean) =
        dao.setDone(
            id = id,
            done = done,
            doneAt = if (done) System.currentTimeMillis() else null,
        )

    suspend fun softDelete(id: Long) = dao.softDelete(id)

    suspend fun restore(id: Long) = dao.restore(id)

    suspend fun attachTag(id: Long, tagName: String) = dao.attachTag(id, tagName)

    fun observeTagIds(id: Long): Flow<List<Long>> = dao.observeTagIds(id)

    /** 事项上的标签名。id 为 0（还没落库）时给空流，调用方不必自己判空。 */
    fun observeTagNames(id: Long): Flow<List<String>> =
        if (id == 0L) kotlinx.coroutines.flow.flowOf(emptyList())
        else combine(dao.observeTagIds(id), dao.observeTags()) { tagIds, tags ->
            tags.filter { it.id in tagIds }.map { it.name }
        }

    /** 回收站保留 30 天，超出的定期清掉 —— 否则软删除只是把删除变成了无限堆积。 */
    suspend fun purgeExpiredTrash(now: Long = System.currentTimeMillis()) =
        dao.purgeTrashOlderThan(now - TRASH_RETENTION_MILLIS)

    suspend fun purgeAllTrash() = dao.purgeAllTrash()

    private fun deriveQuadrant(inventory: InventoryEntity): Quadrant =
        Quadrant.derive(
            importance = Importance.fromRank(inventory.importance),
            urgency = Urgency.fromRank(inventory.urgency),
        )

    companion object {
        const val TRASH_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}

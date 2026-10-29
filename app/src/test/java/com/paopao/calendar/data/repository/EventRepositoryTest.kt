package com.paopao.calendar.data.repository

import com.paopao.calendar.data.local.dao.EventDao
import com.paopao.calendar.data.local.entity.EventEntity
import com.paopao.calendar.domain.model.Recurrence
import com.paopao.calendar.domain.model.RecurrenceCodec
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 读取路径上的重复展开。
 *
 * 库里只存一条带规则的母记录，界面上看到的「每一次」全部由这里算出，
 * 所以展开错了就等于日程本身错了。
 */
class EventRepositoryTest {

    private val repo = EventRepository(NoopEventDao)

    @Test
    fun `a plain event shows on every day it spans`() {
        val trip = event(
            id = 1L,
            start = LocalDate.of(2026, 10, 20),
            end = LocalDate.of(2026, 10, 22),
        )
        listOf(
            LocalDate.of(2026, 10, 19),
            LocalDate.of(2026, 10, 20),
            LocalDate.of(2026, 10, 21),
            LocalDate.of(2026, 10, 22),
            LocalDate.of(2026, 10, 23),
        ).forEachIndexed { index, day ->
            val hits = repo.occurrencesOn(listOf(trip), day)
            assertEquals("10月${day.dayOfMonth}日命中次数", if (index in 1..3) 1 else 0, hits.size)
        }
    }

    @Test
    fun `a recurring master expands beyond its own span and keeps its id`() {
        val monday = LocalDate.of(2026, 10, 19)
        val standup = event(
            id = 7L,
            start = monday,
            recurrence = Recurrence.Weekly(
                com.paopao.calendar.domain.model.DayOfWeekMask.of(DayOfWeek.MONDAY),
            ),
        )
        val later = monday.plusWeeks(6)
        val hits = repo.occurrencesOn(listOf(standup), later)
        assertEquals(1, hits.size)
        // id 不变，只挪跨度：点进虚拟实例编辑仍然回到母记录
        assertEquals(7L, hits.single().id)
        assertEquals(later, LocalDate.ofEpochDay(hits.single().startEpochDay))
    }

    @Test
    fun `a recurring rule never fires before its anchor`() {
        val monday = LocalDate.of(2026, 10, 19)
        val standup = event(
            id = 8L,
            start = monday,
            recurrence = Recurrence.Weekly(
                com.paopao.calendar.domain.model.DayOfWeekMask.of(DayOfWeek.MONDAY),
            ),
        )
        assertTrue(repo.occurrencesOn(listOf(standup), monday.minusWeeks(1)).isEmpty())
    }

    @Test
    fun `multi-day recurrence shows on each day of its own span`() {
        val start = LocalDate.of(2026, 10, 19) // 周一
        val classSessions = event(
            id = 9L,
            start = start,
            end = start.plusDays(2), // 每次连着三天
            recurrence = com.paopao.calendar.domain.model.Recurrence.Weekly(
                com.paopao.calendar.domain.model.DayOfWeekMask.of(DayOfWeek.MONDAY),
            ),
        )
        // 周三落在「上一次周一展开」的第三天里
        val hits = repo.occurrencesOn(listOf(classSessions), start.plusDays(2))
        assertEquals(1, hits.size)
        assertEquals(start, LocalDate.ofEpochDay(hits.single().startEpochDay))
    }

    @Test
    fun `repeat until caps the expansion`() {
        val monday = LocalDate.of(2026, 10, 19)
        val until = monday.plusWeeks(2)
        val temporary = event(
            id = 10L,
            start = monday,
            recurrence = Recurrence.Weekly(
                com.paopao.calendar.domain.model.DayOfWeekMask.of(DayOfWeek.MONDAY),
            ),
            repeatUntil = until,
        )
        assertEquals(1, repo.occurrencesOn(listOf(temporary), until).size)
        assertTrue(repo.occurrencesOn(listOf(temporary), until.plusWeeks(1)).isEmpty())
    }

    @Test
    fun `month day markers cover expanded occurrences not just anchors`() {
        val monday = LocalDate.of(2026, 10, 19)
        val standup = event(
            id = 11L,
            start = monday,
            recurrence = Recurrence.Weekly(
                com.paopao.calendar.domain.model.DayOfWeekMask.of(DayOfWeek.MONDAY),
            ),
        )
        val days = repo.daysWithEvents(listOf(standup), monday, monday.plusDays(13))
        assertEquals(
            setOf(monday, monday.plusWeeks(1)),
            days,
        )
    }

    private fun event(
        id: Long,
        start: LocalDate,
        end: LocalDate = start,
        recurrence: Recurrence = Recurrence.None,
        repeatUntil: LocalDate? = null,
    ): EventEntity = EventEntity(
        id = id,
        title = "测试 $id",
        startEpochDay = start.toEpochDay(),
        endEpochDay = end.toEpochDay(),
        recurrenceText = RecurrenceCodec.encode(recurrence),
        repeatUntilEpochDay = repeatUntil?.toEpochDay(),
    )

    /** 展开发生在纯函数一侧，测试不碰数据库，所以 DAO 只需要满足类型。 */
    private object NoopEventDao : EventDao {
        override fun observeOverlapping(fromEpochDay: Long, toEpochDay: Long): Flow<List<EventEntity>> =
            flowOf(emptyList())

        override suspend fun findById(id: Long): EventEntity? = null
        override fun observeAll(): Flow<List<EventEntity>> = flowOf(emptyList())
        override suspend fun findOverlapping(fromEpochDay: Long, toEpochDay: Long): List<EventEntity> =
            emptyList()

        override suspend fun findRecurring(): List<EventEntity> = emptyList()
        override suspend fun findAll(): List<EventEntity> = emptyList()
        override fun observeRecurring(): Flow<List<EventEntity>> = flowOf(emptyList())
        override suspend fun insert(event: EventEntity): Long = event.id
        override suspend fun update(event: EventEntity) = Unit
        override suspend fun delete(event: EventEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
    }
}

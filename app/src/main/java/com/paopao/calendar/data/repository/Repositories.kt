package com.paopao.calendar.data.repository

import com.paopao.calendar.data.local.dao.CategoryDao
import com.paopao.calendar.data.local.dao.FocusDao
import com.paopao.calendar.data.local.dao.HabitDao
import com.paopao.calendar.data.local.dao.MemorialDao
import com.paopao.calendar.data.local.entity.CategoryEntity
import com.paopao.calendar.data.local.entity.FocusSessionEntity
import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.data.local.entity.MemorialEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

class HabitRepository(private val dao: HabitDao) {

    fun observeActive(): Flow<List<HabitEntity>> = dao.observeActive()

    fun observeArchived(): Flow<List<HabitEntity>> = dao.observeArchived()

    suspend fun find(id: Long): HabitEntity? = dao.findById(id)

    /** 桌面插件用的一次性快照。 */
    suspend fun findActive(): List<HabitEntity> = dao.findActive()

    suspend fun findLogs(from: LocalDate, to: LocalDate): List<com.paopao.calendar.data.local.entity.HabitLogEntity> =
        dao.findLogsInRange(from.toEpochDay(), to.toEpochDay())

    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<com.paopao.calendar.data.local.entity.HabitLogEntity>> =
        dao.observeLogsInRange(from.toEpochDay(), to.toEpochDay())

    suspend fun upsert(habit: HabitEntity): Long = if (habit.id == 0L) {
        dao.insert(habit)
    } else {
        dao.update(habit); habit.id
    }

    suspend fun toggle(habitId: Long, date: LocalDate) = dao.toggle(habitId, date.toEpochDay())

    suspend fun archive(habitId: Long) = dao.archive(habitId)

    suspend fun delete(habit: HabitEntity) = dao.delete(habit)

    /**
     * 连续打卡天数（到今天为止，或到昨天为止 —— 后者让「今天还没打」不算断）。
     */
    suspend fun streak(habitId: Long, today: LocalDate, lookbackDays: Int = 365): Int {
        val days = dao.loggedDaysBetween(habitId, today.minusDays(lookbackDays.toLong()).toEpochDay(), today.toEpochDay())
            .map { LocalDate.ofEpochDay(it) }
            .toSet()
        if (days.isEmpty()) return 0
        var cursor = if (today in days) today else today.minusDays(1)
        if (cursor !in days) return 0
        var count = 0
        while (cursor in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** 本周完成率，用来决定习惯卡片上那个环该画多少。 */
    suspend fun weeklyProgress(habit: HabitEntity, weekMonday: LocalDate): Double {
        val target = if (habit.targetPerWeek <= 0) 7 else habit.targetPerWeek
        val days = dao.loggedDaysBetween(
            habitId = habit.id,
            from = weekMonday.toEpochDay(),
            to = weekMonday.plusDays(6).toEpochDay(),
        )
        return (days.size.toDouble() / target).coerceAtMost(1.0)
    }
}

class FocusRepository(private val dao: FocusDao) {

    fun observeRecent(): Flow<List<FocusSessionEntity>> = dao.observeRecent()

    fun observeBetween(day: LocalDate): Flow<List<FocusSessionEntity>> {
        val start = day.atStartOfDay().toEpochDaySecond()
        return dao.observeBetween(
            from = start,
            to = day.plusDays(1).atStartOfDay().toEpochDaySecond(),
        )
    }

    fun observeTotalMinutes(from: LocalDate, to: LocalDate): Flow<Int> = dao.observeTotalMinutesBetween(
        from = from.atStartOfDay().toEpochDaySecond(),
        to = to.plusDays(1).atStartOfDay().toEpochDaySecond(),
    )

    suspend fun record(session: FocusSessionEntity): Long = dao.insert(session)

    suspend fun countOn(day: LocalDate): Int = dao.countBetween(
        from = day.atStartOfDay().toEpochDaySecond(),
        to = day.plusDays(1).atStartOfDay().toEpochDaySecond(),
    )

    private fun java.time.LocalDateTime.toEpochDaySecond(): Long =
        this.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

class MemorialRepository(private val dao: MemorialDao) {

    fun observeAll(): Flow<List<MemorialEntity>> = dao.observeAll()

    suspend fun find(id: Long): MemorialEntity? = dao.findById(id)

    suspend fun findAll(): List<MemorialEntity> = dao.findAll()

    suspend fun upsert(memorial: MemorialEntity): Long = if (memorial.id == 0L) {
        dao.insert(memorial)
    } else {
        dao.update(memorial); memorial.id
    }

    suspend fun delete(memorial: MemorialEntity) = dao.delete(memorial)

    /**
     * 下一次发生日与已经过天数。
     *
     * 农历纪念日的「今年是哪天」必须由历法换算得出，公历同月同日在农历里每年都不同 ——
     * 这正是把纪念日做成 `isLunar` 标记而不是直接存公历日期的原因。
     */
    fun nextOccurrence(memorial: MemorialEntity, today: LocalDate): MemorialOccurrence {
        val anchor = LocalDate.ofEpochDay(memorial.anchorEpochDay)
        if (!memorial.repeatYearly) {
            return MemorialOccurrence(
                thisYear = anchor,
                daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, anchor).toInt(),
                passed = java.time.temporal.ChronoUnit.DAYS.between(anchor, today).toInt(),
                isToday = anchor == today,
            )
        }
        val upcoming = nextInYear(memorial, anchor, today.year)?.takeIf { !it.isBefore(today) }
            ?: nextInYear(memorial, anchor, today.year + 1)
            ?: return MemorialOccurrence(thisYear = anchor, daysUntil = 0, passed = 0, isToday = false)
        return MemorialOccurrence(
            thisYear = upcoming,
            daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, upcoming).toInt(),
            passed = java.time.temporal.ChronoUnit.DAYS.between(anchor, upcoming).toInt(),
            isToday = upcoming == today,
        )
    }

    private fun nextInYear(
        memorial: MemorialEntity,
        anchor: LocalDate,
        year: Int,
    ): LocalDate? = if (memorial.isLunar) {
        LunarOccurrence.dateFor(memorial, year)
    } else {
        solarAnniversary(anchor, year)
    }
}

data class MemorialOccurrence(
    val thisYear: LocalDate,
    val daysUntil: Int,
    val passed: Int,
    val isToday: Boolean,
)

class CategoryRepository(private val dao: CategoryDao) {

    fun observeByScope(scope: Int): Flow<List<CategoryEntity>> = dao.observeByScope(scope)

    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()

    suspend fun upsert(category: CategoryEntity): Long = if (category.id == 0L) {
        dao.insert(category)
    } else {
        dao.update(category); category.id
    }

    suspend fun delete(category: CategoryEntity) = dao.delete(category)

    /** 首次启动写入预置分类，之后不再干预用户的增删。 */
    suspend fun seedDefaults() {
        dao.seedIfEmpty(CategoryEntity.SCOPE_INVENTORY, DEFAULT_INVENTORY_CATEGORIES)
        dao.seedIfEmpty(CategoryEntity.SCOPE_EVENT, DEFAULT_EVENT_CATEGORIES)
    }

    companion object {
        private val DEFAULT_INVENTORY_CATEGORIES = listOf(
            CategoryEntity(name = "工作", colorArgb = 0xFF4A6FA5.toInt(), iconKey = "work", scope = CategoryEntity.SCOPE_INVENTORY, sortOrder = 0, isSystem = true),
            CategoryEntity(name = "生活", colorArgb = 0xFF4CAF7D.toInt(), iconKey = "life", scope = CategoryEntity.SCOPE_INVENTORY, sortOrder = 1, isSystem = true),
            CategoryEntity(name = "学习", colorArgb = 0xFF9B6BC4.toInt(), iconKey = "study", scope = CategoryEntity.SCOPE_INVENTORY, sortOrder = 2, isSystem = true),
        )

        private val DEFAULT_EVENT_CATEGORIES = listOf(
            CategoryEntity(name = "日程", colorArgb = 0xFF3F51B5.toInt(), iconKey = "schedule", scope = CategoryEntity.SCOPE_EVENT, sortOrder = 0, isSystem = true),
            CategoryEntity(name = "生日", colorArgb = 0xFFE57373.toInt(), iconKey = "birthday", scope = CategoryEntity.SCOPE_EVENT, sortOrder = 1, isSystem = true),
        )
    }
}

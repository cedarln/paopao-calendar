package com.paopao.calendar.data.repository

import com.paopao.calendar.data.local.dao.CategoryDao
import com.paopao.calendar.data.local.dao.FocusDao
import com.paopao.calendar.data.local.dao.HabitDao
import com.paopao.calendar.data.local.dao.MemorialDao
import com.paopao.calendar.data.local.entity.CategoryEntity
import com.paopao.calendar.data.local.entity.FocusSessionEntity
import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.data.local.entity.HabitLogEntity
import com.paopao.calendar.data.local.entity.MemorialEntity
import com.paopao.calendar.domain.habit.HabitMath
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

class HabitRepository(private val dao: HabitDao) {

    fun observeActive(): Flow<List<HabitEntity>> = dao.observeActive()

    fun observeArchived(): Flow<List<HabitEntity>> = dao.observeArchived()

    suspend fun find(id: Long): HabitEntity? = dao.findById(id)

    /** 桌面插件用的一次性快照。 */
    suspend fun findActive(): List<HabitEntity> = dao.findActive()

    /** 导出用：含已归档，一条不漏。 */
    suspend fun findAllIncludingArchived(): List<HabitEntity> = dao.findAllIncludingArchived()

    suspend fun findLogs(from: LocalDate, to: LocalDate): List<HabitLogEntity> =
        dao.findLogsInRange(from.toEpochDay(), to.toEpochDay())

    /** 导出用：全部打卡历史，含已归档习惯的。 */
    suspend fun findAllLogs(): List<HabitLogEntity> = dao.findAllLogs()

    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<HabitLogEntity>> =
        dao.observeLogsInRange(from.toEpochDay(), to.toEpochDay())

    suspend fun findLog(habitId: Long, date: LocalDate): HabitLogEntity? =
        dao.findLog(habitId, date.toEpochDay())

    suspend fun upsert(habit: HabitEntity): Long = if (habit.id == 0L) {
        dao.insert(habit)
    } else {
        dao.update(habit); habit.id
    }

    suspend fun toggle(habitId: Long, date: LocalDate) = dao.toggle(habitId, date.toEpochDay())

    /**
     * 写这一天的备注；那天还没打卡时先补一条打卡。
     *
     * 「先打勾、回头再补一句」和「点进备注框直接写了一句」是同一个动作的两种顺序，
     * 对用户不该有区别。但反过来不成立：只写备注不打卡，会在日历上凭空显示一个已完成，
     * 所以这里补的是**完成事实本身**，不给用户留「记了但没做」的口子。
     */
    suspend fun setNote(habitId: Long, date: LocalDate, note: String) {
        val epochDay = date.toEpochDay()
        if (dao.findLog(habitId, epochDay) == null) {
            dao.log(HabitLogEntity(habitId = habitId, epochDay = epochDay, note = note))
        } else {
            dao.setNote(habitId, epochDay, note)
        }
    }

    suspend fun archive(habitId: Long) = dao.archive(habitId)

    suspend fun delete(habit: HabitEntity) = dao.delete(habit)

    /**
     * 连续打卡天数（到今天为止，或到昨天为止 —— 后者让「今天还没打」不算断）。
     *
     * 回走逻辑在 [HabitMath.streakOf]，这里只负责把区间取回来：日历、插件与总览页
     * 都要报同一个数，规则不能各写一遍。
     */
    suspend fun streak(habitId: Long, today: LocalDate, lookbackDays: Int = 365): Int {
        val days = dao.loggedDaysBetween(habitId, today.minusDays(lookbackDays.toLong()).toEpochDay(), today.toEpochDay())
            .map { LocalDate.ofEpochDay(it) }
            .toSet()
        return HabitMath.streakOf(days, today)
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

    /** 导出用：整表专注记录。 */
    suspend fun findAll(): List<FocusSessionEntity> = dao.findAll()

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

    /** 导出用：分类是历史数据的归属，删掉的（非系统）也要在备份里留痕。 */
    suspend fun findAll(): List<CategoryEntity> = dao.findAll()

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

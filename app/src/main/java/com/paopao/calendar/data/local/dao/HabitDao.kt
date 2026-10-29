package com.paopao.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.paopao.calendar.data.local.entity.FocusSessionEntity
import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.data.local.entity.HabitLogEntity
import com.paopao.calendar.data.local.entity.MemorialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits WHERE archivedAtMillis IS NULL ORDER BY startEpochDay ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    /** 桌面插件用的一次性快照。 */
    @Query("SELECT * FROM habits WHERE archivedAtMillis IS NULL ORDER BY startEpochDay ASC")
    suspend fun findActive(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE archivedAtMillis IS NOT NULL ORDER BY archivedAtMillis DESC")
    fun observeArchived(): Flow<List<HabitEntity>>

    /** 导出用：一条不漏，含已归档。 */
    @Query("SELECT * FROM habits ORDER BY id ASC")
    suspend fun findAllIncludingArchived(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun findById(id: Long): HabitEntity?

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Query("UPDATE habits SET archivedAtMillis = :now WHERE id = :id")
    suspend fun archive(id: Long, now: Long = System.currentTimeMillis())

    /** 某习惯在区间内的全部打卡日，用于算连续天数与周完成率。 */
    @Query("SELECT epochDay FROM habit_logs WHERE habitId = :habitId AND epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    suspend fun loggedDaysBetween(habitId: Long, from: Long, to: Long): List<Long>

    @Query("SELECT epochDay FROM habit_logs WHERE habitId = :habitId AND epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    fun observeLoggedDays(habitId: Long, from: Long, to: Long): Flow<List<Long>>

    /**
     * 区间内全部打卡记录。月视图与插件一次要覆盖 30 天 × N 习惯，
     * 逐日订阅的放大倍数不划算，所以给一个「整段取回、调用方自己分组」的口子。
     */
    @Query("SELECT * FROM habit_logs WHERE epochDay BETWEEN :from AND :to")
    fun observeLogsInRange(from: Long, to: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE epochDay BETWEEN :from AND :to")
    suspend fun findLogsInRange(from: Long, to: Long): List<HabitLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun log(entry: HabitLogEntity)

    /**
     * 只改备注，不动打卡事实本身。
     *
     * 不复用 `log()` 的 REPLACE：那条要求把整行读回来再写回去，而「打完卡再补一句」
     * 恰好是并发最高的一刻 —— 插件和界面谁后写谁赢，赢的那次可能带着过期的勾选状态。
     */
    @Query("UPDATE habit_logs SET note = :note WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun setNote(habitId: Long, epochDay: Long, note: String)

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun findLog(habitId: Long, epochDay: Long): HabitLogEntity?

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun unlog(habitId: Long, epochDay: Long)

    @Transaction
    suspend fun toggle(habitId: Long, epochDay: Long) {
        val existing = loggedDaysBetween(habitId, epochDay, epochDay)
        if (existing.isEmpty()) {
            log(HabitLogEntity(habitId = habitId, epochDay = epochDay))
        } else {
            unlog(habitId, epochDay)
        }
    }
}

@Dao
interface FocusDao {

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions WHERE startedAtMillis BETWEEN :from AND :to ORDER BY startedAtMillis DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAtMillis DESC LIMIT 200")
    fun observeRecent(): Flow<List<FocusSessionEntity>>

    @Query("SELECT COALESCE(SUM(actualMinutes), 0) FROM focus_sessions WHERE completed = 1 AND startedAtMillis BETWEEN :from AND :to")
    fun observeTotalMinutesBetween(from: Long, to: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE startedAtMillis BETWEEN :from AND :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MemorialDao {

    @Query("SELECT * FROM memorials ORDER BY anchorEpochDay ASC")
    fun observeAll(): Flow<List<MemorialEntity>>

    @Query("SELECT * FROM memorials ORDER BY anchorEpochDay ASC")
    suspend fun findAll(): List<MemorialEntity>

    @Query("SELECT * FROM memorials WHERE id = :id")
    suspend fun findById(id: Long): MemorialEntity?

    @Insert
    suspend fun insert(memorial: MemorialEntity): Long

    @Update
    suspend fun update(memorial: MemorialEntity)

    @Delete
    suspend fun delete(memorial: MemorialEntity)
}

package com.paopao.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.paopao.calendar.data.local.entity.CategoryEntity
import com.paopao.calendar.data.local.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    /**
     * 区间重叠查询：跨天的日程只要与请求区间有交集就该出现，
     * 所以条件是「结束日不早于区间起点 且 开始日不晚于区间终点」，不是 BETWEEN start。
     */
    @Query(
        """
        SELECT * FROM events
        WHERE endEpochDay >= :fromEpochDay AND startEpochDay <= :toEpochDay
        ORDER BY startEpochDay ASC, startMillis ASC
        """
    )
    fun observeOverlapping(fromEpochDay: Long, toEpochDay: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun findById(id: Long): EventEntity?

    @Query("SELECT * FROM events ORDER BY startEpochDay ASC")
    fun observeAll(): Flow<List<EventEntity>>

    /** 同上条件，但取一次性快照：日视图与提醒调度需要「此刻的值」而不是持续流。 */
    @Query(
        """
        SELECT * FROM events
        WHERE endEpochDay >= :fromEpochDay AND startEpochDay <= :toEpochDay
        ORDER BY startEpochDay ASC, startMillis ASC
        """
    )
    suspend fun findOverlapping(fromEpochDay: Long, toEpochDay: Long): List<EventEntity>

    /**
     * 所有带重复规则的母记录。
     *
     * 单靠 [findOverlapping] 找不到它们：一条「每周一」的日程，母记录的跨度只有
     * 锚点那一天，看三个月后的周一时区间查询会漏掉它。
     */
    @Query("SELECT * FROM events WHERE recurrenceText <> 'none'")
    suspend fun findRecurring(): List<EventEntity>

    /** [findRecurring] 的持续流版本，界面侧展开重复规则时用它。 */
    @Query("SELECT * FROM events WHERE recurrenceText <> 'none'")
    fun observeRecurring(): Flow<List<EventEntity>>

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE scope = :scope ORDER BY sortOrder ASC, name ASC")
    fun observeByScope(scope: Int): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY scope ASC, sortOrder ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories WHERE scope = :scope")
    suspend fun countByScope(scope: Int): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    /** 预置分类只在表为空时写入，避免每次冷启动都重复插一遍。 */
    @Transaction
    suspend fun seedIfEmpty(scope: Int, seed: List<CategoryEntity>) {
        if (countByScope(scope) == 0) {
            seed.forEach { insert(it) }
        }
    }
}

package com.paopao.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.paopao.calendar.data.local.entity.InventoryEntity
import com.paopao.calendar.data.local.entity.InventoryTagCrossRef
import com.paopao.calendar.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventories WHERE deletedAtMillis IS NULL AND parentId IS NULL ORDER BY sortOrder ASC, dueEpochDay ASC")
    fun observeRootActive(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventories WHERE deletedAtMillis IS NULL AND parentId = :parentId ORDER BY sortOrder ASC")
    fun observeChildren(parentId: Long): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventories WHERE deletedAtMillis IS NULL AND dueEpochDay = :epochDay ORDER BY done ASC, dueMinuteOfDay ASC")
    fun observeDueOn(epochDay: Long): Flow<List<InventoryEntity>>

    /** 日历网格的密度点要一次性覆盖 42 天，逐日查会打出 42 次订阅。 */
    @Query(
        """
        SELECT * FROM inventories
        WHERE deletedAtMillis IS NULL AND dueEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        ORDER BY dueEpochDay ASC, dueMinuteOfDay ASC
        """
    )
    fun observeDueBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<InventoryEntity>>

    /** 所有带重复规则的活跃事项，日历网格按规则展开时与 [observeDueBetween] 合并使用。 */
    @Query("SELECT * FROM inventories WHERE deletedAtMillis IS NULL AND recurrenceText <> 'none'")
    fun observeRecurring(): Flow<List<InventoryEntity>>

    /** 桌面插件的一次性快照：与 [observeDueBetween] + [observeRecurring] 同一条取数规则。 */
    @Query(
        """
        SELECT * FROM inventories
        WHERE deletedAtMillis IS NULL AND (dueEpochDay BETWEEN :fromEpochDay AND :toEpochDay OR recurrenceText <> 'none')
        ORDER BY dueEpochDay ASC, dueMinuteOfDay ASC
        """
    )
    suspend fun findScheduled(fromEpochDay: Long, toEpochDay: Long): List<InventoryEntity>

    /** 四象限插件用：只取活跃未完成顶层事项，象限分组交给内存。 */
    @Query("SELECT * FROM inventories WHERE deletedAtMillis IS NULL AND done = 0 AND parentId IS NULL ORDER BY dueEpochDay ASC, id DESC")
    suspend fun findActiveRoots(): List<InventoryEntity>

    /**
     * 象限筛选走 SQL 而不是取全量再在内存里 filter：
     * 事项列表是唯一会随使用时间线性变长的表，两年后就是几千行。
     * [quadrant] 传 0 表示不限象限。
     */
    @Query(
        """
        SELECT * FROM inventories
        WHERE deletedAtMillis IS NULL
          AND parentId IS NULL
          AND (:showDone OR done = 0)
          AND (:quadrant = 0 OR quadrantIndex = :quadrant)
        ORDER BY done ASC, dueEpochDay ASC, id DESC
        """
    )
    fun observeByQuadrant(quadrant: Int, showDone: Boolean): Flow<List<InventoryEntity>>

    @Query(
        """
        SELECT * FROM inventories
        WHERE deletedAtMillis IS NULL AND (title LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%')
        ORDER BY updatedAtMillis DESC LIMIT 200
        """
    )
    fun search(query: String): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventories WHERE id = :id")
    suspend fun findById(id: Long): InventoryEntity?

    /**
     * 导出备份用：整表快照，含回收站里还没过期的软删除事项。
     *
     * 不看 deletedAtMillis 也不看 parentId：备份的职责是「换机之后一模一样」，
     * 在导出侧再筛一遍等于替用户决定哪些数据他没资格带走。
     */
    @Query("SELECT * FROM inventories ORDER BY id ASC")
    suspend fun findAllIncludingDeleted(): List<InventoryEntity>

    @Query("SELECT * FROM inventories WHERE deletedAtMillis IS NOT NULL ORDER BY deletedAtMillis DESC")
    fun observeTrash(): Flow<List<InventoryEntity>>

    @Query("SELECT COUNT(*) FROM inventories WHERE deletedAtMillis IS NULL AND done = 0")
    fun observeActiveCount(): Flow<Int>

    @Insert
    suspend fun insert(inventory: InventoryEntity): Long

    @Update
    suspend fun update(inventory: InventoryEntity)

    @Delete
    suspend fun delete(inventory: InventoryEntity)

    /**
     * 软删除：写入 deletedAtMillis 而不是真删。
     * 「删错了」在待办类应用里高频到必须有撤销，而真删之后撤销只能靠重新输入。
     */
    @Query("UPDATE inventories SET deletedAtMillis = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE inventories SET deletedAtMillis = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("UPDATE inventories SET done = :done, doneAtMillis = :doneAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?)

    @Query("DELETE FROM inventories WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :threshold")
    suspend fun purgeTrashOlderThan(threshold: Long)

    /** 清空回收站：与 [purgeTrashOlderThan] 分开，因为「过期清理」和「用户主动清空」语义不同。 */
    @Query("DELETE FROM inventories WHERE deletedAtMillis IS NOT NULL")
    suspend fun purgeAllTrash()

    // ---- 标签 ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    /** 导出备份用：标签表与关系表都要整表，缺一个事项就丢了它的分类线。 */
    @Query("SELECT * FROM tags ORDER BY id ASC")
    suspend fun findAllTags(): List<TagEntity>

    @Query("SELECT * FROM inventory_tags ORDER BY inventoryId ASC, tagId ASC")
    suspend fun findAllLinks(): List<InventoryTagCrossRef>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(crossRef: InventoryTagCrossRef)

    @Query("DELETE FROM inventory_tags WHERE inventoryId = :inventoryId AND tagId = :tagId")
    suspend fun unlink(inventoryId: Long, tagId: Long)

    @Query("SELECT tagId FROM inventory_tags WHERE inventoryId = :inventoryId")
    fun observeTagIds(inventoryId: Long): Flow<List<Long>>

    /** 给事项打标签：同名标签复用已有行，避免标签表被 "工作"、"工作 "、"工作  " 撑爆。 */
    @Transaction
    suspend fun attachTag(inventoryId: Long, tagName: String) {
        val existing = findTagByName(tagName.trim())
        val tagId = existing?.id ?: insertTag(TagEntity(name = tagName.trim()))
        link(InventoryTagCrossRef(inventoryId = inventoryId, tagId = tagId))
    }
}

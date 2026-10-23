package com.paopao.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 事项 —— 本项目的核心对象，四象限挂在它身上。
 *
 * 存 [importance] 与 [urgency] 两个轴，象限是派生值：
 * 只存「Q1..Q4」的话，用户把一件事从「重要不紧急」改成「重要且紧急」时，
 * 原先那条判断就永久丢失了，而无法回答「我这周有多少事是从 Q2 掉进 Q1 的」
 * —— 那恰恰是时间管理里最值得看的一个指标。
 */
@Entity(
    tableName = "inventories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = InventoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("parentId"),
        Index("categoryId"),
        Index("dueEpochDay"),
        Index("done"),
        Index("deletedAtMillis"),
        Index("quadrantIndex"),
    ],
)
data class InventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    /** 1=无所谓 2=一般 3=重要。 */
    val importance: Int = 2,
    /** 1=不急 2=近期 3=紧急。 */
    val urgency: Int = 2,
    /**
     * [com.paopao.calendar.domain.model.Quadrant] 的序号 1..4。
     *
     * 这是 importance×urgency 的物化结果，写入时由仓储层用同一份 Kotlin 逻辑算出，
     * 唯一作用是让象限筛选能用上索引 —— 判定规则的真值来源仍然只有 [importance]/[urgency]。
     */
    val quadrantIndex: Int = 2,
    val done: Boolean = false,
    val doneAtMillis: Long? = null,
    val dueEpochDay: Long? = null,
    /** 当日分钟数，null 表示只看日期不看时刻。 */
    val dueMinuteOfDay: Int? = null,
    val recurrenceText: String = "none",
    val categoryId: Long? = null,
    /** 子事项指向父事项；顶层为 null。 */
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    /** 软删除时间戳：撤销删除靠它，非空即在回收站。 */
    val deletedAtMillis: Long? = null,
)

/** 标签，多对多挂在事项上。分类是单选、标签是多选，二者不是一回事。 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "inventory_tags",
    primaryKeys = ["inventoryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = InventoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId"), Index("inventoryId")],
)
data class InventoryTagCrossRef(
    val inventoryId: Long,
    val tagId: Long,
)

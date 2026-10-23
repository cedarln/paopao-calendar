package com.paopao.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 日程（日历上占据一天的东西）。与 [InventoryEntity] 分表而不是同表加 type 字段：
 * 日程的本质是「时间区间」，事项的本质是「待完成」，两者的可空性、排序与索引需求都不同，
 * 合成一张表会让一半列常年为 null。
 *
 * 日期一律用 epochDay（Long）而非字符串：区间查询 `BETWEEN` 在整数上才走得动索引。
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("startEpochDay"),
        Index("categoryId"),
    ],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    /** 首日。全天日程也用这个字段。 */
    val startEpochDay: Long,
    /** 末日（含）。单日日程等于 [startEpochDay]。 */
    val endEpochDay: Long,
    /** 非全天时的开始/结束时间戳；null 表示全天。 */
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val allDay: Boolean = true,
    val recurrenceText: String = "none",
    /**
     * 重复规则的截止日（含），null 表示无限期。
     *
     * 不复用 [endEpochDay]：那一列在「连续三天的出差」这类事件里表示事件本身的跨度，
     * 让它同时兼任重复截止日，会让多日重复事件无法表达。
     */
    val repeatUntilEpochDay: Long? = null,
    val remindMinutes: Int = NO_REMINDER,
    val colorArgb: Int = 0,
    val categoryId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    companion object {
        const val NO_REMINDER = -1
    }
}

/** 分类。日程与事项共用一套分类表，靠 [scope] 区分归属。 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["scope", "name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val iconKey: String,
    val scope: Int = SCOPE_INVENTORY,
    val sortOrder: Int = 0,
    /** 预置分类不允许删除，删除会让历史数据失去归属。 */
    val isSystem: Boolean = false,
) {
    companion object {
        const val SCOPE_EVENT = 0
        const val SCOPE_INVENTORY = 1
        const val SCOPE_HABIT = 2
    }
}

package com.paopao.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 习惯定义。 */
@Entity(
    tableName = "habits",
    indices = [Index("categoryId"), Index("archivedAtMillis")],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val iconKey: String,
    /** 每周目标次数，0 表示「每天」。 */
    val targetPerWeek: Int = 7,
    val startEpochDay: Long,
    /**
     * 结束日（含当天）；null 是「无期限」。
     *
     * 用可空而不是「9999-12-31」这类哨兵值：SQLite 里 NULL 与真实日期在查询条件上是同一套语义，
     * 而哨兵值要求每个比较它的地方都记得特殊处理 —— 少处理一处就多一个「过期习惯还在日历里」的 bug。
     * v2 装旧版的设备走 MIGRATION_2_3 加这一列，老习惯落 NULL，正好等于「一直有效」，
     * 不需要为升级再补一次数据修复。
     */
    val endEpochDay: Long? = null,
    val categoryId: Long? = null,
    val archivedAtMillis: Long? = null,
)

/**
 * 打卡记录。主键是 (habitId, epochDay) 而不是自增 id：
 * 一天一条是业务约束，用复合主键让它变成数据库层面的硬约束，
 * 比在代码里「先查再插」可靠 —— 后者在插件和主界面同时刷新时会写出重复行。
 */
@Entity(
    tableName = "habit_logs",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("epochDay")],
)
data class HabitLogEntity(
    val habitId: Long,
    val epochDay: Long,
    /**
     * 完成时写的备注。列从 v1 就在，但一直没有任何读写入口 ——
     * 「打卡时顺手记一句，以后回来翻」才是这条记录真正的用处，
     * 只留一个勾的话半年后根本想不起来那天到底做了什么。
     */
    val note: String = "",
)

/**
 * 专注（番茄）记录。
 *
 * [plannedMinutes] 与 [actualMinutes] 分开存：用户中途放弃是常态，
 * 只存实际时长就没法统计「计划达成率」这个真正有意义的指标。
 */
@Entity(
    tableName = "focus_sessions",
    indices = [Index("startedAtMillis"), Index("inventoryId")],
    foreignKeys = [
        ForeignKey(
            entity = InventoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val mode: Int = MODE_WORK,
    val completed: Boolean = false,
    val inventoryId: Long? = null,
) {
    companion object {
        const val MODE_WORK = 0
        const val MODE_REST = 1
    }
}

/**
 * 纪念日。[anchorEpochDay] 是「第一次发生」的那一天，之后按 [repeatYearly]/[isLunar] 推算。
 *
 * 支持按农历重复是给长辈生日用的 —— 那才是中国式纪念日的真实需求。
 */
@Entity(
    tableName = "memorials",
    indices = [Index("anchorEpochDay")],
)
data class MemorialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val anchorEpochDay: Long,
    val isLunar: Boolean = false,
    val repeatYearly: Boolean = true,
    val colorArgb: Int,
    val iconKey: String,
    val note: String = "",
    val remindDaysBefore: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

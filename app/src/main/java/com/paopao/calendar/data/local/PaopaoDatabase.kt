package com.paopao.calendar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.paopao.calendar.data.local.dao.CategoryDao
import com.paopao.calendar.data.local.dao.EventDao
import com.paopao.calendar.data.local.dao.FocusDao
import com.paopao.calendar.data.local.dao.HabitDao
import com.paopao.calendar.data.local.dao.InventoryDao
import com.paopao.calendar.data.local.dao.MemorialDao
import com.paopao.calendar.data.local.dao.WorkoutDao
import com.paopao.calendar.data.local.entity.CategoryEntity
import com.paopao.calendar.data.local.entity.EventEntity
import com.paopao.calendar.data.local.entity.FocusSessionEntity
import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.data.local.entity.HabitLogEntity
import com.paopao.calendar.data.local.entity.InventoryEntity
import com.paopao.calendar.data.local.entity.InventoryTagCrossRef
import com.paopao.calendar.data.local.entity.MemorialEntity
import com.paopao.calendar.data.local.entity.PlanActionEntity
import com.paopao.calendar.data.local.entity.WorkoutLogEntity
import com.paopao.calendar.data.local.entity.WorkoutPlanEntity
import com.paopao.calendar.data.local.entity.TagEntity

/**
 * 单库单文件。
 *
 * 从 version 2 起每一次 schema 改动都必须配一条 [androidx.room.migration.Migration]：
 * v0.1.0 已经作为 GitHub Release 发出去了，装了旧版的设备不能被 destructive 重建洗掉数据。
 * 迁移语句与 `schemas/` 下导出的 `creationSql` 保持一致，否则 Room 的 identity hash 校验
 * 会在运行期直接抛 IllegalStateException。
 */
@Database(
    entities = [
        EventEntity::class,
        CategoryEntity::class,
        InventoryEntity::class,
        TagEntity::class,
        InventoryTagCrossRef::class,
        HabitEntity::class,
        HabitLogEntity::class,
        FocusSessionEntity::class,
        MemorialEntity::class,
        WorkoutPlanEntity::class,
        PlanActionEntity::class,
        WorkoutLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class PaopaoDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun categoryDao(): CategoryDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun habitDao(): HabitDao
    abstract fun focusDao(): FocusDao
    abstract fun memorialDao(): MemorialDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val NAME = "paopao.db"
    }
}

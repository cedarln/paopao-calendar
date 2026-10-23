package com.paopao.calendar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.paopao.calendar.data.local.dao.CategoryDao
import com.paopao.calendar.data.local.dao.EventDao
import com.paopao.calendar.data.local.dao.FocusDao
import com.paopao.calendar.data.local.dao.HabitDao
import com.paopao.calendar.data.local.dao.InventoryDao
import com.paopao.calendar.data.local.dao.MemorialDao
import com.paopao.calendar.data.local.entity.CategoryEntity
import com.paopao.calendar.data.local.entity.EventEntity
import com.paopao.calendar.data.local.entity.FocusSessionEntity
import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.data.local.entity.HabitLogEntity
import com.paopao.calendar.data.local.entity.InventoryEntity
import com.paopao.calendar.data.local.entity.InventoryTagCrossRef
import com.paopao.calendar.data.local.entity.MemorialEntity
import com.paopao.calendar.data.local.entity.TagEntity

/**
 * 单库单文件。
 *
 * `version = 1` 且不写 migration 是刻意的：一旦对外发布，每一次 schema 改动都要配
 * 一条 migration 加一个 schema 测试。开发期靠 [exportSchema] 产出的 JSON 比对来兜底，
 * 首版发布前把 `fallbackToDestructiveMigration` 关掉。
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class PaopaoDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun categoryDao(): CategoryDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun habitDao(): HabitDao
    abstract fun focusDao(): FocusDao
    abstract fun memorialDao(): MemorialDao

    companion object {
        const val NAME = "paopao.db"
    }
}

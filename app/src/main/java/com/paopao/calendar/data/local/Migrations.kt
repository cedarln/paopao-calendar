package com.paopao.calendar.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2：新增训练三表（计划、计划动作、打卡）。
 *
 * 语句逐字抄自 `app/schemas/…/2.json` 里 Room 生成的 `createSql`，不是手写的近似版本。
 * 差别在于：Room 会按建表语句算一个 identity hash，装旧版的设备升级后第一次打开库时
 * 拿它比对，任何一个字段的 NOT NULL 或索引名不一致都会抛 `IllegalStateException` ——
 * 而那一刻用户手上已经是发出去的包，改不回来了。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `bodyPart` TEXT NOT NULL, `weekMask` INTEGER NOT NULL, " +
                "`estimatedMinutes` INTEGER NOT NULL, `colorArgb` INTEGER NOT NULL, `iconKey` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, `sourceId` TEXT, `createdAtMillis` INTEGER NOT NULL, " +
                "`archivedAtMillis` INTEGER)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_plans_archivedAtMillis` " +
                "ON `workout_plans` (`archivedAtMillis`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_plans_sourceId` ON `workout_plans` (`sourceId`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `plan_actions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`planId` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`sets` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `durationSec` INTEGER NOT NULL, " +
                "`restSec` INTEGER NOT NULL, `exerciseKey` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                "FOREIGN KEY(`planId`) REFERENCES `workout_plans`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_plan_actions_planId` ON `plan_actions` (`planId`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, `planId` INTEGER, `planName` TEXT NOT NULL, " +
                "`durationSec` INTEGER NOT NULL, `startedAtMillis` INTEGER, `sourceId` TEXT, " +
                "`note` TEXT NOT NULL, " +
                "FOREIGN KEY(`planId`) REFERENCES `workout_plans`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_logs_epochDay` ON `workout_logs` (`epochDay`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_logs_planId` ON `workout_logs` (`planId`)",
        )
    }
}

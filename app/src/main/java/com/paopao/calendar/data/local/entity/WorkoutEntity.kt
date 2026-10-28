package com.paopao.calendar.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * 训练计划。
 *
 * 数据形态来自作者自己的健身小程序 fit-miniapp，但去掉了 openid 与 members 两列：
 * 那是微信云开发里的跨用户身份与「一起练」关系，单机应用没有对应物。
 *
 * [weekMask] 用 [com.paopao.calendar.domain.model.DayOfWeekMask] 的位口径
 * （bit0=周一……bit6=周日），而不是小程序原来的 `getDay()` 数组（周日=0）——
 * 一次转换在导入侧做完，库里只留一种口径，省掉每一次读取都要猜的麻烦。
 */
@Entity(
    tableName = "workout_plans",
    indices = [Index("archivedAtMillis"), Index("sourceId")],
)
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 部位标签，自由文本：肩/背/胸/腿/臀/腹/手臂/核心/全身之外允许自定义。 */
    val bodyPart: String,
    val weekMask: Int = 0b111_1111,
    /** 预计总时长，单位分钟；由动作估算而来，也允许用户手改。 */
    val estimatedMinutes: Int = 0,
    val colorArgb: Int,
    val iconKey: String = DEFAULT_ICON,
    val note: String = "",
    /**
     * 外部来源的原始标识。本机新建的计划是 null。
     *
     * 现在没人往里写：小程序云库的 `_id` 只在有朝一日做数据导入时才有意义。
     * 与其到时候改表加列，不如现在留一个空位。
     */
    val sourceId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val archivedAtMillis: Long? = null,
) {
    companion object {
        const val DEFAULT_ICON = "workout"
    }
}

/**
 * 计划里的一条动作。
 *
 * [reps] 与 [durationSec] 是互斥的两种计量：次数动作用 reps，计时动作（平板支撑这类）
 * 用 durationSec 且 reps=0。拆成两个可空列反而要解释「哪个才算数」，所以按 fit-miniapp
 * 的既有约定用 0 表示「不适用」。
 */
@Entity(
    tableName = "plan_actions",
    indices = [Index("planId")],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlanActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val sortOrder: Int,
    val name: String,
    val sets: Int = 3,
    /** 0 表示计时动作。 */
    val reps: Int = 0,
    val durationSec: Int = 60,
    val restSec: Int = 30,
    /** 命中动作库时记下条目标识，供详情页跳百科；自定义动作留空。 */
    val exerciseKey: String = "",
    val note: String = "",
)

/**
 * 一次训练打卡。
 *
 * 与 habit_logs 不同，这里一天可以有多条（早晚两练是健身场景的常态），
 * 所以用自增主键 + epochDay 索引，而不是复合主键。
 *
 * [planName] 是故意冗余的：计划可以被删掉，但「那天练了什么」属于历史，
 * 不该跟着一起消失。planId 用 SET_NULL 保留这条记录的可追溯性。
 */
@Entity(
    tableName = "workout_logs",
    indices = [Index("epochDay"), Index("planId")],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class WorkoutLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val planId: Long? = null,
    val planName: String,
    /** 实际训练时长（秒）。来自计时器，手动补记时按计划的估计值填。 */
    val durationSec: Int = 0,
    val startedAtMillis: Long? = null,
    val sourceId: String? = null,
    val note: String = "",
) {
    companion object {
        /** 自由训练（没有对应计划）时写进 [planName] 的占位名。 */
        const val FALLBACK_NAME = "自由训练"
    }
}

/** 计划连同它的动作，编辑页与详情页都按这个粒度取。 */
data class PlanWithActions(
    @Embedded val plan: WorkoutPlanEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "planId",
    )
    val actions: List<PlanActionEntity>,
)

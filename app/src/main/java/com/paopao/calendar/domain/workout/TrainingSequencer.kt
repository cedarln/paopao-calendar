package com.paopao.calendar.domain.workout

import com.paopao.calendar.data.local.entity.PlanActionEntity

/**
 * 计时器要跑的那一串节拍。
 *
 * 单独抽出来是为了能在 JVM 上断言：一个计划展开成多少段、休息放在哪里、最后一组后面
 * 不该再跟一段休息 —— 这些全靠状态机跑一遍才能发现，而在真机上跑 40 分钟才发现更糟。
 */
data class TimerStep(
    /** 动作名，休息段仍然标它属于哪个动作，用户看得到「平板支撑 休息」。 */
    val label: String,
    /** 第几组 / 目标次数这类副标题；休息段是「喘口气」。 */
    val detail: String,
    val seconds: Int,
    val isRest: Boolean,
)

object TrainingSequencer {

    /**
     * 按计划动作展开成 工作→休息→工作… 的线性节拍。
     *
     * 组间休息只在「后面还有事情要做」时插入：整场最后一次工作后面接一段休息，
     * 用户会以为训练还没结束，这是计时类应用最讨人厌的一个细节。
     */
    fun steps(actions: List<PlanActionEntity>): List<TimerStep> {
        val steps = ArrayList<TimerStep>()
        // 用下标而不是 action == actions.last() 判断收尾：两条完全同形的动作（同名同组同息）
        // 在 data class 的 equals 下是相等的，那种比较会把前一条也当成最后一条，休息就丢了。
        actions.forEachIndexed { actionIndex, action ->
            val sets = action.sets.coerceAtLeast(1)
            val workSec = TrainingMath.setWorkSec(action.durationSec, sets).coerceAtLeast(1)
            for (set in 1..sets) {
                steps += TimerStep(
                    label = action.name.ifBlank { "未命名动作" },
                    detail = if (action.reps > 0) "第 $set/$sets 组 · ${action.reps} 次" else "第 $set/$sets 组 · ${workSec} 秒",
                    seconds = workSec,
                    isRest = false,
                )
                val isLastStep = actionIndex == actions.lastIndex && set == sets
                if (action.restSec > 0 && !isLastStep) {
                    steps += TimerStep(
                        label = action.name.ifBlank { "未命名动作" },
                        detail = "休息 ${action.restSec} 秒",
                        seconds = action.restSec,
                        isRest = true,
                    )
                }
            }
        }
        return steps
    }

    /** 整场预计总秒数：与 [steps] 的口径一致，用于「预计 X 分钟」的显示。 */
    fun totalSeconds(actions: List<PlanActionEntity>): Int = steps(actions).sumOf { it.seconds }
}

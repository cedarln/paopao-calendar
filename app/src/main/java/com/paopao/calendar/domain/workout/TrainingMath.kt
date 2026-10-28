package com.paopao.calendar.domain.workout

import com.paopao.calendar.data.local.entity.WorkoutLogEntity
import com.paopao.calendar.data.local.entity.WorkoutPlanEntity
import com.paopao.calendar.domain.model.DayOfWeekMask
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 训练相关的纯计算。
 *
 * 全部来自 fit-miniapp 里已经在跑的算法，逐条保持口径一致 —— 这些数字用户看的是「连续性」
 * 和「大概消耗」，换了公式会让同一份打卡历史突然变成另一个故事。
 */
object TrainingMath {

    /** 一次动作的用时：小程序里 durationSec 已经是「所有组加起来」的总秒数，所以只再加一次组间休息。 */
    fun actionSeconds(durationSec: Int, restSec: Int): Int = durationSec + restSec

    fun totalSeconds(actionSeconds: List<Int>): Int = actionSeconds.sum()

    /**
     * 预计时长（分钟）。
     *
     * `max(1, round(秒/60))` 与 parsePlan 的 normalize() 一致：一组平板支撑也可能不到 30 秒，
     * 显示成 0 分钟会被读成「没练」。
     */
    fun estimatedMinutes(totalSec: Int): Int = max(1, (totalSec / 60f).roundToInt())

    /**
     * 次数动作没有写时长时的估算：每次约 5 秒 × 次数 × 组数。
     *
     * 5 秒/次是小程序原文案里写明的经验值（4组×15次≈300秒），照搬不改 —— 它偏松，
     * 但作为「预计」而不是「记录」，宁松不紧。
     */
    fun estimateDurationSec(reps: Int, sets: Int): Int = reps * 5 * sets

    /**
     * 单组的工作秒数，计时器按组推进时用。
     *
     * 注意与 [totalSeconds] 的口径差别：库里 `durationSec` 存的是**这个动作所有组的总时长**
     * （小程序的估算公式 `totalSec = Σ(durationSec + restSec)` 就是这么算的），
     * 所以一组时长要除回组数。除不尽时按四舍五入给每一组，全动作累计可能差几秒 ——
     * 计时器落库的是实际耗时，这个误差只影响每组显示，不影响任何统计数字。
     * 每组下限取 1 秒，免得状态机停在「工作 0 秒」上看着像卡死；唯一例外是 `durationSec`
     * 本来就是 0 的坏数据，那时原样返回 0，不在这里把数据问题伪装成一次真实训练。
     */
    fun setWorkSec(durationSec: Int, sets: Int): Int {
        if (durationSec <= 0) return 0
        val groupCount = max(1, sets)
        return if (groupCount == 1) {
            durationSec
        } else {
            (durationSec.toFloat() / groupCount).roundToInt().coerceAtLeast(1)
        }
    }

    /** 热量估算：每分钟 6.5 千卡，取整。同 report.js。 */
    fun kilocalories(minutes: Int): Int = (minutes * 6.5f).roundToInt()

    /**
     * 「一键打卡」（不进计时页、按下去就算练了）写入的秒数。
     *
     * 取计划的预计分钟数，空白计划兜到 1 分钟：0 秒的日志在热力图上什么都不会亮，
     * 用户按了却看不到反馈，等于这次打卡白按。
     */
    fun quickCheckInSec(estimatedMinutes: Int): Int = max(1, estimatedMinutes) * 60

    /**
     * 连续打卡天数。
     *
     * 今天没打不算断（从昨天起算），与历史页/报告页原本的行为一致：
     * 白天没练、晚上才补的用户不该在中午刷新时看到连胜清零。
     * 上限 365 是原实现里的回看窗口，保留：再往前的断链本就已经断了。
     */
    fun streak(loggedDays: Set<LocalDate>, today: LocalDate, lookbackDays: Int = 365): Int {
        if (loggedDays.isEmpty()) return 0
        var cursor = if (today in loggedDays) today else today.minusDays(1)
        if (cursor !in loggedDays) return 0
        var count = 0
        while (cursor in loggedDays && count < lookbackDays) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /**
     * 某天是否排在计划里。
     *
     * `weekMask == 0` 读成「没安排任何一天」而不是「每天」：默认值已经是全周，
     * 真出现 0 只能是数据被写坏了，此时宁可什么都不冒出来，也不要在日历上每天提醒一遍。
     */
    fun isScheduledOn(weekMask: Int, date: LocalDate): Boolean =
        weekMask != 0 && DayOfWeekMask(weekMask).contains(date.dayOfWeek)

    /**
     * 选中日「排了但还没打卡」的计划。
     *
     * 已打卡的要摘掉：同一天连按两次「打卡」会写两条日志，而连续天数只看天，
     * 多出来的那条只会让当天时长虚高。
     */
    fun plansToCheckIn(
        plans: List<WorkoutPlanEntity>,
        logs: List<WorkoutLogEntity>,
        date: LocalDate,
    ): List<WorkoutPlanEntity> {
        val epochDay = date.toEpochDay()
        val donePlanIds = logs.filter { it.epochDay == epochDay }.mapNotNull { it.planId }.toSet()
        return plans.filter { it.id !in donePlanIds && isScheduledOn(it.weekMask, date) }
    }

    /**
     * 把 JS `Date.getDay()` 口径（0=周日……6=周六）转成 [DayOfWeekMask]（bit0=周一……bit6=周日）。
     *
     * 两套编号在周六差 1、周日差 6，靠记忆换算迟早出错，所以把转换收在这一个函数里，
     * 顺带承担「将来的导入器要处理小程序导出的 weekDays」这件事。
     */
    fun weekMaskFromJsDayNumbers(jsDays: Collection<Int>): DayOfWeekMask {
        var mask = DayOfWeekMask.NONE
        jsDays.forEach { day ->
            val javaDay = when (day) {
                0 -> java.time.DayOfWeek.SUNDAY
                in 1..6 -> java.time.DayOfWeek.of(day)
                else -> return@forEach      // 越界编号直接忽略，不让脏数据把整周撑爆
            }
            mask = mask.plus(javaDay)
        }
        return mask
    }
}

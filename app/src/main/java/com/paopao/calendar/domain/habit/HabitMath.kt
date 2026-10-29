package com.paopao.calendar.domain.habit

import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.data.local.entity.HabitLogEntity
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 习惯的派生计算：在不在有效期内、当天显不显示、周/月/年完成率、连续天数。
 *
 * 全部是纯函数，一个 Context 都不碰 —— 这不只是为了能写单测：同一段「今天该不该露脸」的判断
 * 要在日历日视图、月视图标记、桌面插件、总览页四个地方出现，只要有一处自己重写一遍规则，
 * 就会出现「插件里能打卡但日历里看不到」这类两边各执一词的 bug。规则放这里，四个调用方共用。
 */
object HabitMath {

    /**
     * 习惯在 [date] 这天是否还在有效期内（起止都含端点）。
     *
     * `endEpochDay == null` 是「无期限」，不是 0 也不是某个哨兵日期 —— 见 [HabitEntity.endEpochDay]。
     */
    fun isActiveOn(habit: HabitEntity, date: LocalDate): Boolean {
        val epochDay = date.toEpochDay()
        if (epochDay < habit.startEpochDay) return false
        val end = habit.endEpochDay ?: return true
        return epochDay <= end
    }

    /** 已经结束（结束日在 [today] 之前）；无期限的习惯永远返回 false。 */
    fun isFinished(habit: HabitEntity, today: LocalDate): Boolean {
        val end = habit.endEpochDay ?: return false
        return end < today.toEpochDay()
    }

    /** 还没开始：日历与插件都不该提前提示一个未来才生效的习惯。 */
    fun isUpcoming(habit: HabitEntity, today: LocalDate): Boolean =
        today.toEpochDay() < habit.startEpochDay

    /**
     * 某一天「已完成」的习惯，按 habit 本身的顺序（仓储给的是开始日升序）。
     *
     * 只有打了卡才出现在结果里 —— 这是用户定的口径：日历上显示的是这一天真的做过的事，
     * 没做的人不需要每天被同一行空白提醒一次。
     */
    fun completionsOn(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        date: LocalDate,
    ): List<Completion> {
        val epochDay = date.toEpochDay()
        val byHabit = logs.asSequence()
            .filter { it.epochDay == epochDay }
            .associateBy { it.habitId }
        return habits.mapNotNull { habit ->
            byHabit[habit.id]?.let { Completion(habit, it.note) }
        }
    }

    /** 区间内哪些天有习惯打卡，月视图标记用。 */
    fun loggedDaysInRange(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        from: LocalDate,
        to: LocalDate,
    ): Set<LocalDate> {
        val ids = habits.map { it.id }.toSet()
        return logs.asSequence()
            .filter { it.habitId in ids && it.epochDay in from.toEpochDay()..to.toEpochDay() }
            .map { LocalDate.ofEpochDay(it.epochDay) }
            .toSet()
    }

    /**
     * 周期区间。周按用户设定的周起始日排，月与年取自然区间。
     *
     * 用自然周/月/年而不是滚动 7/30/365 天：用户问的是「本月打卡了多少」，
     * 滚动窗口的分母每天都在换，月底回看时数字对不上任何一张日历。
     */
    fun periodRange(period: HabitPeriod, today: LocalDate, firstDayOfWeek: DayOfWeek): Pair<LocalDate, LocalDate> =
        when (period) {
            HabitPeriod.WEEK -> {
                val start = weekStartOf(today, firstDayOfWeek)
                start to start.plusDays(6)
            }

            HabitPeriod.MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())

            HabitPeriod.YEAR -> today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())

            HabitPeriod.TOTAL -> today.withDayOfMonth(1) to today
        }

    /** 某个自然日所在周的起点（周起始日跟随用户设置）。 */
    fun weekStartOf(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate {
        val offset = (date.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        return date.minusDays(offset.toLong())
    }

    /** 某个习惯在 [logs] 里出现过的日期，统计与日历标记都以此为输入。 */
    fun daysOf(logs: List<HabitLogEntity>, habitId: Long): Set<LocalDate> =
        logs.asSequence()
            .filter { it.habitId == habitId }
            .map { LocalDate.ofEpochDay(it.epochDay) }
            .toSet()

    /** 某天的备注原文；没打卡或没写就是空串。 */
    fun noteOn(logs: List<HabitLogEntity>, habitId: Long, date: LocalDate): String =
        logs.firstOrNull { it.habitId == habitId && it.epochDay == date.toEpochDay() }?.note.orEmpty()

    /**
     * 一个习惯在某周期里的完成度。
     *
     * 分母 `expected` 是这套统计的核心口径，三个约束同时生效：
     * ① 只算已经过去的天（今天是 29 号就不该拿 31 号稀释百分比）；
     * ② 只算落在习惯起止区间内的天（10 月才开始的习惯，本年分母不是 365）；
     * ③ 按 `targetPerWeek` 折算而不是每天都算（每周 3 次的习惯天天打，是超额完成不是 300%）。
     * 分子超过分母时 `rate` 截到 1.0，但 `done` 与 `expected` 原样给出去 ——
     * 界面要能同时显示「18 / 13 次」这种真实的超额，而不是只剩一个 100%。
     */
    fun statOf(
        habit: HabitEntity,
        days: Set<LocalDate>,
        period: HabitPeriod,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): HabitStat {
        val (start, end) = periodRange(period, today, firstDayOfWeek)
        val logged = days.filter { it in start..end }.toSet()
        val eligible = eligibleDays(habit, start, end, today)
        val expected = expectedCount(habit.targetPerWeek, eligible)
        val done = logged.size
        return HabitStat(
            period = period,
            start = start,
            end = end,
            done = done,
            eligibleDays = eligible,
            expected = expected,
            rate = if (expected <= 0) 0f else (done.toFloat() / expected).coerceAtMost(1f),
            loggedDays = logged,
        )
    }

    /** 三个自然周期一次算完，总览页按周/月/年三张卡渲染。 */
    fun statsOf(
        habit: HabitEntity,
        days: Set<LocalDate>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): List<HabitStat> = CARDS.map { statOf(habit, days, it, today, firstDayOfWeek) }

    /**
     * 区间内「应该打」的天数：周期 ∩ 习惯起止 ∩ 已经过去的天。
     *
     * 上界同时取结束日和 today：已经结束的习惯在总览里分母就是它真实存在的那段日子，
     * 不会随着年份推移被算进越来越多的天数。
     */
    fun eligibleDays(habit: HabitEntity, from: LocalDate, to: LocalDate, today: LocalDate): Int {
        val start = maxOf(from, LocalDate.ofEpochDay(habit.startEpochDay))
        val rawEnd = habit.endEpochDay?.let { LocalDate.ofEpochDay(it) } ?: to
        val end = minOf(minOf(to, rawEnd), today)
        if (end < start) return 0
        return (end.toEpochDay() - start.toEpochDay() + 1).toInt()
    }

    /** 天数折算成应打卡次数。每周目标缺省或非法（<=0 / >7）都按每天算。 */
    fun expectedCount(targetPerWeek: Int, eligibleDays: Int): Int {
        if (eligibleDays <= 0) return 0
        val target = if (targetPerWeek <= 0 || targetPerWeek > 7) 7 else targetPerWeek
        if (target >= 7) return eligibleDays
        val scaled = eligibleDays.toDouble() * target / 7.0
        return scaled.roundToAtLeastOne()
    }

    /**
     * 从今天往回数连续天数。今天没打卡不算断：
     * 从昨天开始数才是用户心里的「我连着做了 N 天」，否则每天白天这条都会假报 0。
     */
    fun streakOf(loggedDays: Set<LocalDate>, today: LocalDate): Int {
        if (loggedDays.isEmpty()) return 0
        var cursor = if (today in loggedDays) today else today.minusDays(1)
        if (cursor !in loggedDays) return 0
        var count = 0
        while (cursor in loggedDays) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** 历史最长连续，用来和「当前连续」并排显示 —— 后者低的时候前者才有安慰作用。 */
    fun bestStreakOf(loggedDays: Set<LocalDate>): Int {
        if (loggedDays.isEmpty()) return 0
        val sorted = loggedDays.sortedBy { it.toEpochDay() }
        var best = 1
        var run = 1
        for (index in 1 until sorted.size) {
            run = if (sorted[index].toEpochDay() == sorted[index - 1].toEpochDay() + 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /**
     * 自开始以来的总计：分母仍走 [eligibleDays]，但窗口取习惯的整段起止。
     *
     * 上界给 [today]，未来月份不计。
     */
    fun totalOf(
        habit: HabitEntity,
        days: Set<LocalDate>,
        today: LocalDate,
    ): HabitStat {
        val start = LocalDate.ofEpochDay(habit.startEpochDay)
        val end = habit.endEpochDay?.let { LocalDate.ofEpochDay(it) } ?: today
        val windowEnd = minOf(end, today)
        val logged = days.filter { it in start..windowEnd }.toSet()
        val eligible = eligibleDays(habit, start, windowEnd, today)
        val expected = expectedCount(habit.targetPerWeek, eligible)
        return HabitStat(
            period = HabitPeriod.TOTAL,
            start = start,
            end = windowEnd,
            done = logged.size,
            eligibleDays = eligible,
            expected = expected,
            rate = if (expected <= 0) 0f else (logged.size.toFloat() / expected).coerceAtMost(1f),
            loggedDays = logged,
        )
    }

    /** [count] 个连续周的起点，从最老到最新；总览页热力图按行取。 */
    fun weekStarts(today: LocalDate, firstDayOfWeek: DayOfWeek, count: Int): List<LocalDate> {
        val current = weekStartOf(today, firstDayOfWeek)
        return (0 until count).map { current.minusWeeks((count - 1 - it).toLong()) }
    }

    private fun Double.roundToAtLeastOne(): Int {
        val rounded = this.roundToInt()
        return if (rounded < 1) 1 else rounded
    }

    /** 总览页固定要的那三张卡；[HabitPeriod.TOTAL] 不在其中，它是单独一行的总计。 */
    private val CARDS = listOf(HabitPeriod.WEEK, HabitPeriod.MONTH, HabitPeriod.YEAR)
}

/** 用户能选的自然周期；[TOTAL] 是「自开始以来」，总览页第三张卡之外的总计行用它。 */
enum class HabitPeriod {
    WEEK,
    MONTH,
    YEAR,
    TOTAL,
    ;

    /** 周期标签。领域层里唯一一处中文，因为它要在四个界面上一致地出现。 */
    val label: String
        get() = when (this) {
            WEEK -> "本周"
            MONTH -> "本月"
            YEAR -> "本年"
            TOTAL -> "合计"
        }
}

/** 一个周期算完的结果。[loggedDays] 直接带上，省掉界面再回查一遍。 */
data class HabitStat(
    val period: HabitPeriod,
    val start: LocalDate,
    val end: LocalDate,
    val done: Int,
    val eligibleDays: Int,
    val expected: Int,
    val rate: Float,
    val loggedDays: Set<LocalDate>,
)

/** 日历日视图的一行：这一天的习惯完成记录与其备注。 */
data class Completion(
    val habit: HabitEntity,
    val note: String,
)

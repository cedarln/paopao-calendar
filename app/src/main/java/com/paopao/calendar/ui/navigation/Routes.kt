package com.paopao.calendar.ui.navigation

import com.paopao.calendar.R

/**
 * 路由。
 *
 * 用字符串常量而不是类型安全的 navigation-serializable：后者要加 kotlinx-serialization
 * 插件和一堆编译器版本约束，在这个规模下收益不明显，而路由表本来就只有这一处消费者。
 *
 * id 一律走路径参数并用 `-1` 表示「新建」：可空的路径参数在 NavCompose 里要声明
 * `nullable = true` + 默认值，而 Long 的可空默认值在 bundle 里表现不稳定。
 */
object Routes {
    const val ARG_INVENTORY_ID = "inventoryId"
    const val ARG_EPOCH_DAY = "epochDay"
    const val ARG_HABIT_ID = "habitId"
    const val ARG_MEMORIAL_ID = "memorialId"
    const val ARG_PLAN_ID = "planId"

    /**
     * 日历带可选的「定位到某天」参数：桌面插件点某一天时要落到那一天的议程上。
     *
     * 参数缺省用 -1 而不是 null：可空的路径参数在 NavCompose 里要 `nullable = true`
     * 且默认值在 bundle 里表现不稳定，而 -1 作为「不特别定位」的哨兵自解释。
     */
    const val CALENDAR = "calendar?{$ARG_EPOCH_DAY}"
    const val INVENTORY = "inventory"
    const val FOCUS = "focus"
    const val MORE = "more"
    const val SETTINGS = "settings"
    const val MONTH_SETTINGS = "settings/monthView"
    const val HABIT = "habit"
    const val MEMORIAL = "memorial"

    /** 具体某一天；-1 表示「保持用户上次看的日期」。 */
    fun calendarAt(epochDay: Long) = "calendar?$ARG_EPOCH_DAY=$epochDay"

    fun calendarStart() = calendarAt(NEW)

    const val INVENTORY_EDIT = "inventory/edit/{$ARG_INVENTORY_ID}/{$ARG_EPOCH_DAY}"
    const val HABIT_EDIT = "habit/edit/{$ARG_HABIT_ID}"
    const val MEMORIAL_EDIT = "memorial/edit/{$ARG_MEMORIAL_ID}"

    /** 单个习惯的总览：本周/本月/本年的完成度、连续天数与带备注的历史。 */
    const val HABIT_STATS = "habit/stats/{$ARG_HABIT_ID}"

    fun habitStats(habitId: Long) = "habit/stats/$habitId"

    /**
     * 训练模块。
     *
     * 计时页的 planId 允许是 [NEW]：那时跑的是「自由训练」——不挂计划、只计时长，
     * 打完卡写一条 planId 为空的日志。用同一个页面兜两种输入，比复制一个空白计时器省一半代码。
     */
    const val TRAINING = "training"
    const val TRAINING_EDIT = "training/edit/{$ARG_PLAN_ID}"
    const val EXERCISE_LIBRARY = "training/exercises"
    const val WORKOUT_RUN = "training/run/{$ARG_PLAN_ID}"

    fun trainingEdit(planId: Long = NEW) = "training/edit/$planId"

    fun workoutRun(planId: Long = NEW) = "training/run/$planId"

    /** 新建时的哨兵值。 */
    const val NEW = -1L

    fun inventoryEdit(inventoryId: Long = NEW, epochDay: Long = NEW) =
        "inventory/edit/$inventoryId/$epochDay"

    fun habitEdit(habitId: Long = NEW) = "habit/edit/$habitId"

    fun memorialEdit(memorialId: Long = NEW) = "memorial/edit/$memorialId"

    /** 路径参数里的哨兵还原成可空 id。 */
    fun idOrNull(raw: Long?): Long? = raw?.takeIf { it != NEW }

    /** 查询参数里的哨兵还原成可空日期。 */
    fun dayOrNull(raw: Long?): Long? = raw?.takeIf { it >= 0L }
}

enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    /** 底栏点击时要落到哪个具体路由上（日历需要带上「不特别定位」的参数）。 */
    val launchRoute: String,
) {
    CALENDAR(Routes.CALENDAR, R.string.nav_calendar, Routes.calendarStart()),
    INVENTORY(Routes.INVENTORY, R.string.nav_inventory, Routes.INVENTORY),
    FOCUS(Routes.FOCUS, R.string.nav_focus, Routes.FOCUS),
    MORE(Routes.MORE, R.string.nav_more, Routes.MORE),
}

package com.paopao.calendar.domain.export

import com.paopao.calendar.data.local.entity.CategoryEntity
import com.paopao.calendar.data.local.entity.EventEntity
import com.paopao.calendar.data.local.entity.FocusSessionEntity
import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.data.local.entity.HabitLogEntity
import com.paopao.calendar.data.local.entity.InventoryEntity
import com.paopao.calendar.data.local.entity.InventoryTagCrossRef
import com.paopao.calendar.data.local.entity.MemorialEntity
import com.paopao.calendar.data.local.entity.PlanActionEntity
import com.paopao.calendar.data.local.entity.TagEntity
import com.paopao.calendar.data.local.entity.WorkoutLogEntity
import com.paopao.calendar.data.local.entity.WorkoutPlanEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * 导出构造器的共用样本。
 *
 * 固定时区与固定时刻是重点：这三个格式的断言都是逐字比对的，
 * 用 ZoneId.systemDefault() 的话，同一份代码在 CI（UTC）和在国内（+08:00）会得出两个答案，
 * 那种测试第一次红的时候没人分得清是代码变了还是机器变了。
 */
object ExportFixtures {

    val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 2026-03-05 是星期四，整月样本范围用它方便手算。 */
    val march: ExportRange = ExportRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))

    val generatedAtMillis: Long = LocalDate.of(2026, 4, 1).atTime(8, 30)
        .atZone(zone).toInstant().toEpochMilli()

    fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        LocalDate.of(year, month, dayOfMonth).toEpochDay()

    /** 2026-03-09 10:00 本地时间；转 UTC 是 02:00，正好能断出时区换算没写错。 */
    val morningMillis: Long = LocalDate.of(2026, 3, 9).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
    val morningEndMillis: Long = LocalDate.of(2026, 3, 9).atTime(11, 15).atZone(zone).toInstant().toEpochMilli()

    val allDayEvent = EventEntity(
        id = 1L,
        title = "体检",
        note = "空腹",
        startEpochDay = day(2026, 3, 9),
        endEpochDay = day(2026, 3, 9),
        allDay = true,
        categoryId = 7L,
    )

    val timedEvent = EventEntity(
        id = 2L,
        title = "组会",
        startEpochDay = day(2026, 3, 9),
        endEpochDay = day(2026, 3, 9),
        startMillis = morningMillis,
        endMillis = morningEndMillis,
        allDay = false,
        remindMinutes = 30,
    )

    /** 每周四（bit3 = 1<<3 = 8），3 月命中 03-05 / 03-12 / 03-19 / 03-26 四天。 */
    val weeklyEvent = EventEntity(
        id = 3L,
        title = "周会",
        startEpochDay = day(2026, 3, 5),
        endEpochDay = day(2026, 3, 5),
        allDay = true,
        recurrenceText = "weekly:8",
    )

    /** 跨天且带奇怪字符的标题：逗号与分号在 ICS 里必须转义，否则属性被截断。 */
    val tripEvent = EventEntity(
        id = 4L,
        title = "出差, 上海; 三天",
        note = "带, 逗号\n和换行，另外还要记一段足够长的备注，长到这一行必须折起来才不会越过七十五字节那一刀",
        startEpochDay = day(2026, 3, 20),
        endEpochDay = day(2026, 3, 22),
        allDay = true,
    )

    val runningHabit = HabitEntity(
        id = 11L,
        name = "跑步",
        colorArgb = -12345,
        iconKey = "run",
        targetPerWeek = 3,
        startEpochDay = day(2026, 1, 1),
    )

    val readingHabit = HabitEntity(
        id = 12L,
        name = "阅读",
        colorArgb = -1,
        iconKey = "book",
        targetPerWeek = 0,
        startEpochDay = day(2026, 3, 1),
        endEpochDay = day(2026, 3, 15),
    )

    val snapshot = ExportSnapshot(
        range = march,
        generatedAtMillis = generatedAtMillis,
        appVersion = "0.3.0",
        events = listOf(allDayEvent, timedEvent, weeklyEvent, tripEvent),
        inventories = listOf(
            InventoryEntity(id = 21L, title = "写周报", dueEpochDay = day(2026, 3, 9), importance = 3, urgency = 3, quadrantIndex = 1),
            InventoryEntity(id = 22L, title = "买猫粮", done = true, dueEpochDay = day(2026, 3, 9), categoryId = 8L, deletedAtMillis = generatedAtMillis),
        ),
        tags = listOf(TagEntity(id = 31L, name = "工作")),
        tagLinks = listOf(InventoryTagCrossRef(inventoryId = 21L, tagId = 31L)),
        categories = listOf(
            CategoryEntity(id = 7L, name = "日程", colorArgb = -1, iconKey = "schedule", scope = CategoryEntity.SCOPE_EVENT),
            CategoryEntity(id = 8L, name = "生活", colorArgb = -1, iconKey = "life", scope = CategoryEntity.SCOPE_INVENTORY),
        ),
        habits = listOf(runningHabit, readingHabit),
        habitLogs = listOf(
            HabitLogEntity(habitId = 11L, epochDay = day(2026, 3, 9), note = "5 公里 配速6:10"),
            HabitLogEntity(habitId = 12L, epochDay = day(2026, 3, 9)),
            HabitLogEntity(habitId = 11L, epochDay = day(2026, 2, 28), note = "二月的那一次"),
        ),
        focusSessions = listOf(
            FocusSessionEntity(id = 41L, startedAtMillis = morningMillis, plannedMinutes = 25, actualMinutes = 25, completed = true, inventoryId = 21L),
        ),
        memorials = listOf(
            MemorialEntity(id = 51L, name = "妈妈生日", anchorEpochDay = day(1968, 6, 12), isLunar = true, colorArgb = -1, iconKey = "cake"),
        ),
        workoutPlans = listOf(
            WorkoutPlanEntity(id = 61L, name = "推", bodyPart = "胸", estimatedMinutes = 45, colorArgb = -1, archivedAtMillis = generatedAtMillis),
        ),
        planActions = listOf(
            PlanActionEntity(id = 71L, planId = 61L, sortOrder = 0, name = "卧推", sets = 5, reps = 5, exerciseKey = "bench_press"),
        ),
        workoutLogs = listOf(
            WorkoutLogEntity(id = 81L, epochDay = day(2026, 3, 9), planId = 61L, planName = "推", durationSec = 2_700, startedAtMillis = morningMillis),
            WorkoutLogEntity(id = 82L, epochDay = day(2026, 3, 9), planName = "自由训练", durationSec = 600, note = "临时跑了会儿"),
        ),
    )
}

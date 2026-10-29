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
import java.time.YearMonth

/**
 * 导出格式。
 *
 * 三种格式各管一件事，不做「一个文件里全都有」：JSON 是给机器读回去的，
 * Markdown 是给人看的月报，ICS 是给别人（手机自带日历、Google Calendar）导入的。
 * 混在一起的话，三者各自的约束会互相打架 —— 比如 ICS 只认 VEVENT，
 * 而习惯打卡在里面的语义根本就不是「一件事」。
 */
enum class ExportFormat(
    val label: String,
    val description: String,
    val extension: String,
    val mimeType: String,
) {
    JSON(
        label = "JSON 全量备份",
        description = "每张表原样导出，含已归档与回收站；用来备份或换机。",
        extension = "json",
        mimeType = "application/json",
    ),
    MARKDOWN(
        label = "Markdown 月报",
        description = "按天列出日程、事项、习惯打卡与训练，按月给汇总。",
        extension = "md",
        mimeType = "text/markdown",
    ),
    ICS(
        label = "ICS 日程",
        description = "重复日程展开成一条条真实事件，可导入其他日历应用。",
        extension = "ics",
        mimeType = "text/calendar",
    ),
}

/**
 * 导出的时间范围。
 *
 * 起止都是「含端点」的日期，且刻意不含时区与时刻：用户选的是「哪几个月」，
 * 不是「哪个时间戳区间」。
 */
data class ExportRange(
    val from: LocalDate,
    val until: LocalDate,
) {
    /** 预设与自定义都可能传反顺序，这里统一收敛成 from <= until。 */
    init {
        require(!from.isAfter(until)) { "导出范围的开始日不能晚于结束日：$from > $until" }
    }

    val days: Int get() = (until.toEpochDay() - from.toEpochDay() + 1).toInt()

    /**
     * 范围内的每一个月，用于月报分节与文件名。
     *
     * 没用 `YearMonth.from(a)..YearMonth.from(b)` 那个 range：它返回的
     * `java.time.YearMonths` 是包私有类，在本项目的 desugaring 配置下取不到成员。
     * 手写的三步循环也就三行，不必为它换一个数据结构。
     */
    fun months(): List<YearMonth> {
        val months = mutableListOf<YearMonth>()
        var cursor = YearMonth.from(from)
        val last = YearMonth.from(until)
        while (!cursor.isAfter(last)) {
            months += cursor
            cursor = cursor.plusMonths(1)
        }
        return months
    }

    /** 文件名里的段时间戳：纯数字，任何文件系统都不会有意见。 */
    fun fileToken(): String = "${compact(from)}_${compact(until)}"

    fun displayText(): String = "$from 至 $until（共 $days 天）"

    private fun compact(date: LocalDate): String =
        "%04d%02d%02d".format(date.year, date.monthValue, date.dayOfMonth)

    companion object {
        /** 相对当前的预设范围。月按自然月切：「最近 2 个月」= 上个月 + 本月。 */
        fun preset(preset: ExportPreset, today: LocalDate): ExportRange = when (preset) {
            // 自定义没有「相对今天」的算法，界面会立刻用用户选的起止覆盖掉这个初值。
            ExportPreset.CUSTOM, ExportPreset.THIS_MONTH -> ExportRange(today.withDayOfMonth(1), today)
            ExportPreset.LAST_2_MONTHS -> ExportRange(
                today.minusMonths(1).withDayOfMonth(1),
                today.withDayOfMonth(today.lengthOfMonth()),
            )
            ExportPreset.LAST_3_MONTHS -> ExportRange(
                today.minusMonths(2).withDayOfMonth(1),
                today.withDayOfMonth(today.lengthOfMonth()),
            )
            ExportPreset.LAST_6_MONTHS -> ExportRange(
                today.minusMonths(5).withDayOfMonth(1),
                today.withDayOfMonth(today.lengthOfMonth()),
            )
            ExportPreset.THIS_YEAR -> ExportRange(LocalDate.of(today.year, 1, 1), today)
            ExportPreset.LAST_3_YEARS -> ExportRange(
                today.minusYears(3).withDayOfMonth(1),
                today.withDayOfMonth(today.lengthOfMonth()),
            )
        }
    }
}

enum class ExportPreset(val label: String) {
    THIS_MONTH("本月"),
    LAST_2_MONTHS("最近 2 个月"),
    LAST_3_MONTHS("最近 3 个月"),
    LAST_6_MONTHS("最近 6 个月"),
    THIS_YEAR("今年"),
    LAST_3_YEARS("最近 3 年"),
    CUSTOM("自定义起止"),
}

/**
 * 一次导出的全部输入。
 *
 * 字段是「整表快照」而不是「算好的结果」：把取数与排版分开，三个格式的构造器才是纯函数，
 * 能在 JVM 上逐字断言输出 —— 导出的东西一旦错了，用户是在别的设备上才发现的，
 * 那时候已经没有第二条路可退了。
 *
 * 范围只约束按时间看的两种格式（[ExportFormat.MARKDOWN] 与 [ExportFormat.ICS]）；
 * JSON 永远全量，理由见 [JsonExport]。
 */
data class ExportSnapshot(
    val range: ExportRange,
    val generatedAtMillis: Long,
    val appVersion: String,
    val events: List<EventEntity> = emptyList(),
    val inventories: List<InventoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val tagLinks: List<InventoryTagCrossRef> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val habitLogs: List<HabitLogEntity> = emptyList(),
    val focusSessions: List<FocusSessionEntity> = emptyList(),
    val memorials: List<MemorialEntity> = emptyList(),
    val workoutPlans: List<WorkoutPlanEntity> = emptyList(),
    val planActions: List<PlanActionEntity> = emptyList(),
    val workoutLogs: List<WorkoutLogEntity> = emptyList(),
) {
    val generatedAtDate: LocalDate get() =
        java.time.Instant.ofEpochMilli(generatedAtMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    /** 范围内出现过打卡的日子，日视图口径复用 `HabitMath`，不在此重算。 */
    fun habitLogsOn(date: LocalDate): List<HabitLogEntity> {
        val epochDay = date.toEpochDay()
        return habitLogs.filter { it.epochDay == epochDay }
    }

    fun workoutLogsOn(date: LocalDate): List<WorkoutLogEntity> {
        val epochDay = date.toEpochDay()
        return workoutLogs.filter { it.epochDay == epochDay }
    }

    /** 事项按到期日归到某天；没有到期日的只出现在 JSON 里。 */
    fun inventoriesDueOn(date: LocalDate): List<InventoryEntity> {
        val epochDay = date.toEpochDay()
        return inventories.filter { it.dueEpochDay == epochDay }
    }

    fun tagNamesOf(inventoryId: Long): List<String> {
        val ids = tagLinks.filter { it.inventoryId == inventoryId }.map { it.tagId }.toSet()
        return tags.filter { it.id in ids }.map { it.name }
    }

    fun categoryName(id: Long?): String? = categories.firstOrNull { it.id == id }?.name
}

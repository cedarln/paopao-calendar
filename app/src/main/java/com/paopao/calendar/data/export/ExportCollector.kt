package com.paopao.calendar.data.export

import android.content.Context
import com.paopao.calendar.AppContainer
import com.paopao.calendar.domain.export.ExportArtifactNames
import com.paopao.calendar.domain.export.ExportFormat
import com.paopao.calendar.domain.export.ExportRange
import com.paopao.calendar.domain.export.ExportSnapshot
import com.paopao.calendar.domain.export.IcsExport
import com.paopao.calendar.domain.export.JsonExport
import com.paopao.calendar.domain.export.MarkdownExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把七张表拧成一次导出的输入，再交给对应的格式构造器。
 *
 * 取数与排版分开的代价是多这一个类，换来的是三个格式都能在没有 Android 依赖的地方
 * 逐字断言 —— 导出错了，用户是在换机或导入别的日历时才发现的，那时候我们已经被卸载了。
 */
class ExportCollector(private val container: AppContainer) {

    /**
     * 全表快照。
     *
     * [range] 只记在结果里给按时间排的格式用；取数一律整表，因为 JSON 备份要连
     * 回收站与已归档一起带走（理由见 [JsonExport]），而 Markdown 与 ICS 拿到全量之后
     * 自己按范围筛 —— 一次多读几百行，比写两套查询条件便宜，也不会出现「备份里少了一列」这种事。
     */
    suspend fun snapshot(range: ExportRange): ExportSnapshot = withContext(Dispatchers.IO) {
        val context: Context = container.appContext
        ExportSnapshot(
            range = range,
            generatedAtMillis = System.currentTimeMillis(),
            appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0",
            events = container.eventRepository.findAllIncludingHistory(),
            inventories = container.inventoryRepository.findAllIncludingTrash(),
            tags = container.inventoryRepository.findAllTags(),
            tagLinks = container.inventoryRepository.findAllTagLinks(),
            categories = container.categoryRepository.findAll(),
            habits = container.habitRepository.findAllIncludingArchived(),
            habitLogs = container.habitRepository.findAllLogs(),
            focusSessions = container.focusRepository.findAll(),
            memorials = container.memorialRepository.findAll(),
            workoutPlans = container.workoutRepository.findAllPlansIncludingArchived(),
            planActions = container.workoutRepository.findAllActions(),
            workoutLogs = container.workoutRepository.findAllLogs(),
        )
    }

    /** 一步到位：取数 + 排版 + 起好文件名。落盘交给 [ExportFiles]。 */
    suspend fun build(format: ExportFormat, range: ExportRange): String =
        when (format) {
            ExportFormat.JSON -> JsonExport.build(snapshot(range))
            ExportFormat.MARKDOWN -> MarkdownExport.build(snapshot(range))
            ExportFormat.ICS -> IcsExport.build(snapshot(range))
        }

    /** 文件名里带上格式、范围与导出时刻；同一天反复导出不会互相覆盖。 */
    fun fileNameFor(format: ExportFormat, range: ExportRange): String =
        ExportArtifactNames.fileName(format, range, System.currentTimeMillis())
}

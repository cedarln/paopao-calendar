package com.paopao.calendar.domain.export

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * JSON 全量导出。
 *
 * 为什么 JSON 不受时间范围影响：它是备份件。用户勾「最近 3 个月」想要的是**看**这三个月，
 * 而一个缺一半日程、少几个习惯定义的备份，在需要它的那天等于没有备份。
 * 时间范围只喂给 Markdown 与 ICS，界面上也照这句话解释。
 *
 * 手写序列化而不是用 org.json：后者在单元测试里是 android.jar 的桩（方法一律返回默认值），
 * 测不出真实输出；而「能逐字节断言的导出」恰恰是这一层唯一有意义的验证方式。
 * 结构一律用 `Map<String, Any?>` 描述，渲染器只认 Map / List / String / Number / Boolean / null
 * 六种值 —— 加字段的人只写数据，不用管逗号与缩进。
 */
object JsonExport {

    /** 备份文件的结构版本。字段增减要改它，将来读回来的一侧靠它决定怎么解释。 */
    const val SCHEMA_VERSION = 1

    internal fun day(value: Long?): Any? = value?.let { LocalDate.ofEpochDay(it).toString() }

    /**
     * 时刻写成带偏移的 ISO 文本。
     *
     * 用 OffsetDateTime 而不是 ZonedDateTime：后者会把时区 id 也拼进去
     * （`2026-04-01T08:30+08:00[Asia/Shanghai]`），那种字符串没有任何解析器标配支持，
     * 而备份里的时间是要被读回去的。时区 id 已经单独记在顶层 timeZone 一列里了。
     */
    internal fun stamp(value: Long?, zone: ZoneId): Any? =
        value?.let { Instant.ofEpochMilli(it).atZone(zone).toOffsetDateTime().toString() }

    fun build(snapshot: ExportSnapshot, zone: ZoneId = ZoneId.systemDefault()): String {
        val root = linkedMapOf<String, Any?>(
            "schema" to SCHEMA_VERSION,
            "app" to "泡泡日历",
            "package" to "com.paopao.calendar",
            "version" to snapshot.appVersion,
            "generatedAt" to stamp(snapshot.generatedAtMillis, zone),
            "timeZone" to zone.id,
            // 范围照实写下来，哪怕 JSON 是全量：用户要记得起自己是哪天、选的哪一段导的
            "requestedRange" to linkedMapOf(
                "from" to snapshot.range.from.toString(),
                "until" to snapshot.range.until.toString(),
                "days" to snapshot.range.days,
                "note" to "JSON 恒为全量备份，此字段只记录导出时选中的范围",
            ),
            "counts" to JsonTables.counts(snapshot),
        )
        JsonTables.tables(root, snapshot, zone)
        return JsonRender.render(root, 0) + "\n"
    }
}

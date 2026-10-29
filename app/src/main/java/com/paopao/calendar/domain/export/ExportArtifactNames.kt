package com.paopao.calendar.domain.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 导出文件名的规则。
 *
 * 放在纯层是因为它真的需要被断言：文件名里出现空格、冒号或中文，SAF 那头不一定报错，
 * 但换到 Windows 或导入 ICS 时就会变成「文件在，打不开」。所以只用 ASCII 与连字符，
 * 时间戳精确到秒，同一天点两次导出也不会盖掉前一份。
 */
object ExportArtifactNames {

    private val secondFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")

    fun fileName(
        format: ExportFormat,
        range: ExportRange,
        generatedAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val time = Instant.ofEpochMilli(generatedAtMillis).atZone(zone).toLocalTime().format(secondFormat)
        return "paopao-${format.name.lowercase()}-${range.fileToken()}-$time.${format.extension}"
    }
}

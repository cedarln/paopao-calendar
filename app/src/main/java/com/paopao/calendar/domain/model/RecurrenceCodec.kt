package com.paopao.calendar.domain.model

/**
 * [Recurrence] 的文本编码。
 *
 * 存一列而不是 kind/days/dayOfMonth 三列：后两者对多数规则是无意义的空值，
 * 三列 nullable 会让每一次查询都要解释「哪一列在什么 kind 下才有效」。
 * 编码规则对外可见（`weekly:65` 这种），导出 ICS 时也能直接映射到 RRULE。
 */
object RecurrenceCodec {

    fun encode(recurrence: Recurrence): String = when (recurrence) {
        Recurrence.None -> "none"
        is Recurrence.Weekly -> "weekly:${recurrence.days.bits}"
        is Recurrence.Monthly -> "monthly:${recurrence.dayOfMonth}"
        is Recurrence.LunarYearly ->
            "lunaryear:${recurrence.lunarMonth}:${recurrence.lunarDay}:${if (recurrence.leapFallback) 1 else 0}"
        Recurrence.Yearly -> "yearly"
    }

    fun decode(raw: String?): Recurrence {
        if (raw.isNullOrBlank()) return Recurrence.None
        val parts = raw.split(":")
        return when (parts.first()) {
            // 0 位的掩码与 0 号的「每月」都永远命中不了，存成 None 才不会变成静默失效的规则
            "weekly" -> parts.getOrNull(1)?.toIntOrNull()
                ?.takeIf { it in 1..0b111_1111 }
                ?.let { Recurrence.Weekly(DayOfWeekMask(it)) }
                ?: Recurrence.None

            "monthly" -> parts.getOrNull(1)?.toIntOrNull()
                ?.takeIf { it in 1..31 }
                ?.let { Recurrence.Monthly(it) }
                ?: Recurrence.None

            "lunaryear" -> {
                val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return Recurrence.None
                val day = parts.getOrNull(2)?.toIntOrNull()?.takeIf { it in 1..30 } ?: return Recurrence.None
                Recurrence.LunarYearly(
                    lunarMonth = month,
                    lunarDay = day,
                    leapFallback = parts.getOrNull(3) == "1",
                )
            }

            "yearly" -> Recurrence.Yearly
            else -> Recurrence.None
        }
    }
}

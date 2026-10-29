package com.paopao.calendar.domain.export

/**
 * 那套最小 JSON 渲染器。
 *
 * 只认 Map / List / String / Number / Boolean / null 六种值，两格缩进，
 * 空集合写成 `[]` 与 `{}`。
 */
internal object JsonRender {

    fun render(value: Any?, depth: Int): String = when (value) {
        null -> "null"
        is Map<*, *> -> objectOf(value, depth)
        is List<*> -> arrayOf(value, depth)
        is String -> quote(value)
        is Number, is Boolean -> value.toString()
        else -> error("JSON 导出不支持的类型：${value::class.simpleName}（$value）")
    }

    private fun objectOf(map: Map<*, *>, depth: Int): String {
        if (map.isEmpty()) return "{}"
        val pad = indent(depth + 1)
        return buildString {
            append("{\n")
            map.entries.forEachIndexed { index, (key, item) ->
                append(pad).append(quote(key.toString())).append(": ").append(render(item, depth + 1))
                if (index < map.size - 1) append(',')
                append('\n')
            }
            append(indent(depth)).append('}')
        }
    }

    private fun arrayOf(list: List<*>, depth: Int): String {
        if (list.isEmpty()) return "[]"
        val pad = indent(depth + 1)
        return buildString {
            append("[\n")
            list.forEachIndexed { index, item ->
                append(pad).append(render(item, depth + 1))
                if (index < list.size - 1) append(',')
                append('\n')
            }
            append(indent(depth)).append(']')
        }
    }

    private fun indent(depth: Int): String = "  ".repeat(depth)

    /**
     * 字符串转义。
     *
     * 控制字符一律转成 \u00XX 形式：备注里粘进一段带退格或 0x00 的文本时，
     * 原样写出去就是一份解析不了的 JSON，而报解析错的地方在另一台设备上。
     */
    internal fun quote(text: String): String = buildString {
        append('"')
        for (ch in text) {
            val escaped = escapeOf(ch)
            if (escaped == null) append(ch) else append(escaped)
        }
        append('"')
    }

    private fun escapeOf(ch: Char): String? = when (ch) {
        '"' -> "\\\""
        '\\' -> "\\\\"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        '\b' -> "\\b"
        '\u000C' -> "\\f"
        else -> if (ch < ' ') "\\u%04x".format(ch.code) else null
    }
}

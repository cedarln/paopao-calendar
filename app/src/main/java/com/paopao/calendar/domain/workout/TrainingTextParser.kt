package com.paopao.calendar.domain.workout

/**
 * 「粘贴训练文案 → 训练计划」的规则解析。
 *
 * 这是 fit-miniapp 里 `cloudfunctions/parsePlan` 的兜底分支（`parseByRules`）的移植版：
 * 原函数是双模式的，配了 `LLM_API_KEY` 会去调外部大模型，否则退回这套词典 + 位置窗口。
 * 这里只搬规则版，因为本应用刻意不联网 —— 词典识别率确实低一些，但换来的是
 * 「你的健身文案不会离开这台设备」，这笔账值得算。
 *
 * 算法逐条保持原样：清洗噪声 → 按出现位置找动作 → 用「到下一个动作之前」当解析窗口 →
 * 先挖掉休息再抽组数/次数/时长。窗口切分是这套方法的核心：它让「卧推4组12次 划船3组10次」
 * 里后面的数字不会串到前面的动作上。
 */
data class ParsedAction(
    val name: String,
    val sets: Int,
    val reps: Int,
    val durationSec: Int,
    val restSec: Int,
    /** 只有次数或默认时长时标「估」，与小程序的历史记录口径一致。 */
    val estimated: Boolean,
    /** 命中动作库时带上条目标识，方便详情页跳百科；词典里的自定义动作是空串。 */
    val exerciseKey: String,
)

data class ParsedPlan(
    val bodyPart: String,
    val totalDurationMin: Int,
    /** 循环组数：规则版识别不出来，恒为 1，只有大模型版才会给出别的值。 */
    val cycleCount: Int,
    val actions: List<ParsedAction>,
) {
    val isEmpty: Boolean get() = actions.isEmpty()
}

object TrainingTextParser {

    /** 词典：关键词 → 规范动作名。与 ACTION_DICT 同内容，顺序不影响结果（按出现位置排序）。 */
    private val actionDict: Map<String, String> = linkedMapOf(
        "侧平举" to "哑铃侧平举",
        "推举" to "哑铃推举",
        "前平举" to "哑铃前平举",
        "耸肩" to "哑铃耸肩",
        "划船" to "哑铃划船",
        "引体向上" to "引体向上",
        "高位下拉" to "高位下拉",
        "硬拉" to "杠铃硬拉",
        "卧推" to "杠铃卧推",
        "俯卧撑" to "俯卧撑",
        "夹胸" to "绳索夹胸",
        "飞鸟" to "哑铃飞鸟",
        "深蹲" to "深蹲",
        "箭步蹲" to "箭步蹲",
        "腿举" to "腿举",
        "提踵" to "站姿提踵",
        "臀桥" to "臀桥",
        "卷腹" to "卷腹",
        "平板支撑" to "平板支撑",
        "俄罗斯转体" to "俄罗斯转体",
        "开合跳" to "开合跳",
        "高抬腿" to "高抬腿",
        "波比跳" to "波比跳",
        "登山跑" to "登山跑",
        "深蹲跳" to "深蹲跳",
    )

    /** 分享文案里的链接、emoji、话题标签、@ 与抖音话术，解析前先换成空格。 */
    private val noisePatterns = listOf(
        Regex("https?://\\S+"),
        Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]"),
        Regex("#\\S+"),
        Regex("@\\S+"),
        Regex("复制打开|复制此链接|抖音|Dou音|douyin|分享视频|口令|v\\d+\\.\\S+"),
    )

    private val restPattern = Regex("(?:组间休息|间歇|休息|歇|停)\\s*(\\d+)\\s*(秒|[sS])")
    private val setsPattern = Regex("(\\d+)\\s*组")
    private val repsTimesPattern = Regex("(\\d+)\\s*次")
    private val repsCountPattern = Regex("(\\d+)\\s*个")
    private val secondsPattern = Regex("(\\d+)\\s*秒")
    private val secondsLetterPattern = Regex("(\\d+)\\s*[sS]\\b")
    private val minutesPattern = Regex("(\\d+)\\s*分钟")

    /** 原实现里 bodyPart 是在**未清洗**的原文上找「练X」的，这里保持一致。 */
    private val partHints = listOf("肩", "背", "胸", "腿", "臀", "腹", "手臂")

    fun parse(text: String): ParsedPlan {
        val clean = noisePatterns.fold(text) { acc, pattern -> acc.replace(pattern, " ") }

        val hits = collectHits(clean)
        if (hits.isEmpty()) {
            return ParsedPlan(bodyPart = "未知", totalDurationMin = 0, cycleCount = 0, actions = emptyList())
        }

        val actions = hits.mapIndexed { index, hit ->
            val end = if (index + 1 < hits.size) hits[index + 1].position else clean.length
            extractAction(hit.name, clean.substring(hit.position, end))
        }

        val totalSec = actions.sumOf { it.durationSec + it.restSec }
        return ParsedPlan(
            bodyPart = partHints.firstOrNull { text.contains("练$it") } ?: "全身",
            totalDurationMin = TrainingMath.estimatedMinutes(totalSec),
            cycleCount = 1,
            actions = actions,
        )
    }

    private data class Hit(val name: String, val position: Int, val keyLength: Int)

    /**
     * 按文本出现顺序收集动作，并丢掉被前一个命中包含的重叠项。
     *
     * 「哑铃推举」里同时含「推举」，两个都留就会多出一条不存在的动作；
     * 原实现是先按位置排序、再跳过起点落在上一个命中区间内的项，这里照搬。
     *
     * 相对原实现改了一处：同一位置上「深蹲」与「深蹲跳」都命中时，原实现按词典插入顺序
     * 留下先出现的「深蹲」，把用户明明写的「深蹲跳」识别成了另一个动作。这里改成同位置
     * 保留更长的关键词 —— 更长的词才是用户真正写的那个字。
     */
    private fun collectHits(clean: String): List<Hit> {
        val found = mutableListOf<Hit>()
        actionDict.forEach { (keyword, canonicalName) ->
            var from = 0
            while (true) {
                val position = clean.indexOf(keyword, from)
                if (position < 0) break
                found += Hit(canonicalName, position, keyword.length)
                from = position + keyword.length
            }
        }
        return found.sortedBy { it.position }.let { sorted ->
            val unique = mutableListOf<Hit>()
            sorted.forEach { hit ->
                val last = unique.lastOrNull()
                when {
                    last == null || hit.position >= last.position + last.keyLength -> unique += hit
                    hit.position == last.position && hit.keyLength > last.keyLength -> unique[unique.lastIndex] = hit
                    else -> Unit
                }
            }
            unique
        }
    }

    private fun extractAction(name: String, rawWindow: String): ParsedAction {
        // 休息先抽出来并从窗口里挖掉，否则「休息30秒」会被当成动作时长
        val restMatch = restPattern.find(rawWindow)
        val restSec = restMatch?.groupValues?.get(1)?.toIntOrNull() ?: DEFAULT_REST_SEC
        val window = if (restMatch != null) rawWindow.replace(restMatch.value, " ") else rawWindow

        val sets = positiveInt(setsPattern, window) ?: 1
        val reps = positiveInt(repsTimesPattern, window) ?: positiveInt(repsCountPattern, window) ?: 0
        val seconds = positiveInt(secondsPattern, window) ?: positiveInt(secondsLetterPattern, window)
        val minutes = positiveInt(minutesPattern, window)

        val durationSec = when {
            seconds != null -> seconds
            minutes != null -> minutes * 60
            reps > 0 -> TrainingMath.estimateDurationSec(reps, sets)
            else -> DEFAULT_DURATION_SEC
        }

        return ParsedAction(
            name = name.take(MAX_NAME_LENGTH),
            sets = sets,
            reps = reps,
            durationSec = durationSec,
            restSec = restSec,
            estimated = seconds == null && minutes == null,
            exerciseKey = ExerciseCatalog.findByName(name)?.key.orEmpty(),
        )
    }

    /**
     * 取第一个匹配到的正整数，没有匹配或匹配到 0 都返回 null。
     *
     * 这是 JS `Number(x) || 兜底` 的等价物：那边 0 是假值会继续往下找，
     * 所以「0组」「0次」在两套写法里都得让位给下一个候选，而不是当成有效数字。
     * 顺带一个刻意的偏差：原文案「做 0 秒」会让时长变成 0，这里当作没写、继续估算 ——
     * 零秒动作不是有效计划，照搬只会产出一条更离谱的记录。
     */
    private fun positiveInt(pattern: Regex, input: String): Int? =
        pattern.find(input)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

    private const val DEFAULT_REST_SEC = 30
    private const val DEFAULT_DURATION_SEC = 60
    private const val MAX_NAME_LENGTH = 12
}

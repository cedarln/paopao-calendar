package com.paopao.calendar.domain.model

/**
 * 四象限的两个输入轴。
 *
 * 这里没有把象限存成一个 int，而是存重要度和紧急度：象限是派生值，
 * 单存象限会让「重要但不紧急」被用户改成「重要且紧急」时丢失原始判断。
 */
enum class Importance {
    TRIVIAL, NORMAL, CRITICAL;

    companion object {
        fun fromRank(rank: Int): Importance = entries.getOrElse(rank - 1) { NORMAL }
    }

    /** 1..3，3 为最重要。 */
    val rank: Int get() = ordinal + 1
}

enum class Urgency {
    LATER, SOON, NOW;

    companion object {
        fun fromRank(rank: Int): Urgency = entries.getOrElse(rank - 1) { SOON }
    }

    val rank: Int get() = ordinal + 1
}

/**
 * 艾森豪斯纳四象限。命名沿用 Q1..Q4，Q1 是需要立刻处理的那一格。
 */
enum class Quadrant(val importance: Importance, val urgency: Urgency) {
    /** 重要且紧急 */
    Q1(Importance.CRITICAL, Urgency.NOW),

    /** 重要不紧急 —— 长期价值最高的一格 */
    Q2(Importance.CRITICAL, Urgency.LATER),

    /** 紧急不重要 —— 通常是最该委派或删掉的一格 */
    Q3(Importance.TRIVIAL, Urgency.NOW),

    /** 既不紧急也不重要 */
    Q4(Importance.TRIVIAL, Urgency.LATER);

    companion object {
        fun of(importance: Importance, urgency: Urgency): Quadrant = entries.first {
            it.importance == importance && it.urgency == urgency
        }

        /** 读回物化列 `quadrantIndex`；越界值按 Q2 处理而不是抛异常，历史脏数据不该让界面崩掉。 */
        fun fromIndex(index: Int): Quadrant = entries.getOrElse(index - 1) { Q2 }

        /**
         * 中间地带（重要度 NORMAL 或紧急度 SOON）会落不到四个角上，两条归边原则：
         * 「宁可信其重要」—— NORMAL 一律按重要处理，别让一件普通事掉进「可以删」那格；
         * 「琐事别抬进 Q2」—— TRIVIAL + SOON 算紧急，它挤占的正是当下的时间。
         */
        fun derive(importance: Importance, urgency: Urgency): Quadrant {
            val critical = importance != Importance.TRIVIAL
            val urgent = urgency == Urgency.NOW ||
                (urgency == Urgency.SOON && importance == Importance.TRIVIAL)
            return when {
                critical && urgent -> Q1
                critical -> Q2
                urgent -> Q3
                else -> Q4
            }
        }
    }
}

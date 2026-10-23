package com.paopao.calendar.ui.theme

import androidx.compose.ui.graphics.Color
import com.paopao.calendar.domain.model.Quadrant

/** 象限序号（1..4，与 `InventoryEntity.quadrantIndex` 同源）到配色的映射。 */
fun quadrantColor(quadrantIndex: Int): Color = when (Quadrant.fromIndex(quadrantIndex)) {
    Quadrant.Q1 -> QuadrantQ1
    Quadrant.Q2 -> QuadrantQ2
    Quadrant.Q3 -> QuadrantQ3
    Quadrant.Q4 -> QuadrantQ4
}

fun quadrantNameRes(quadrantIndex: Int): Int = when (Quadrant.fromIndex(quadrantIndex)) {
    Quadrant.Q1 -> com.paopao.calendar.R.string.quadrant_q1
    Quadrant.Q2 -> com.paopao.calendar.R.string.quadrant_q2
    Quadrant.Q3 -> com.paopao.calendar.R.string.quadrant_q3
    Quadrant.Q4 -> com.paopao.calendar.R.string.quadrant_q4
}

fun quadrantHintRes(quadrantIndex: Int): Int = when (Quadrant.fromIndex(quadrantIndex)) {
    Quadrant.Q1 -> com.paopao.calendar.R.string.quadrant_q1_hint
    Quadrant.Q2 -> com.paopao.calendar.R.string.quadrant_q2_hint
    Quadrant.Q3 -> com.paopao.calendar.R.string.quadrant_q3_hint
    Quadrant.Q4 -> com.paopao.calendar.R.string.quadrant_q4_hint
}

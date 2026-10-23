@file:OptIn(ExperimentalMaterial3Api::class)

package com.paopao.calendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paopao.calendar.data.local.entity.CategoryEntity
import com.paopao.calendar.data.local.entity.EventEntity
import com.paopao.calendar.domain.calendar.ChineseCalendar
import com.paopao.calendar.domain.calendar.lunarDayNumeric
import com.paopao.calendar.domain.calendar.lunarMonthNumeric
import com.paopao.calendar.domain.model.DayOfWeekMask
import com.paopao.calendar.domain.model.Recurrence
import com.paopao.calendar.domain.model.RecurrenceCodec
import com.paopao.calendar.ui.common.ColorSwatchField
import com.paopao.calendar.ui.common.DateField
import com.paopao.calendar.ui.common.MinuteOfDayField
import com.paopao.calendar.ui.common.OptionField
import com.paopao.calendar.ui.common.SectionTitle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 重复规则的可选项，索引与 [recurrenceFor] 一一对应。 */
private val RecurrenceLabels = listOf("不重复", "每天", "工作日", "每周", "每月", "每年", "农历每年")

private val ReminderLabels = listOf("不提醒", "准时", "提前 5 分钟", "提前 15 分钟", "提前 30 分钟", "提前 1 小时", "提前 2 小时", "提前 1 天", "提前 2 天", "提前 1 周")

private val ReminderMinutes = listOf(-1, 0, 5, 15, 30, 60, 120, 1440, 2880, 10080)

/**
 * 日程的新建/编辑面板。
 *
 * 重复规则从界面直接映射到 [Recurrence]，所以「每周」记的是起始日的星期、
 * 「每月」记的是起始日的号数、农历每年记的是起始日换算出的农历月日 ——
 * 让用户自己填这三个数字是徒劳的，他们填不对。
 */
@Composable
fun EventEditorSheet(
    event: EventEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (EventEntity) -> Unit,
    onDelete: (EventEntity) -> Unit,
) {
    var draft by remember(event.id) { mutableStateOf(event) }
    var hasUntil by remember(event.id, event.repeatUntilEpochDay) {
        mutableStateOf(event.repeatUntilEpochDay != null)
    }

    val startDate = LocalDate.ofEpochDay(draft.startEpochDay)
    val endDate = LocalDate.ofEpochDay(draft.endEpochDay)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it) },
                label = { Text("备注") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("时间")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("全天", modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.allDay,
                    onCheckedChange = { checked ->
                        draft = draft.copy(
                            allDay = checked,
                            startMillis = if (checked) null else defaultStartMillis(startDate),
                            endMillis = if (checked) null else defaultStartMillis(endDate),
                        )
                    },
                )
            }
            DateField(label = "开始", value = startDate) { date ->
                draft = draft.copy(startEpochDay = date.toEpochDay())
                if (endDate.isBefore(date)) draft = draft.copy(endEpochDay = date.toEpochDay())
            }
            DateField(label = "结束", value = endDate) { date ->
                val safe = if (date.isBefore(startDate)) startDate else date
                draft = draft.copy(endEpochDay = safe.toEpochDay())
            }
            if (!draft.allDay) {
                MinuteOfDayField(label = "开始时刻", minuteOfDay = draft.startMillis?.minuteOfDay()) { minute ->
                    draft = draft.copy(
                        startMillis = minute?.let { startDate.atTime(it / 60, it % 60).toMillis() }
                            ?: defaultStartMillis(startDate),
                    )
                }
                MinuteOfDayField(label = "结束时刻", minuteOfDay = draft.endMillis?.minuteOfDay()) { minute ->
                    draft = draft.copy(
                        endMillis = minute?.let { endDate.atTime(it / 60, it % 60).toMillis() }
                            ?: defaultStartMillis(endDate),
                    )
                }
            }

            SectionTitle("重复")
            OptionField(
                label = "重复规则",
                value = RecurrenceLabels[draft.recurrenceIndex()],
                options = RecurrenceLabels,
            ) { index ->
                draft = draft.copy(recurrenceText = RecurrenceCodec.encode(recurrenceFor(index, startDate)))
            }
            if (draft.recurrenceText != "none") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("设置截止日", modifier = Modifier.weight(1f))
                    Switch(
                        checked = hasUntil,
                        onCheckedChange = { checked ->
                            hasUntil = checked
                            draft = draft.copy(
                                repeatUntilEpochDay = if (checked) {
                                    startDate.plusMonths(3).toEpochDay()
                                } else {
                                    null
                                },
                            )
                        },
                    )
                }
                if (hasUntil) {
                    DateField(label = "截止", value = draft.repeatUntilEpochDay?.let(LocalDate::ofEpochDay) ?: startDate) {
                        draft = draft.copy(repeatUntilEpochDay = it.toEpochDay())
                    }
                }
            }

            SectionTitle("提醒与归类")
            OptionField(
                label = "提醒",
                value = ReminderLabels[ReminderMinutes.indexOf(draft.remindMinutes).coerceAtLeast(0)],
                options = ReminderLabels,
            ) { index ->
                draft = draft.copy(remindMinutes = ReminderMinutes[index])
            }
            OptionField(
                label = "分类",
                value = categories.find { it.id == draft.categoryId }?.name ?: "未分类",
                options = listOf("未分类") + categories.map { it.name },
            ) { index ->
                draft = draft.copy(categoryId = if (index == 0) null else categories[index - 1].id)
            }

            ColorSwatchField(
                label = "颜色",
                selectedArgb = draft.colorArgb,
                onSelect = { draft = draft.copy(colorArgb = it) },
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSave(draft.copy(title = draft.title.ifBlank { "未命名日程" })) },
                    modifier = Modifier.weight(1f),
                ) { Text(if (draft.id == 0L) "添加" else "保存") }
                if (draft.id != 0L) {
                    TextButton(onClick = { onDelete(draft) }) { Text("删除") }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
                Text("取消")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun Long.minuteOfDay(): Int =
    java.time.Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .let { it.hour * 60 + it.minute }

private fun LocalDateTime.toMillis(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun defaultStartMillis(date: LocalDate): Long =
    date.atTime(9, 0).toMillis()

private fun EventEntity.recurrenceIndex(): Int = when (val rule = RecurrenceCodec.decode(recurrenceText)) {
    Recurrence.None -> 0
    is Recurrence.Weekly -> when (rule.days) {
        DayOfWeekMask.ALL -> 1
        DayOfWeekMask.WORKDAYS -> 2
        else -> 3
    }
    is Recurrence.Monthly -> 4
    Recurrence.Yearly -> 5
    is Recurrence.LunarYearly -> 6
}

private fun recurrenceFor(index: Int, start: LocalDate): Recurrence = when (index) {
    0 -> Recurrence.None
    1 -> Recurrence.Weekly(DayOfWeekMask.ALL)
    2 -> Recurrence.Weekly(DayOfWeekMask.WORKDAYS)
    3 -> Recurrence.Weekly(DayOfWeekMask.of(start.dayOfWeek))
    4 -> Recurrence.Monthly(start.dayOfMonth)
    5 -> Recurrence.Yearly
    else -> {
        val info = ChineseCalendar.infoOf(start)
        Recurrence.LunarYearly(
            lunarMonth = info.lunarMonthNumeric,
            lunarDay = info.lunarDayNumeric,
            leapFallback = true,
        )
    }
}

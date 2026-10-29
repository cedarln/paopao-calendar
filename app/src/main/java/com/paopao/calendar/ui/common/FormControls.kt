package com.paopao.calendar.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.paopao.calendar.ui.theme.SwatchPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 表单用的日期/时间选择控件。
 *
 * [modifier] 排在回调之前是刻意的：回调放最后一个参数，调用方才能用尾随 lambda 写。
 *
 * 时间选择用两个下拉而不是 M3 TimePicker：初版只需要「整点与五分钟刻度」，
 * 而 TimePicker 的表盘交互在这个尺寸的表单里既占高度又难点准。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate,
    modifier: Modifier = Modifier,
    onValueChange: (LocalDate) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    FormButton(
        label = label,
        value = value.format(CommonFormatters.chineseDate),
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun OptionField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onOptionSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        FormButton(label = label, value = value, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onOptionSelected(index)
                    },
                )
            }
        }
    }
}

/** 分钟时刻（0..1439）与「时:分」文本之间的双向选择控件。 */
@Composable
fun MinuteOfDayField(
    label: String,
    minuteOfDay: Int?,
    modifier: Modifier = Modifier,
    onValueChange: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = minuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "全天"
    Box(modifier = modifier.fillMaxWidth()) {
        FormButton(label = label, value = display, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("全天") }, onClick = { expanded = false; onValueChange(null) })
            // 15 分钟一档：待办类应用里精确到分钟的输入频率极低，但 5 分钟档会让菜单有 288 项。
            (0 until 96).forEach { slot ->
                val minute = slot * 15
                DropdownMenuItem(
                    text = { Text("%02d:%02d".format(minute / 60, minute % 60)) },
                    onClick = { expanded = false; onValueChange(minute) },
                )
            }
        }
    }
}

@Composable
private fun FormButton(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

object CommonFormatters {
    val chineseDate: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日", java.util.Locale.CHINA)
    val chineseMonthDay: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("M月d日", java.util.Locale.CHINA)
    val chineseWeekday: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.CHINA)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 6.dp),
    )
}

/**
 * 一行标题 + 右侧开关的表单控件。
 *
 * 月视图设置里那版是私有的，习惯表单也要用（「无期限」这个开关没有它就只能做成两个页面），
 * 所以上提到公共控件里，而不是在第二个文件里再抄一份同样的 Row。
 */
@Composable
fun SwitchField(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 六色色板选择行。选色这件事在日程、事项、习惯、纪念日四个表单里是同构的。 */
@Composable
fun ColorSwatchField(
    label: String,
    selectedArgb: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(label)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SwatchPalette.forEach { argb ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (selectedArgb == argb) 3.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(argb) },
                )
            }
        }
    }
}


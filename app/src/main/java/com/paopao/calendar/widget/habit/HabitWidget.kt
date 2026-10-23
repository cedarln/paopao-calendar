package com.paopao.calendar.widget.habit

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.paopao.calendar.appContainer
import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.data.local.entity.HabitLogEntity
import com.paopao.calendar.ui.navigation.Routes
import com.paopao.calendar.widget.WidgetStyles
import com.paopao.calendar.widget.dayNight
import com.paopao.calendar.widget.openRoute
import com.paopao.calendar.widget.toggleHabitAction
import com.paopao.calendar.widget.widgetRoot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 习惯打卡插件。
 *
 * 与今日待办一样可以就地打勾：习惯是唯一「做完了但没产出」的事项类型，
 * 记下来的成本越低，才越可能真的记下来。
 */
data class HabitWidgetRow(
    val habit: HabitEntity,
    val weekDone: Int,
    val weekTarget: Int,
    val doneToday: Boolean,
)

class HabitWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val today = LocalDate.now()
        val weekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val habits = container.habitRepository.findActive().take(MAX_ROWS)
        val logs = container.habitRepository.findLogs(weekMonday, today).groupBy(HabitLogEntity::habitId)
        val rows = habits.map { habit ->
            val days = logs[habit.id].orEmpty().map { it.epochDay }.toSet()
            val target = if (habit.targetPerWeek <= 0) 7 else habit.targetPerWeek
            HabitWidgetRow(
                habit = habit,
                weekDone = (weekMonday.toEpochDay()..today.toEpochDay()).count { it in days },
                weekTarget = target,
                doneToday = today.toEpochDay() in days,
            )
        }

        provideContent {
            Column(modifier = widgetRoot()) {
                Text(
                    text = "本周习惯",
                    style = WidgetStyles.title,
                    modifier = GlanceModifier.clickable(openRoute(context, Routes.HABIT)),
                )
                Spacer(GlanceModifier.height(6.dp))
                if (rows.isEmpty()) {
                    Text(text = "还没有习惯", style = WidgetStyles.muted)
                }
                rows.forEach { row -> HabitRow(context, row) }
            }
        }
    }

    private companion object {
        const val MAX_ROWS = 5
    }
}

@GlanceComposable
@Composable
private fun HabitRow(context: Context, row: HabitWidgetRow) {
    val accent = habitColor(row.habit.colorArgb)
    Row(
        modifier = GlanceModifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(16.dp)
                .cornerRadius(4.dp)
                .background(if (row.doneToday) accent else uncheckedColor),
            contentAlignment = Alignment.Center,
        ) {
            if (row.doneToday) {
                Text("✓", style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp))
            }
        }
        Text(
            text = row.habit.name,
            style = WidgetStyles.body,
            modifier = GlanceModifier
                .defaultWeight()
                .padding(start = 8.dp)
                .clickable(toggleHabitAction(row.habit.id)),
        )
        Text(
            text = "${row.weekDone}/${row.weekTarget}",
            style = WidgetStyles.muted,
            modifier = GlanceModifier.clickable(openRoute(context, Routes.HABIT)),
        )
    }
}

private val uncheckedColor = dayNight(0xFFE3E7E5, 0xFF3A403E)

/** 习惯色是用户自己挑的，日/夜都保持原色，不做「智能提亮」。 */
private fun habitColor(argb: Int): ColorProvider = ColorProvider(Color(argb))

class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
}

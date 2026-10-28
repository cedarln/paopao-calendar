package com.paopao.calendar.widget.training

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
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.paopao.calendar.appContainer
import com.paopao.calendar.data.local.entity.WorkoutPlanEntity
import com.paopao.calendar.domain.workout.TrainingMath
import com.paopao.calendar.ui.navigation.Routes
import com.paopao.calendar.widget.WidgetStyles
import com.paopao.calendar.widget.checkInWorkoutAction
import com.paopao.calendar.widget.dayNight
import com.paopao.calendar.widget.openRoute
import com.paopao.calendar.widget.widgetRoot
import java.time.LocalDate

/**
 * 今日训练插件。
 *
 * 与习惯插件同一个立场：打卡这件事离屏幕越近越可能发生。这里只列「今天排到的计划」，
 * 而不是全部计划 —— 桌面那 2×2 的位置放不下一个列表页，而用户要回答的问题只有一个：
 * 今天该练的练了吗。
 */
data class TrainingWidgetRow(
    val plan: WorkoutPlanEntity,
    val checkedIn: Boolean,
)

class TrainingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val today = LocalDate.now()
        val plans = container.workoutRepository.findPlans().map { it.plan }
        val logs = container.workoutRepository.logsOnDay(today)
        val loggedPlanIds = logs.mapNotNull { it.planId }.toSet()
        val rows = plans
            .filter { TrainingMath.isScheduledOn(it.weekMask, today) }
            .map { TrainingWidgetRow(it, it.id in loggedPlanIds) }
            .take(MAX_ROWS)
        val minutes = logs.sumOf { it.durationSec } / 60
        val freeMinutes = logs.filter { it.planId == null }.sumOf { it.durationSec } / 60
        val streak = container.workoutRepository.streak(today)

        provideContent {
            Column(modifier = widgetRoot()) {
                Text(
                    text = "今日训练",
                    style = WidgetStyles.title,
                    modifier = GlanceModifier.clickable(openRoute(context, Routes.TRAINING)),
                )
                Spacer(GlanceModifier.height(6.dp))
                if (rows.isEmpty()) {
                    Text(
                        text = if (minutes > 0) "今天没排计划，已经练了 $minutes 分钟" else "今天没排训练",
                        style = WidgetStyles.muted,
                    )
                }
                rows.forEach { row -> TrainingRow(context, row) }
                if (freeMinutes > 0) {
                    Text(
                        text = "自由训练 $freeMinutes 分钟",
                        style = WidgetStyles.muted,
                        modifier = GlanceModifier.padding(top = 2.dp),
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
                // 只报今天与连胜：插件读的是当天的日志，凑不出「本周」，硬算出来只会是个假数
                Text(
                    text = "今天 $minutes 分钟 · 连续 $streak 天",
                    style = WidgetStyles.accent,
                    modifier = GlanceModifier.clickable(openRoute(context, Routes.TRAINING)),
                )
            }
        }
    }

    private companion object {
        const val MAX_ROWS = 4
    }
}

@GlanceComposable
@Composable
private fun TrainingRow(context: Context, row: TrainingWidgetRow) {
    val accent = ColorProvider(Color(row.plan.colorArgb))
    Row(
        modifier = GlanceModifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(16.dp)
                .cornerRadius(8.dp)
                .background(if (row.checkedIn) accent else uncheckedColor),
            contentAlignment = Alignment.Center,
        ) {
            if (row.checkedIn) {
                Text("✓", style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp))
            }
        }
        Text(
            text = row.plan.name.ifBlank { "未命名计划" },
            style = WidgetStyles.body,
            modifier = GlanceModifier
                .defaultWeight()
                .padding(start = 8.dp)
                .clickable(checkInWorkoutAction(row.plan.id)),
        )
        Text(
            text = "约 ${row.plan.estimatedMinutes} 分钟",
            style = WidgetStyles.muted,
            // 已经打过的行也要能点：手滑打了一次之后，桌面是唯一不用进 App 就能撤销的地方
            modifier = GlanceModifier.clickable(checkInWorkoutAction(row.plan.id)),
        )
    }
}

private val uncheckedColor = dayNight(0xFFE3E7E5, 0xFF3A403E)

class TrainingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrainingWidget()
}

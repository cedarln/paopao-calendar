package com.paopao.calendar.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.paopao.calendar.MainActivity

/**
 * 插件层的共用样式与跳转。
 *
 * 颜色写成「日/夜」成对的 [ColorProvider] 而不是套用 Material 主题：插件不在应用的
 * 组合树里，拿不到 `LocalConfiguration`，用主题色反而会永远停在浅色。
 */
internal fun dayNight(lightArgb: Long, darkArgb: Long): ColorProvider =
    androidx.glance.color.ColorProvider(Color(lightArgb), Color(darkArgb))

internal val WidgetInk = dayNight(0xFF191C1B, 0xFFE1E8E5)
internal val WidgetMuted = dayNight(0xFF5A6169, 0xFFA9B3AE)
internal val WidgetAccent = dayNight(0xFF227a68, 0xFF7FD3BE)
internal val WidgetSurface = dayNight(0xFFFBFDFC, 0xFF191C1B)
internal val WidgetCellBackground = dayNight(0xFFEAF2EF, 0xFF2A302E)

internal object WidgetStyles {
    val title = TextStyle(color = WidgetInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(color = WidgetInk, fontSize = 12.sp)
    val bodyBold = TextStyle(color = WidgetInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val muted = TextStyle(color = WidgetMuted, fontSize = 11.sp)
    val accent = TextStyle(color = WidgetAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val count = TextStyle(color = WidgetAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val day = TextStyle(color = WidgetInk, fontSize = 10.sp)
    val dayToday = TextStyle(color = WidgetAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val outOfMonth = TextStyle(color = WidgetMuted, fontSize = 10.sp)
    val dot = TextStyle(color = WidgetMuted, fontSize = 6.sp)
}

/** 居中对齐写在 [TextStyle] 里：Glance 的 Text 没有独立的 textAlign 参数。 */
internal fun TextStyle.centered(): TextStyle = copy(textAlign = TextAlign.Center)

internal fun widgetRoot(): GlanceModifier = GlanceModifier
    .fillMaxSize()
    .cornerRadius(16.dp)
    .background(WidgetSurface)
    .padding(10.dp)

/** 点某一天：落到日历页并定位到那天。 */
internal fun openDay(context: Context, epochDay: Long): Action =
    actionStartActivity(MainActivity.intentToDay(context, epochDay))

/** 点某个模块：落到对应的顶级页。 */
internal fun openRoute(context: Context, route: String): Action =
    actionStartActivity(MainActivity.intentToRoute(context, route))

/** 就地勾掉一条事项 / 打一次卡，不打开应用。 */
internal fun toggleInventoryAction(inventoryId: Long): Action =
    actionRunCallback<ToggleInventoryDoneCallback>(actionParametersOf(ParamInventoryId to inventoryId))

internal fun toggleHabitAction(habitId: Long): Action =
    actionRunCallback<ToggleHabitTodayCallback>(actionParametersOf(ParamHabitId to habitId))

/** 今天练没练，在桌面上点一下就完事；再点一次撤销。 */
internal fun checkInWorkoutAction(planId: Long): Action =
    actionRunCallback<CheckInWorkoutCallback>(actionParametersOf(ParamWorkoutPlanId to planId))

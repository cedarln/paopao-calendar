package com.paopao.calendar.widget.inventory

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.paopao.calendar.R
import com.paopao.calendar.appContainer
import com.paopao.calendar.data.local.entity.InventoryEntity
import com.paopao.calendar.domain.model.DayCell
import com.paopao.calendar.domain.model.MonthGrid
import com.paopao.calendar.domain.model.Quadrant
import com.paopao.calendar.ui.navigation.Routes
import com.paopao.calendar.widget.ParamInventoryId
import com.paopao.calendar.widget.ToggleInventoryDoneCallback
import com.paopao.calendar.widget.WidgetAccent
import com.paopao.calendar.widget.WidgetCellBackground
import com.paopao.calendar.widget.WidgetMuted
import com.paopao.calendar.widget.WidgetStyles
import com.paopao.calendar.widget.dayNight
import com.paopao.calendar.widget.openDay
import com.paopao.calendar.widget.openRoute
import com.paopao.calendar.widget.toggleInventoryAction
import com.paopao.calendar.widget.centered
import com.paopao.calendar.widget.widgetRoot
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * 今日待办插件。
 *
 * 只列 6 条：桌面插件不是清单的镜像，是「今天还剩几件」的提醒。
 * 要看得全，点标题进应用。
 */
class TodoInventoryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val today = LocalDate.now()
        val items = container.inventoryRepository
            .itemsOn(container.inventoryRepository.scheduledSnapshot(today, today), today)
            .take(MAX_ROWS)

        provideContent {
            Column(modifier = widgetRoot()) {
                Text(
                    text = "今天 ${today.monthValue}月${today.dayOfMonth}日",
                    style = WidgetStyles.title,
                    modifier = GlanceModifier.clickable(openRoute(context, Routes.INVENTORY)),
                )
                Spacer(GlanceModifier.height(6.dp))
                if (items.isEmpty()) {
                    Text(context.getString(R.string.widget_empty), style = WidgetStyles.muted)
                }
                items.forEach { item -> TodoRow(context, item) }
            }
        }
    }

    private companion object {
        const val MAX_ROWS = 6
    }
}

@GlanceComposable
@Composable
private fun TodoRow(context: Context, item: InventoryEntity) {
    Row(
        modifier = GlanceModifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (item.done) "☑" else "☐",
            style = TextStyle(color = WidgetAccent, fontSize = 14.sp),
            modifier = GlanceModifier
                .clickable(toggleInventoryAction(item.id))
                .padding(end = 6.dp),
        )
        Text(
            text = item.title,
            style = if (item.done) WidgetStyles.muted else WidgetStyles.body,
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(openRoute(context, Routes.INVENTORY)),
        )
    }
}

/** 四象限插件：四格各一个数字，重点是「哪一格堆得离谱」。 */
class QuadrantInventoryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val roots = context.appContainer.inventoryRepository.activeRootSnapshot()
        val counts = Quadrant.entries.associateWith { quadrant ->
            roots.count { Quadrant.fromIndex(it.quadrantIndex) == quadrant }
        }
        provideContent {
            Column(modifier = widgetRoot()) {
                Text(
                    text = "四象限",
                    style = WidgetStyles.title,
                    modifier = GlanceModifier.clickable(openRoute(context, Routes.INVENTORY)),
                )
                Spacer(GlanceModifier.height(6.dp))
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    QuadrantCell(context, Quadrant.Q1, counts.getValue(Quadrant.Q1), GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(6.dp))
                    QuadrantCell(context, Quadrant.Q2, counts.getValue(Quadrant.Q2), GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.height(6.dp))
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    QuadrantCell(context, Quadrant.Q3, counts.getValue(Quadrant.Q3), GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(6.dp))
                    QuadrantCell(context, Quadrant.Q4, counts.getValue(Quadrant.Q4), GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

private val QuadrantHints = mapOf(
    Quadrant.Q1 to "马上做",
    Quadrant.Q2 to "计划做",
    Quadrant.Q3 to "委派掉",
    Quadrant.Q4 to "可以删",
)

private val QuadrantAccents = mapOf(
    Quadrant.Q1 to dayNight(0xFFD9534F, 0xFFE88A85),
    Quadrant.Q2 to dayNight(0xFF2E8B7A, 0xFF7FD3BE),
    Quadrant.Q3 to dayNight(0xFFB8860B, 0xFFE0C168),
    Quadrant.Q4 to dayNight(0xFF8D9099, 0xFFA9ADB5),
)

/** [cellWeight] 由调用方在 Row 作用域里取，权重只能在父容器的 Scope 里要。 */
@GlanceComposable
@Composable
private fun QuadrantCell(
    context: Context,
    quadrant: Quadrant,
    count: Int,
    cellWeight: GlanceModifier,
) {
    Column(
        modifier = cellWeight
            .cornerRadius(12.dp)
            .background(WidgetCellBackground)
            .clickable(openRoute(context, Routes.INVENTORY))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$count 件",
            style = TextStyle(color = QuadrantAccents.getValue(quadrant), fontSize = 16.sp),
        )
        Text(
            text = QuadrantHints.getValue(quadrant),
            style = WidgetStyles.muted,
        )
    }
}

/** 月视图插件：当月网格 + 事项密度，点某天进日历。 */
class MonthInventoryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val today = LocalDate.now()
        val month = today.withDayOfMonth(1)
        val engine = container.calendarEngine(container.settingsStore.settings.first().firstDayOfWeek)
        val grid = engine.monthGrid(month, today, emptySet())
        val from = grid.cells.first().date
        val to = grid.cells.last().date
        val items = container.inventoryRepository.scheduledSnapshot(from, to)
        val eventDays = container.eventRepository.eventDaysInRange(from, to)
        val itemDays = container.inventoryRepository.scheduledDays(items, from, to)

        provideContent { MonthContent(context, month, grid, eventDays, itemDays) }
    }
}

private val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@GlanceComposable
@Composable
private fun MonthContent(
    context: Context,
    month: LocalDate,
    grid: MonthGrid,
    eventDays: Set<LocalDate>,
    itemDays: Set<LocalDate>,
) {
    Column(modifier = widgetRoot()) {
        Text(
            text = "${month.year}年${month.monthValue}月",
            style = WidgetStyles.title,
            modifier = GlanceModifier.clickable(openDay(context, month.toEpochDay())),
        )
        Spacer(GlanceModifier.height(2.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WeekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = WidgetStyles.muted.centered(),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
        grid.weeks.forEach { week ->
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                week.forEach { cell ->
                    MonthDayCell(
                        context = context,
                        cell = cell,
                        marker = dayMarker(cell.date in itemDays, cell.date in eventDays),
                        cellWeight = GlanceModifier.defaultWeight(),
                    )
                }
            }
        }
    }
}

/** 两个独立的事实（有事项 / 有日程）压成一格标记，避免在格子里画两行圆点。 */
private fun dayMarker(hasItem: Boolean, hasEvent: Boolean): String = when {
    hasItem && hasEvent -> "●●"
    hasItem -> "●"
    hasEvent -> "○"
    else -> " "
}

@GlanceComposable
@Composable
private fun MonthDayCell(
    context: Context,
    cell: DayCell,
    marker: String,
    cellWeight: GlanceModifier,
) {
    val numberStyle = when {
        !cell.inCurrentMonth -> WidgetStyles.outOfMonth
        cell.isToday -> WidgetStyles.dayToday
        else -> WidgetStyles.day
    }.centered()
    Column(
        modifier = cellWeight
            .clickable(openDay(context, cell.date.toEpochDay()))
            .padding(vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${cell.date.dayOfMonth}", style = numberStyle)
        Text(marker, style = WidgetStyles.dot.centered())
    }
}

class TodoInventoryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoInventoryWidget()
}

class QuadrantInventoryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuadrantInventoryWidget()
}

class MonthInventoryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthInventoryWidget()
}

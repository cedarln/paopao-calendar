package com.paopao.calendar.widget.memorial

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
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.paopao.calendar.appContainer
import com.paopao.calendar.data.local.entity.MemorialEntity
import com.paopao.calendar.data.repository.MemorialOccurrence
import com.paopao.calendar.ui.navigation.Routes
import com.paopao.calendar.widget.WidgetAccent
import com.paopao.calendar.widget.WidgetInk
import com.paopao.calendar.widget.WidgetMuted
import com.paopao.calendar.widget.WidgetStyles
import com.paopao.calendar.widget.openRoute
import com.paopao.calendar.widget.widgetRoot
import java.time.LocalDate

/**
 * 纪念日插件：最近几个纪念日与倒数天数。
 *
 * 「倒数」是这个模块唯一有价值的信息，所以数字放右边、字号最大；
 * 农历纪念日的落点每年都在变，这里必须走仓储层的历法换算而不是简单月日比较。
 */
class MemorialWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val today = LocalDate.now()
        val rows = container.memorialRepository.findAll()
            .map { MemorialRowData(it, container.memorialRepository.nextOccurrence(it, today)) }
            .sortedBy { it.occurrence.daysUntil }
            .take(MAX_ROWS)

        provideContent {
            Column(modifier = widgetRoot()) {
                Text(
                    text = "纪念日",
                    style = WidgetStyles.title,
                    modifier = GlanceModifier.clickable(openRoute(context, Routes.MEMORIAL)),
                )
                Spacer(GlanceModifier.height(6.dp))
                if (rows.isEmpty()) {
                    Text(text = "还没有纪念日", style = WidgetStyles.muted)
                }
                rows.forEach { row -> MemorialRow(row) }
            }
        }
    }

    private companion object {
        const val MAX_ROWS = 3
    }
}

private data class MemorialRowData(
    val memorial: MemorialEntity,
    val occurrence: MemorialOccurrence,
)

@GlanceComposable
@Composable
private fun MemorialRow(row: MemorialRowData) {
    val days = row.occurrence.daysUntil
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.memorial.name,
            style = WidgetStyles.body,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = when {
                row.occurrence.isToday -> "今天"
                days >= 0 -> "$days 天后"
                else -> "${row.occurrence.passed} 天"
            },
            style = TextStyle(
                color = if (row.occurrence.isToday) WidgetAccent else WidgetInk,
                fontSize = 12.sp,
            ),
            modifier = GlanceModifier.padding(start = 8.dp),
        )
    }
}

class MemorialWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MemorialWidget()
}

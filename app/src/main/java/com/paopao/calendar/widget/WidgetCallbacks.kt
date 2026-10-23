package com.paopao.calendar.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.paopao.calendar.appContainer
import com.paopao.calendar.widget.habit.HabitWidget
import com.paopao.calendar.widget.inventory.TodoInventoryWidget
import java.time.LocalDate

/**
 * 插件上的就地操作。
 *
 * 勾一下就算完成，是为了让「在桌面上清今天」真的可行 —— 如果点一下只是打开应用，
 * 用户就不会把它当待办用，而今日待办插件的全部意义在于不用解锁进 App。
 *
 * 每个回调都显式刷新自己：Room 的写入不会通知 RemoteViews，
 * 靠 `updatePeriodMillis`（系统最短 30 分钟）刷新等于没刷。
 */
internal val ParamInventoryId = ActionParameters.Key<Long>("paopao.param.INVENTORY_ID")
internal val ParamHabitId = ActionParameters.Key<Long>("paopao.param.HABIT_ID")

class ToggleInventoryDoneCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val id = parameters[ParamInventoryId] ?: return
        val container = context.appContainer
        val item = container.inventoryRepository.findById(id) ?: return
        container.inventoryRepository.setDone(id, done = !item.done)
        TodoInventoryWidget().update(context, glanceId)
    }
}

class ToggleHabitTodayCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val id = parameters[ParamHabitId] ?: return
        context.appContainer.habitRepository.toggle(id, LocalDate.now())
        HabitWidget().update(context, glanceId)
    }
}

package com.paopao.calendar.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.paopao.calendar.appContainer
import com.paopao.calendar.domain.workout.TrainingMath
import com.paopao.calendar.widget.habit.HabitWidget
import com.paopao.calendar.widget.inventory.TodoInventoryWidget
import com.paopao.calendar.widget.training.TrainingWidget
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
internal val ParamWorkoutPlanId = ActionParameters.Key<Long>("paopao.param.WORKOUT_PLAN_ID")

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

/**
 * 训练计划的就地打卡 / 撤销。
 *
 * 与习惯那套「同一个勾既能打也能撤」不同，这里先看今天有没有这个计划的日志：
 * 有就删掉，没有才写。桌面插件上没有「时长」这个输入位，所以按计划的预计时长记账 ——
 * 想记实际耗时就得进计时页，那是另一条路径。
 */
class CheckInWorkoutCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val planId = parameters[ParamWorkoutPlanId] ?: return
        val container = context.appContainer
        val today = LocalDate.now()
        val existing = container.workoutRepository.logsOnDay(today).firstOrNull { it.planId == planId }
        if (existing != null) {
            container.workoutRepository.deleteLog(existing.id)
        } else {
            val plan = container.workoutRepository.findPlan(planId)?.plan ?: return
            container.workoutRepository.checkIn(plan, today, TrainingMath.quickCheckInSec(plan.estimatedMinutes))
        }
        TrainingWidget().update(context, glanceId)
    }
}

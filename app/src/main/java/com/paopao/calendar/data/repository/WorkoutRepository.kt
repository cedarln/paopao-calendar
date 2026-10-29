package com.paopao.calendar.data.repository

import com.paopao.calendar.data.local.dao.WorkoutDao
import com.paopao.calendar.data.local.entity.PlanActionEntity
import com.paopao.calendar.data.local.entity.PlanWithActions
import com.paopao.calendar.data.local.entity.WorkoutLogEntity
import com.paopao.calendar.data.local.entity.WorkoutPlanEntity
import com.paopao.calendar.domain.workout.TrainingMath
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * 训练计划与打卡的读写口。
 *
 * 计划的动作是「整版提交」而不是逐条增删：编辑页一次交出完整清单，
 * 仓储负责删净重写。这样 UI 侧不需要维护三种操作，也不会出现
 * 「改了名字但没落库」这种半更新状态。
 */
class WorkoutRepository(private val dao: WorkoutDao) {

    fun observePlans(): Flow<List<PlanWithActions>> = dao.observePlansWithActions()

    suspend fun findPlans(): List<PlanWithActions> = dao.findPlansWithActions()

    /**
     * 导出用：计划（含已归档）、动作、打卡三条独立快照。
     *
     * 没用 [findPlans] 的 PlanWithActions：它靠 Room 的 @Relation 逐计划回查动作，
     * 备份要的是「表对表原样」，把关系拍平了反而要在导入侧再拆回去。
     */
    suspend fun findAllPlansIncludingArchived(): List<WorkoutPlanEntity> = dao.findAllPlans()

    suspend fun findAllActions(): List<PlanActionEntity> = dao.findAllActions()

    suspend fun findAllLogs(): List<WorkoutLogEntity> = dao.findAllLogs()

    suspend fun findPlan(id: Long): PlanWithActions? = dao.findPlanWithActions(id)

    fun observeArchivedPlans(): Flow<List<WorkoutPlanEntity>> = dao.observeArchivedPlans()

    /** 保存计划本体，返回真实 id（新建时调用方拿 0 传进来）。 */
    suspend fun upsertPlan(plan: WorkoutPlanEntity): Long = if (plan.id == 0L) {
        dao.insertPlan(plan)
    } else {
        dao.updatePlan(plan)
        plan.id
    }

    suspend fun saveActions(planId: Long, actions: List<PlanActionEntity>) =
        dao.replaceActions(planId, actions)

    suspend fun findActions(planId: Long): List<PlanActionEntity> = dao.findActions(planId)

    suspend fun archivePlan(planId: Long) = dao.archivePlan(planId)

    suspend fun deletePlan(plan: WorkoutPlanEntity) = dao.deletePlan(plan)

    /**
     * 打一次卡。
     *
     * [planName] 在写入时就固化进日志：计划之后被改名或删掉，
     * 历史页仍然要能回答「那天练的是什么」。
     */
    suspend fun checkIn(
        plan: WorkoutPlanEntity?,
        date: LocalDate,
        durationSec: Int,
        startedAtMillis: Long? = null,
        note: String = "",
    ): Long = dao.insertLog(
        WorkoutLogEntity(
            epochDay = date.toEpochDay(),
            planId = plan?.id,
            planName = plan?.name ?: WorkoutLogEntity.FALLBACK_NAME,
            durationSec = durationSec,
            startedAtMillis = startedAtMillis,
            note = note,
        ),
    )

    suspend fun deleteLog(logId: Long) = dao.deleteLog(logId)

    fun observeLogsOnDay(date: LocalDate): Flow<List<WorkoutLogEntity>> =
        dao.observeLogsOnDay(date.toEpochDay())

    suspend fun logsOnDay(date: LocalDate): List<WorkoutLogEntity> = dao.findLogsOnDay(date.toEpochDay())

    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<WorkoutLogEntity>> =
        dao.observeLogsInRange(from.toEpochDay(), to.toEpochDay())

    suspend fun findLogs(from: LocalDate, to: LocalDate): List<WorkoutLogEntity> =
        dao.findLogsInRange(from.toEpochDay(), to.toEpochDay())

    /** 月视图只关心「哪些天练过」。 */
    fun observeLoggedDays(from: LocalDate, to: LocalDate): Flow<List<Long>> =
        dao.observeLoggedDays(from.toEpochDay(), to.toEpochDay())

    suspend fun loggedDays(from: LocalDate, to: LocalDate): Set<LocalDate> =
        dao.findLoggedDays(from.toEpochDay(), to.toEpochDay()).map { LocalDate.ofEpochDay(it) }.toSet()

    fun observeLogCountForPlan(planId: Long): Flow<Int> = dao.observeLogCountForPlan(planId)

    /** 连续训练天数；今天没练不清零，与习惯那一套是同一个口径。 */
    suspend fun streak(today: LocalDate, lookbackDays: Int = 365): Int =
        TrainingMath.streak(loggedDays(today.minusDays(lookbackDays.toLong()), today), today, lookbackDays)
}

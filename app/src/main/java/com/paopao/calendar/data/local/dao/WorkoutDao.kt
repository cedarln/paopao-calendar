package com.paopao.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.paopao.calendar.data.local.entity.PlanActionEntity
import com.paopao.calendar.data.local.entity.PlanWithActions
import com.paopao.calendar.data.local.entity.WorkoutLogEntity
import com.paopao.calendar.data.local.entity.WorkoutPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    /**
     * 计划列表。按「最近才练过的排前面」不如按创建时间稳：
     * 训练频率是用户自己定的，排序依据越简单越不容易看起来乱跳。
     */
    @Transaction
    @Query("SELECT * FROM workout_plans WHERE archivedAtMillis IS NULL ORDER BY createdAtMillis DESC")
    fun observePlansWithActions(): Flow<List<PlanWithActions>>

    @Transaction
    @Query("SELECT * FROM workout_plans WHERE archivedAtMillis IS NULL ORDER BY createdAtMillis DESC")
    suspend fun findPlansWithActions(): List<PlanWithActions>

    @Transaction
    @Query("SELECT * FROM workout_plans WHERE id = :id")
    suspend fun findPlanWithActions(id: Long): PlanWithActions?

    @Query("SELECT * FROM workout_plans WHERE archivedAtMillis IS NOT NULL ORDER BY archivedAtMillis DESC")
    fun observeArchivedPlans(): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plans WHERE id = :id")
    suspend fun findPlan(id: Long): WorkoutPlanEntity?

    @Insert
    suspend fun insertPlan(plan: WorkoutPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: WorkoutPlanEntity)

    @Delete
    suspend fun deletePlan(plan: WorkoutPlanEntity)

    @Query("UPDATE workout_plans SET archivedAtMillis = :now WHERE id = :id")
    suspend fun archivePlan(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM plan_actions WHERE planId = :planId ORDER BY sortOrder ASC")
    suspend fun findActions(planId: Long): List<PlanActionEntity>

    /**
     * 整体替换动作清单。
     *
     * 不做逐条 diff：编辑页提交的是「这一版长什么样」，一次删净再按顺序写入，
     * 比在 UI 侧维护增删改三种操作可靠，也顺手把 sortOrder 重新编号收在库里。
     */
    @Transaction
    suspend fun replaceActions(planId: Long, actions: List<PlanActionEntity>) {
        deleteActions(planId)
        actions.forEachIndexed { index, action ->
            insertAction(action.copy(planId = planId, id = 0, sortOrder = index))
        }
    }

    @Query("DELETE FROM plan_actions WHERE planId = :planId")
    suspend fun deleteActions(planId: Long)

    @Insert
    suspend fun insertAction(action: PlanActionEntity): Long

    @Insert
    suspend fun insertLog(log: WorkoutLogEntity): Long

    @Query("DELETE FROM workout_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("SELECT * FROM workout_logs WHERE epochDay = :epochDay ORDER BY id ASC")
    fun observeLogsOnDay(epochDay: Long): Flow<List<WorkoutLogEntity>>

    @Query("SELECT * FROM workout_logs WHERE epochDay = :epochDay ORDER BY id ASC")
    suspend fun findLogsOnDay(epochDay: Long): List<WorkoutLogEntity>

    /** 区间内的全部打卡，日/周/月统计与热力图都从这里取，避免逐日查询放大。 */
    @Query("SELECT * FROM workout_logs WHERE epochDay BETWEEN :from AND :to")
    fun observeLogsInRange(from: Long, to: Long): Flow<List<WorkoutLogEntity>>

    @Query("SELECT * FROM workout_logs WHERE epochDay BETWEEN :from AND :to")
    suspend fun findLogsInRange(from: Long, to: Long): List<WorkoutLogEntity>

    /** 月视图只要「哪几天练过」，取回日期列即可，不必搬整行。 */
    @Query("SELECT DISTINCT epochDay FROM workout_logs WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    fun observeLoggedDays(from: Long, to: Long): Flow<List<Long>>

    @Query("SELECT DISTINCT epochDay FROM workout_logs WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    suspend fun findLoggedDays(from: Long, to: Long): List<Long>

    /** 某个计划的累计打卡次数，动作库百科与计划详情用。 */
    @Query("SELECT COUNT(*) FROM workout_logs WHERE planId = :planId")
    fun observeLogCountForPlan(planId: Long): Flow<Int>
}

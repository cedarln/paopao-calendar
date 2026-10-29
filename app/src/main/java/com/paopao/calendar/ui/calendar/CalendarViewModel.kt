package com.paopao.calendar.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.CategoryEntity
import com.paopao.calendar.data.local.entity.EventEntity
import com.paopao.calendar.data.local.entity.HabitEntity
import com.paopao.calendar.data.local.entity.HabitLogEntity
import com.paopao.calendar.data.local.entity.InventoryEntity
import com.paopao.calendar.data.local.entity.MemorialEntity
import com.paopao.calendar.data.local.entity.WorkoutLogEntity
import com.paopao.calendar.data.local.entity.WorkoutPlanEntity
import com.paopao.calendar.data.settings.AppSettings
import com.paopao.calendar.domain.habit.Completion
import com.paopao.calendar.domain.habit.HabitMath
import com.paopao.calendar.domain.model.DayCell
import com.paopao.calendar.domain.model.MonthGrid
import com.paopao.calendar.domain.workout.TrainingMath
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 日历页某一天的议程：日程、到期事项、当天发生的纪念日、训练打卡与待打卡计划、已完成的习惯。 */
data class DayAgenda(
    val date: LocalDate = LocalDate.now(),
    val events: List<EventEntity> = emptyList(),
    val items: List<InventoryEntity> = emptyList(),
    val memorials: List<MemorialEntity> = emptyList(),
    val workouts: List<WorkoutLogEntity> = emptyList(),
    val planned: List<WorkoutPlanEntity> = emptyList(),
    val habits: List<Completion> = emptyList(),
) {
    val isEmpty: Boolean
        get() = events.isEmpty() && items.isEmpty() && memorials.isEmpty() &&
            workouts.isEmpty() && planned.isEmpty() && habits.isEmpty()
}

data class CalendarUiState(
    val settings: AppSettings = AppSettings(),
    val today: LocalDate = LocalDate.now(),
    val focusedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val grid: MonthGrid? = null,
    val eventDays: Set<LocalDate> = emptySet(),
    val itemDays: Set<LocalDate> = emptySet(),
    val workoutDays: Set<LocalDate> = emptySet(),
    val habitDays: Set<LocalDate> = emptySet(),
    val agenda: DayAgenda = DayAgenda(),
    val categories: List<CategoryEntity> = emptyList(),
)

/**
 * 日历页状态。
 *
 * 网格在 `flatMapLatest` 里算而不是在 Composable 里算：42 格每格一次农历换算，
 * 放在组合阶段会让每次重组都掏主线程。数据流用可见区间订阅，所以增删日程会自动刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val container: AppContainer,
    initialEpochDay: Long?,
) : ViewModel() {

    private val today = LocalDate.now()
    private val selectedDate = MutableStateFlow(
        initialEpochDay?.let(LocalDate::ofEpochDay) ?: today,
    )
    private val focusedMonth = MutableStateFlow(YearMonth.from(selectedDate.value))

    private val editingEvent = MutableStateFlow<EventEntity?>(null)
    val editingEventState: StateFlow<EventEntity?> = editingEvent.asStateFlow()

    val uiState: StateFlow<CalendarUiState> = combine(
        container.settingsStore.settings,
        selectedDate,
        focusedMonth,
    ) { settings, selected, month -> CalendarQuery(settings, selected, month) }
        .flatMapLatest { query ->
            val grid = container.calendarEngine(query.settings.firstDayOfWeek)
                .monthGrid(query.month.atDay(1), today, query.settings.contentFlags)
            val gridFrom = grid.cells.first().date
            val gridTo = grid.cells.last().date
            // combine 的定长重载只到五个流，训练的两路先并成一路
            val workouts = combine(
                container.workoutRepository.observeLogs(gridFrom, gridTo),
                container.workoutRepository.observePlans(),
            ) { logs, plans -> TrainingSlice(logs, plans.map { it.plan }) }
            // 习惯同理：习惯本体和这段时间的打卡记录要成对进来才算得出标记
            val habits = combine(
                container.habitRepository.observeActive(),
                container.habitRepository.observeLogs(gridFrom, gridTo),
            ) { list, logs -> HabitSlice(list, logs) }
            val extras = combine(workouts, habits) { training, habitRows -> AgendaExtras(training, habitRows) }
            combine(
                container.eventRepository.observeInRange(gridFrom, gridTo),
                container.inventoryRepository.observeScheduledBetween(gridFrom, gridTo),
                container.memorialRepository.observeAll(),
                container.categoryRepository.observeByScope(CategoryEntity.SCOPE_EVENT),
                extras,
            ) { events, items, memorials, categories, sources ->
                val training = sources.training
                val habitRows = sources.habits
                val completedHabits = HabitMath.completionsOn(habitRows.habits, habitRows.logs, query.selected)
                CalendarUiState(
                    settings = query.settings,
                    today = today,
                    focusedMonth = YearMonth.from(grid.month),
                    selectedDate = query.selected,
                    grid = grid,
                    eventDays = container.eventRepository.daysWithEvents(events, gridFrom, gridTo),
                    itemDays = container.inventoryRepository.scheduledDays(items, gridFrom, gridTo),
                    workoutDays = training.logs.map { LocalDate.ofEpochDay(it.epochDay) }.toSet(),
                    habitDays = HabitMath.loggedDaysInRange(
                        habitRows.habits,
                        habitRows.logs,
                        gridFrom,
                        gridTo,
                    ),
                    agenda = DayAgenda(
                        date = query.selected,
                        events = container.eventRepository.occurrencesOn(events, query.selected),
                        items = container.inventoryRepository.itemsOn(items, query.selected),
                        memorials = memorials.filter { occursOn(it, query.selected) },
                        workouts = training.logs.filter { it.epochDay == query.selected.toEpochDay() },
                        planned = TrainingMath.plansToCheckIn(training.plans, training.logs, query.selected),
                        habits = completedHabits,
                    ),
                    categories = categories,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState(today = today))

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        focusedMonth.value = YearMonth.from(date)
    }

    /**
     * 滑动换月：只换焦点月，不换选中日。
     *
     * 让选中日跟着页码走的话，翻页时下半屏的议程会跟着抖一下，
     * 而用户翻页时想看的是「这个月大概什么样」，不是「这一天的详情」。
     */
    fun focusMonth(month: YearMonth) {
        focusedMonth.value = month
    }

    fun shiftMonth(deltaMonths: Long) {
        focusedMonth.value = focusedMonth.value.plusMonths(deltaMonths)
    }

    fun goToday() = selectDate(today)

    fun openEventEditor(event: EventEntity?) {
        editingEvent.value = event ?: EventEntity(
            title = "",
            startEpochDay = selectedDate.value.toEpochDay(),
            endEpochDay = selectedDate.value.toEpochDay(),
        )
    }

    fun dismissEventEditor() {
        editingEvent.value = null
    }

    fun saveEvent(event: EventEntity) {
        viewModelScope.launch {
            val savedId = container.eventRepository.upsert(event)
            // 闹钟按 (日程, 发生日) 匹配，所以要用落库后的真实 id 重排，新建时 id 是 0 排不上。
            container.reminderScheduler.rescheduleEvent(event.copy(id = savedId))
            editingEvent.value = null
        }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch {
            if (event.id != 0L) {
                container.eventRepository.delete(event.id)
                container.reminderScheduler.cancelEvent(event.id)
            }
            editingEvent.value = null
        }
    }

    fun toggleItemDone(item: InventoryEntity) {
        viewModelScope.launch { container.inventoryRepository.setDone(item.id, !item.done) }
    }

    /**
     * 日历里一键打卡。
     *
     * 时长取计划的预计分钟数：在日历上按这一下的用户是要「记一笔」，不是要记录一次完整训练，
     * 真要计时应该去训练页跑那套工作/休息状态机。
     */
    fun checkIn(plan: WorkoutPlanEntity) {
        viewModelScope.launch {
            container.workoutRepository.checkIn(
                plan = plan,
                date = selectedDate.value,
                durationSec = TrainingMath.quickCheckInSec(plan.estimatedMinutes),
            )
        }
    }

    /** 不挂计划的临时训练：先记下来，名字留空由仓储填「自由训练」。 */
    fun checkInFreeTraining(durationSec: Int = DEFAULT_FREE_TRAINING_SEC) {
        viewModelScope.launch {
            container.workoutRepository.checkIn(
                plan = null,
                date = selectedDate.value,
                durationSec = durationSec,
            )
        }
    }

    fun undoWorkout(log: WorkoutLogEntity) {
        viewModelScope.launch { container.workoutRepository.deleteLog(log.id) }
    }

    /** 纪念日是否落在 [date]：借用仓储的推进逻辑，把「今天」换成选中日即可。 */
    private fun occursOn(memorial: MemorialEntity, date: LocalDate): Boolean =
        container.memorialRepository.nextOccurrence(memorial, date).thisYear == date

    private data class CalendarQuery(
        val settings: AppSettings,
        val selected: LocalDate,
        val month: YearMonth,
    )

    private data class TrainingSlice(
        val logs: List<WorkoutLogEntity>,
        val plans: List<WorkoutPlanEntity>,
    )

    private data class HabitSlice(
        val habits: List<HabitEntity>,
        val logs: List<HabitLogEntity>,
    )

    /** 训练与习惯各占一路，合并成第三路才挤得进 combine 的五个位置。 */
    private data class AgendaExtras(
        val training: TrainingSlice,
        val habits: HabitSlice,
    )

    companion object {
        /** 一键「自由训练」默认记 30 分钟：够小到不夸大，够大到不会被看成没练。 */
        const val DEFAULT_FREE_TRAINING_SEC = 30 * 60

        /** 表头顺序：从用户设定的周起始日排起，共 7 天。 */
        fun weekdaysStarting(firstDay: java.time.DayOfWeek): List<java.time.DayOfWeek> =
            (0..6).map { java.time.DayOfWeek.of((firstDay.value - 1 + it) % 7 + 1) }

        /** 月视图 6 行 × 7 列；周视图只取选中日所在的那一行。 */
        fun visibleWeeks(grid: MonthGrid, selected: LocalDate, weekOnly: Boolean): List<List<DayCell>> =
            if (!weekOnly) grid.weeks else grid.weeks.filter { week -> week.any { it.date == selected } }
    }
}

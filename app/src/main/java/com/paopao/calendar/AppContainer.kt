package com.paopao.calendar

import android.content.Context
import androidx.room.Room
import com.paopao.calendar.data.local.PaopaoDatabase
import com.paopao.calendar.data.repository.CategoryRepository
import com.paopao.calendar.data.repository.EventRepository
import com.paopao.calendar.data.repository.FocusRepository
import com.paopao.calendar.data.repository.HabitRepository
import com.paopao.calendar.data.repository.InventoryRepository
import com.paopao.calendar.data.repository.MemorialRepository
import com.paopao.calendar.data.settings.SettingsStore
import com.paopao.calendar.domain.calendar.CalendarEngine
import com.paopao.calendar.reminder.ReminderNotifier
import com.paopao.calendar.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手写依赖图。
 *
 * 不引 Hilt 是一个明确取舍：单模块、六个仓储、三个视图模型的规模下，
 * 注解处理器带来的构建时长与调试成本高于它省下的样板。等模块数超过两个再换不迟。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: PaopaoDatabase = Room.databaseBuilder(
        context.applicationContext,
        PaopaoDatabase::class.java,
        PaopaoDatabase.NAME,
    ).build()

    val settingsStore = SettingsStore(context)
    val eventRepository = EventRepository(database.eventDao())
    val inventoryRepository = InventoryRepository(database.inventoryDao())
    val habitRepository = HabitRepository(database.habitDao())
    val focusRepository = FocusRepository(database.focusDao())
    val memorialRepository = MemorialRepository(database.memorialDao())
    val categoryRepository = CategoryRepository(database.categoryDao())

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 日历引擎按用户设定的周起始日重建，所以不做成单例属性而是函数。 */
    fun calendarEngine(firstDayOfWeek: java.time.DayOfWeek) = CalendarEngine(firstDayOfWeek)

    /** 闹钟安排在 Activity、ViewModel 与广播里都要用，挂在容器上而不是各自新建。 */
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext) }

    fun seedDefaults() {
        applicationScope.launch { categoryRepository.seedDefaults() }
    }
}

class PaopaoApplication : android.app.Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.seedDefaults()
        ReminderNotifier.ensureChannel(this)
        // 重启会清空未触发的闹钟，冷启动时补排一次窗口内的提醒。
        container.applicationScope.launch { container.reminderScheduler.reschedule() }
    }
}

/** 供 ViewModel 工厂与桌面插件取用容器的入口。 */
val Context.appContainer: AppContainer
    get() = (applicationContext as PaopaoApplication).container

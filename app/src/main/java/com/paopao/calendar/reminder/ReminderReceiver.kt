package com.paopao.calendar.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.paopao.calendar.MainActivity
import com.paopao.calendar.appContainer
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 闹钟到点后的广播：回查日程并弹通知。
 *
 * 广播接收器跑在 binder 线程上且有几秒的存活窗口，所以读库要 `goAsync()` 借用协程，
 * 而不是在 onReceive 里同步查 —— Room 的主线程查询会直接抛异常。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val epochDay = intent.getLongExtra(MainActivity.EXTRA_EPOCH_DAY, -1L)
        if (eventId < 0L || epochDay < 0L) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val event = app.appContainer.eventRepository.findById(eventId)
                if (event != null) {
                    ReminderNotifier.show(app, event, LocalDate.ofEpochDay(epochDay))
                }
            } finally {
                pending.finish()
            }
        }
    }
}

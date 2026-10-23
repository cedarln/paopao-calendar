package com.paopao.calendar.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机后重排闹钟。
 *
 * 重启会清掉所有未触发的 AlarmManager 闹钟，这是提醒功能最容易「静默失效」的地方：
 * 用户只在某天没收到提醒时才发现，而那已经是既成事实。
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ReminderNotifier.ensureChannel(app)
                ReminderScheduler(app).reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}

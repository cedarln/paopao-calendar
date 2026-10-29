package com.paopao.calendar.reminder

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.paopao.calendar.MainActivity
import com.paopao.calendar.R
import com.paopao.calendar.data.local.entity.EventEntity
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * 提醒通知。
 *
 * 通知内容在**触发时**从库里重新读，而不是把标题塞进 Intent：日程改过标题之后，
 * 按旧标题弹出的通知等于错误信息。
 *
 * 未授权通知（`areNotificationsEnabled() == false`）时直接放弃：日历应用弹出的
 * 「去设置里打开通知」本身就是一种打扰。
 */
object ReminderNotifier {

    const val CHANNEL_ID = "paopao.reminders"

    /**
     * 建通道走 AndroidX 兼容层，不直接调 framework 的 `NotificationChannel`。
     *
     * minSdk 是 24，而 framework 的通道 API 从 26 才有 —— 直接调不是「多余」，是冷启动就
     * NoSuchMethodError：`ensureChannel` 由 `PaopaoApplication.onCreate` 调用，
     * 装了 24/25 的设备一打开应用就崩，而这正是唯一没法靠单元测试发现的那类问题。
     */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_HIGH,
        )
            .setName(context.getString(R.string.reminder_channel_name))
            .setDescription(context.getString(R.string.reminder_channel_description))
            .build()
        // 兼容层自己处理「已存在就跳过」与低版本空实现，所以这里不需要 SDK_INT 分支。
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun show(context: Context, event: EventEntity, occurrenceStart: LocalDate) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        // 总开关开了不代表权限也给了：33 起通知是运行时权限，未授权时 notify 会抛
        // SecurityException，而这里跑在闹钟线程上，抛出去就是一次没人看见的崩溃。
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val body = buildString {
            append(occurText(occurrenceStart))
            if (event.note.isNotBlank()) append('\n').append(event.note)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.title.ifBlank { "日程提醒" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    notificationId(event.id, occurrenceStart),
                    MainActivity.intentToDay(context, occurrenceStart.toEpochDay()),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        manager.notify(notificationId(event.id, occurrenceStart), notification)
    }

    fun cancel(context: Context, eventId: Long, occurrenceStart: LocalDate) {
        NotificationManagerCompat.from(context).cancel(notificationId(eventId, occurrenceStart))
    }

    /** 同一条日程的不同次提醒要能各自留下，所以通知 id 里带上发生日。 */
    private fun notificationId(eventId: Long, occurrenceStart: LocalDate): Int =
        (eventId * 100_000L + occurrenceStart.dayOfYear).toInt()

    private fun occurText(date: LocalDate): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
        return "${date.monthValue}月${date.dayOfMonth}日 $weekday"
    }
}

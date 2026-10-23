package com.paopao.calendar.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            },
        )
    }

    fun show(context: Context, event: EventEntity, occurrenceStart: LocalDate) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

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

package com.paopao.calendar.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.paopao.calendar.MainActivity
import com.paopao.calendar.appContainer
import com.paopao.calendar.data.local.entity.EventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 提醒的闹钟安排。
 *
 * 只在**未来 [HORIZON_DAYS] 天**内安排：重复日程要逐日展开，窗口开得太宽（比如一年）
 * 会让一次重排产生几百个闹钟，而系统对未触发闹钟有数量预算。应用每次冷启动、
 * 保存日程、开机后都会重排，所以窗口小不会漏掉提醒。
 *
 * 精确闹钟在 Android 12+ 是用户可撤销的授权。拿不到时退化成非精确闹钟，
 * 而不是静默放弃 —— 晚几分钟提醒远好于不提醒。
 */
class ReminderScheduler(private val context: Context) {

    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val events get() = context.appContainer.eventRepository

    /** 全量重排：开机、冷启动时用。 */
    suspend fun reschedule(today: LocalDate = LocalDate.now()) {
        val horizonEnd = today.plusDays(HORIZON_DAYS)
        val now = System.currentTimeMillis()
        occurrences(events.allInRange(today, horizonEnd), today, horizonEnd)
            .filter { it.event.remindMinutes != EventEntity.NO_REMINDER }
            .forEach { occurrence ->
                triggerMillis(occurrence.event, occurrence.day)
                    ?.takeIf { it > now }
                    ?.let { setAlarm(occurrence.event.id, occurrence.day, it) }
            }
    }

    /** 单条日程保存后调用：先撤掉这条日程在窗口内的全部闹钟，再按新规则排。 */
    suspend fun rescheduleEvent(event: EventEntity, today: LocalDate = LocalDate.now()) {
        cancelEvent(event.id, today)
        if (event.remindMinutes == EventEntity.NO_REMINDER) return
        val horizonEnd = today.plusDays(HORIZON_DAYS)
        val now = System.currentTimeMillis()
        occurrences(listOf(event), today, horizonEnd).forEach { occurrence ->
            triggerMillis(occurrence.event, occurrence.day)
                ?.takeIf { it > now }
                ?.let { setAlarm(occurrence.event.id, occurrence.day, it) }
        }
    }

    fun cancelEvent(eventId: Long, from: LocalDate = LocalDate.now()) {
        (0L..HORIZON_DAYS).forEach { offset -> alarms.cancel(request(eventId, from.plusDays(offset))) }
    }

    /**
     * 窗口内的每一次发生。
     *
     * 按日展开而不是信任母记录的跨度：重复日程在库里只存锚点那一天，
     * 直接读跨度会漏掉之后所有的周一。
     */
    private fun occurrences(
        pool: List<EventEntity>,
        from: LocalDate,
        to: LocalDate,
    ): List<Occurrence> {
        val seen = LinkedHashSet<Pair<Long, Long>>()
        val found = ArrayList<Occurrence>()
        var cursor = from
        while (cursor <= to) {
            events.occurrencesOn(pool, cursor).forEach { occurrence ->
                val startDay = LocalDate.ofEpochDay(occurrence.startEpochDay)
                if (startDay >= from && seen.add(occurrence.id to startDay.toEpochDay())) {
                    found += Occurrence(occurrence, startDay)
                }
            }
            cursor = cursor.plusDays(1)
        }
        return found
    }

    /** 全天日程没有时刻，提醒落在当天上午 [ALL_DAY_REMIND_HOUR] 点。 */
    private fun triggerMillis(event: EventEntity, occurrenceStart: LocalDate): Long? {
        if (event.remindMinutes == EventEntity.NO_REMINDER) return null
        val wallTime = event.startMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
        } ?: LocalTime.of(ALL_DAY_REMIND_HOUR, 0)
        return occurrenceStart.atTime(wallTime)
            .minusMinutes(event.remindMinutes.toLong())
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun setAlarm(eventId: Long, day: LocalDate, triggerMillis: Long) {
        val request = request(eventId, day)
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, request)
        } else {
            alarms.set(AlarmManager.RTC_WAKEUP, triggerMillis, request)
        }
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /**
     * data 里带 eventId 与发生日：PendingIntent 的匹配看 data，同一条日程的不同次提醒
     * 因此各自独立，重排时也能精确撤掉旧的那一个。extras 是广播侧要读的内容。
     */
    private fun request(eventId: Long, day: LocalDate): PendingIntent {
        val epochDay = day.toEpochDay()
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("paopao://reminder/$eventId/$epochDay"))
            .putExtra(EXTRA_EVENT_ID, eventId)
            .putExtra(MainActivity.EXTRA_EPOCH_DAY, epochDay)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private data class Occurrence(val event: EventEntity, val day: LocalDate)

    private companion object {
        const val REQUEST_CODE = 0
        const val HORIZON_DAYS = 14L
        const val ALL_DAY_REMIND_HOUR = 9
    }
}

/** 闹钟触发时带回日程 id；发生日复用 [MainActivity.EXTRA_EPOCH_DAY]，避免两处常量各写一份。 */
const val EXTRA_EVENT_ID = "com.paopao.calendar.extra.EVENT_ID"

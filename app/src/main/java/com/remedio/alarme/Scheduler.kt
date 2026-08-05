package com.remedio.alarme

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {

    const val EXTRA_ID = "reminder_id"
    private const val SHOW_OFFSET = 100000

    private fun alarmManager(ctx: Context): AlarmManager =
        ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun firePendingIntent(ctx: Context, id: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).apply {
            action = "com.remedio.alarme.FIRE_$id"
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            ctx, id, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Next moment this reminder should fire, or null if it has no active days. */
    fun nextTrigger(r: Reminder, from: Long = System.currentTimeMillis()): Long? {
        if (r.days.isEmpty() || !r.enabled) return null
        for (i in 0..7) {
            val c = Calendar.getInstance()
            c.timeInMillis = from
            c.add(Calendar.DAY_OF_YEAR, i)
            c.set(Calendar.HOUR_OF_DAY, r.hour)
            c.set(Calendar.MINUTE, r.minute)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis > from + 1000L && r.days.contains(c.get(Calendar.DAY_OF_WEEK))) {
                return c.timeInMillis
            }
        }
        return null
    }

    fun rescheduleAll(ctx: Context) {
        val am = alarmManager(ctx)
        val list = Store.load(ctx)
        list.forEach { r ->
            val pi = firePendingIntent(ctx, r.id)
            am.cancel(pi)
            val t = nextTrigger(r) ?: return@forEach
            val showIntent = PendingIntent.getActivity(
                ctx, SHOW_OFFSET + r.id,
                Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t, showIntent), pi)
            } catch (e: SecurityException) {
                // Exact alarm permission not granted -> best effort fallback.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, t, pi)
                }
            }
        }
    }

    fun cancel(ctx: Context, id: Int) {
        alarmManager(ctx).cancel(firePendingIntent(ctx, id))
    }

    /** One-off snooze alarm for an existing reminder. */
    fun snooze(ctx: Context, id: Int, minutes: Int) {
        val am = alarmManager(ctx)
        val at = System.currentTimeMillis() + minutes * 60_000L
        val i = Intent(ctx, AlarmReceiver::class.java).apply {
            action = "com.remedio.alarme.SNOOZE_$id"
            putExtra(EXTRA_ID, id)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, SHOW_OFFSET * 2 + id, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(
                    at,
                    PendingIntent.getActivity(
                        ctx, SHOW_OFFSET * 3 + id,
                        Intent(ctx, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                ), pi
            )
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

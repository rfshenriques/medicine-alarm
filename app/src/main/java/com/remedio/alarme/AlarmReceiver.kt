package com.remedio.alarme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, -1)
        val reminder = Store.find(ctx, id)
        val message = reminder?.message ?: ctx.getString(R.string.default_message)

        // 1. Full-screen-intent notification: the reliable path when the screen
        //    is off or locked, and the required fallback on modern Android.
        Notifier.showAlarm(ctx, id, message)

        // 2. Direct activity launch: works when the app has "display over other
        //    apps" permission, and covers the screen even while he is using the
        //    phone for something else.
        try {
            ctx.startActivity(
                AlarmActivity.intentFor(ctx, id, message).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        } catch (e: Exception) {
            // Background start blocked; the notification above still fires.
        }

        // 3. Queue up the next occurrence.
        Scheduler.rescheduleAll(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.rescheduleAll(context.applicationContext)
    }
}

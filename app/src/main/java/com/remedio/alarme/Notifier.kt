package com.remedio.alarme

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

object Notifier {

    const val CHANNEL_ID = "alarme_remedio_channel"
    private const val NOTIF_BASE = 7000

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = ctx.getString(R.string.channel_desc)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setSound(null, null) // sound is played by AlarmActivity
        }
        nm.createNotificationChannel(ch)
    }

    fun showAlarm(ctx: Context, id: Int, message: String) {
        ensureChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)

        val full = PendingIntent.getActivity(
            ctx, id,
            AlarmActivity.intentFor(ctx, id, message),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(ctx, CHANNEL_ID) else
            @Suppress("DEPRECATION") Notification.Builder(ctx)

        val n = builder
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .build()

        nm.notify(NOTIF_BASE + id, n)
    }

    fun clear(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_BASE + id)
    }
}

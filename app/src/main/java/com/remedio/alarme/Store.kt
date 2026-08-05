package com.remedio.alarme

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Reminder(
    val id: Int,
    var hour: Int,
    var minute: Int,
    var message: String,
    var days: MutableSet<Int>,   // Calendar.SUNDAY(1) .. Calendar.SATURDAY(7)
    var enabled: Boolean
) {
    fun timeLabel(): String = String.format("%02d:%02d", hour, minute)
}

object Store {

    private const val PREFS = "alarme_remedio"
    private const val KEY_LIST = "reminders"
    private const val KEY_LAST_ID = "last_id"
    private const val KEY_DELAY = "reveal_delay"
    private const val KEY_HOLD = "require_hold"
    private const val KEY_SNOOZE = "snooze_enabled"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- reminders ----------

    fun load(ctx: Context): MutableList<Reminder> {
        val raw = prefs(ctx).getString(KEY_LIST, null) ?: return mutableListOf()
        val out = mutableListOf<Reminder>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val daysArr = o.getJSONArray("days")
                val days = mutableSetOf<Int>()
                for (j in 0 until daysArr.length()) days.add(daysArr.getInt(j))
                out.add(
                    Reminder(
                        id = o.getInt("id"),
                        hour = o.getInt("hour"),
                        minute = o.getInt("minute"),
                        message = o.getString("message"),
                        days = days,
                        enabled = o.optBoolean("enabled", true)
                    )
                )
            }
        } catch (e: Exception) {
            return mutableListOf()
        }
        out.sortWith(compareBy({ it.hour }, { it.minute }))
        return out
    }

    fun save(ctx: Context, list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("hour", r.hour)
            o.put("minute", r.minute)
            o.put("message", r.message)
            o.put("enabled", r.enabled)
            val d = JSONArray()
            r.days.sorted().forEach { d.put(it) }
            o.put("days", d)
            arr.put(o)
        }
        prefs(ctx).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun find(ctx: Context, id: Int): Reminder? = load(ctx).firstOrNull { it.id == id }

    fun upsert(ctx: Context, r: Reminder) {
        val list = load(ctx)
        val idx = list.indexOfFirst { it.id == r.id }
        if (idx >= 0) list[idx] = r else list.add(r)
        save(ctx, list)
    }

    fun delete(ctx: Context, id: Int) {
        save(ctx, load(ctx).filter { it.id != id })
    }

    fun nextId(ctx: Context): Int {
        val p = prefs(ctx)
        val next = p.getInt(KEY_LAST_ID, 0) + 1
        p.edit().putInt(KEY_LAST_ID, next).apply()
        return next
    }

    fun defaultReminder(ctx: Context): Reminder = Reminder(
        id = nextId(ctx),
        hour = 9,
        minute = 0,
        message = ctx.getString(R.string.default_message),
        days = mutableSetOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        ),
        enabled = true
    )

    // ---------- settings ----------

    /** Seconds before the dismiss button becomes visible/tappable. */
    fun getRevealDelay(ctx: Context): Int = prefs(ctx).getInt(KEY_DELAY, 3)
    fun setRevealDelay(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DELAY, v).apply()

    fun getRequireHold(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HOLD, false)
    fun setRequireHold(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_HOLD, v).apply()

    fun getSnoozeEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SNOOZE, false)
    fun setSnoozeEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_SNOOZE, v).apply()
}

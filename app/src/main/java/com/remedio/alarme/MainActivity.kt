package com.remedio.alarme

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout
    private lateinit var warnBox: LinearLayout
    private lateinit var warnText: TextView

    private val weekDays = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listContainer = findViewById(R.id.list_container)
        warnBox = findViewById(R.id.warn_box)
        warnText = findViewById(R.id.warn_text)

        findViewById<Button>(R.id.btn_add).setOnClickListener {
            editReminder(null)
        }
        findViewById<Button>(R.id.btn_test).setOnClickListener {
            val first = Store.load(this).firstOrNull()
            startActivity(
                AlarmActivity.intentFor(
                    this,
                    first?.id ?: -1,
                    first?.message ?: getString(R.string.default_message),
                    preview = true
                )
            )
        }
        findViewById<Button>(R.id.btn_settings).setOnClickListener { openSettings() }
        findViewById<Button>(R.id.btn_fix).setOnClickListener { requestNextPermission() }

        Notifier.ensureChannel(this)
        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        Scheduler.rescheduleAll(this)
        renderList()
        renderWarnings()
    }

    // ---------------- list ----------------

    private fun renderList() {
        listContainer.removeAllViews()
        val list = Store.load(this)

        if (list.isEmpty()) {
            val tv = TextView(this)
            tv.text = getString(R.string.empty_list)
            tv.setPadding(0, 32, 0, 32)
            listContainer.addView(tv)
            return
        }

        val inflater = LayoutInflater.from(this)
        list.forEach { r ->
            val row = inflater.inflate(R.layout.item_reminder, listContainer, false)
            row.findViewById<TextView>(R.id.item_time).text = r.timeLabel()
            row.findViewById<TextView>(R.id.item_message).text = r.message
            row.findViewById<TextView>(R.id.item_days).text = daysSummary(r)

            val toggle = row.findViewById<Button>(R.id.item_toggle)
            toggle.text = getString(if (r.enabled) R.string.on else R.string.off)
            toggle.setOnClickListener {
                r.enabled = !r.enabled
                Store.upsert(this, r)
                Scheduler.rescheduleAll(this)
                renderList()
            }

            row.findViewById<Button>(R.id.item_edit).setOnClickListener { editReminder(r) }
            row.findViewById<Button>(R.id.item_delete).setOnClickListener {
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.confirm_delete, r.timeLabel()))
                    .setPositiveButton(R.string.delete) { _, _ ->
                        Scheduler.cancel(this, r.id)
                        Store.delete(this, r.id)
                        renderList()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            listContainer.addView(row)
        }
    }

    private fun daysSummary(r: Reminder): String {
        if (r.days.size == 7) return getString(R.string.every_day)
        val names = resources.getStringArray(R.array.day_short)
        return weekDays.filter { r.days.contains(it) }
            .joinToString(" ") { names[weekDays.indexOf(it)] }
            .ifEmpty { getString(R.string.no_days) }
    }

    // ---------------- editor ----------------

    private fun editReminder(existing: Reminder?) {
        val r = existing ?: Store.defaultReminder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit, null)

        val picker = view.findViewById<TimePicker>(R.id.picker)
        picker.setIs24HourView(true)
        picker.hour = r.hour
        picker.minute = r.minute

        val msg = view.findViewById<EditText>(R.id.edit_message)
        msg.setText(r.message)

        val boxes = listOf<CheckBox>(
            view.findViewById(R.id.d1), view.findViewById(R.id.d2),
            view.findViewById(R.id.d3), view.findViewById(R.id.d4),
            view.findViewById(R.id.d5), view.findViewById(R.id.d6),
            view.findViewById(R.id.d7)
        )
        boxes.forEachIndexed { i, cb -> cb.isChecked = r.days.contains(weekDays[i]) }

        view.findViewById<Button>(R.id.btn_all_days).setOnClickListener {
            boxes.forEach { it.isChecked = true }
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.new_reminder else R.string.edit_reminder)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                r.hour = picker.hour
                r.minute = picker.minute
                r.message = msg.text.toString().ifBlank { getString(R.string.default_message) }
                r.days = mutableSetOf<Int>().apply {
                    boxes.forEachIndexed { i, cb -> if (cb.isChecked) add(weekDays[i]) }
                }
                r.enabled = true
                Store.upsert(this, r)
                Scheduler.rescheduleAll(this)
                renderList()
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- settings ----------------

    private fun openSettings() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val delays = listOf(0, 3, 5, 10)
        val delayBoxes = listOf<CheckBox>(
            view.findViewById(R.id.delay_0), view.findViewById(R.id.delay_3),
            view.findViewById(R.id.delay_5), view.findViewById(R.id.delay_10)
        )
        val current = Store.getRevealDelay(this)
        delayBoxes.forEachIndexed { i, cb ->
            cb.isChecked = delays[i] == current
            cb.setOnClickListener { delayBoxes.forEach { b -> b.isChecked = b === cb } }
        }

        val hold = view.findViewById<CheckBox>(R.id.chk_hold)
        hold.isChecked = Store.getRequireHold(this)
        val snooze = view.findViewById<CheckBox>(R.id.chk_snooze)
        snooze.isChecked = Store.getSnoozeEnabled(this)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val idx = delayBoxes.indexOfFirst { it.isChecked }
                Store.setRevealDelay(this, if (idx >= 0) delays[idx] else 3)
                Store.setRequireHold(this, hold.isChecked)
                Store.setSnoozeEnabled(this, snooze.isChecked)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- permissions ----------------

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun canOverlay(): Boolean = Settings.canDrawOverlays(this)

    private fun batteryUnrestricted(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun missing(): List<String> {
        val out = mutableListOf<String>()
        if (!canScheduleExact()) out.add(getString(R.string.perm_exact))
        if (!canOverlay()) out.add(getString(R.string.perm_overlay))
        if (!batteryUnrestricted()) out.add(getString(R.string.perm_battery))
        return out
    }

    private fun renderWarnings() {
        val m = missing()
        if (m.isEmpty()) {
            warnBox.visibility = View.GONE
        } else {
            warnBox.visibility = View.VISIBLE
            warnText.text = getString(R.string.perm_intro) + "\n\n• " + m.joinToString("\n• ")
        }
    }

    /** Opens the settings screen for the first still-missing permission. */
    private fun requestNextPermission() {
        try {
            if (!canScheduleExact() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
                return
            }
            if (!canOverlay()) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                return
            }
            if (!batteryUnrestricted()) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
                return
            }
        } catch (e: Exception) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}

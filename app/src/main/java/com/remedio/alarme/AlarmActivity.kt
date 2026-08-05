package com.remedio.alarme

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

class AlarmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_PREVIEW = "preview"

        fun intentFor(ctx: Context, id: Int, message: String, preview: Boolean = false): Intent =
            Intent(ctx, AlarmActivity::class.java).apply {
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_PREVIEW, preview)
            }
    }

    private var reminderId = -1
    private var isPreview = false

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())
    private var holdRunnable: Runnable? = null
    private var holdSecondsLeft = 0

    private var lastX = -1f
    private var lastY = -1f

    private lateinit var area: FrameLayout
    private lateinit var dismiss: Button
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContentView(R.layout.activity_alarm)

        reminderId = intent.getIntExtra(EXTRA_ID, -1)
        isPreview = intent.getBooleanExtra(EXTRA_PREVIEW, false)

        area = findViewById(R.id.button_area)
        dismiss = findViewById(R.id.btn_dismiss)
        hint = findViewById(R.id.txt_hint)

        findViewById<TextView>(R.id.txt_message).text =
            intent.getStringExtra(EXTRA_MESSAGE) ?: getString(R.string.default_message)
        findViewById<TextView>(R.id.txt_clock).text =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val snoozeBtn = findViewById<Button>(R.id.btn_snooze)
        snoozeBtn.visibility =
            if (Store.getSnoozeEnabled(this) && !isPreview) View.VISIBLE else View.GONE
        snoozeBtn.setOnClickListener {
            Scheduler.snooze(this, reminderId, 10)
            finishAlarm()
        }

        // Tapping anywhere in the empty area moves the button again, so a
        // mis-tap never trains a location.
        area.setOnClickListener { placeButtonRandomly() }

        setupDismissButton()
        startAlerting()

        // Hardware back must not dismiss the alarm.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* intentionally blocked */ }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reminderId = intent.getIntExtra(EXTRA_ID, reminderId)
        findViewById<TextView>(R.id.txt_message).text =
            intent.getStringExtra(EXTRA_MESSAGE) ?: getString(R.string.default_message)
        placeButtonRandomly()
    }

    // ---------------- screen / lock screen ----------------

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "alarme:screen"
        ).also { it.acquire(5 * 60 * 1000L) }
    }

    // ---------------- the dismiss button ----------------

    private fun setupDismissButton() {
        val delay = Store.getRevealDelay(this)
        val requireHold = Store.getRequireHold(this)

        dismiss.text = getString(R.string.btn_taken)

        if (requireHold) {
            dismiss.setOnTouchListener { _, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> { startHold(); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { cancelHold(); true }
                    else -> false
                }
            }
        } else {
            dismiss.setOnClickListener { finishAlarm() }
        }

        if (delay > 0) {
            dismiss.visibility = View.INVISIBLE
            countdown(delay)
        } else {
            hint.text = getString(R.string.hint_default)
        }

        area.post { placeButtonRandomly() }
    }

    private fun countdown(seconds: Int) {
        var left = seconds
        val r = object : Runnable {
            override fun run() {
                if (left > 0) {
                    hint.text = getString(R.string.hint_wait, left)
                    left--
                    handler.postDelayed(this, 1000)
                } else {
                    hint.text = getString(R.string.hint_default)
                    dismiss.visibility = View.VISIBLE
                    placeButtonRandomly()
                }
            }
        }
        handler.post(r)
    }

    private fun startHold() {
        holdSecondsLeft = 3
        dismiss.text = getString(R.string.btn_hold_counting, holdSecondsLeft)
        holdRunnable = object : Runnable {
            override fun run() {
                holdSecondsLeft--
                if (holdSecondsLeft <= 0) {
                    finishAlarm()
                } else {
                    dismiss.text = getString(R.string.btn_hold_counting, holdSecondsLeft)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.postDelayed(holdRunnable!!, 1000)
    }

    private fun cancelHold() {
        holdRunnable?.let { handler.removeCallbacks(it) }
        holdRunnable = null
        dismiss.text = getString(R.string.btn_taken)
    }

    /**
     * Puts the button somewhere new inside the free area. The new spot must sit
     * a decent distance from the previous one so muscle memory never lands on it.
     */
    private fun placeButtonRandomly() {
        if (area.width == 0 || dismiss.width == 0) {
            area.post { placeButtonRandomly() }
            return
        }
        val maxX = (area.width - dismiss.width).coerceAtLeast(0)
        val maxY = (area.height - dismiss.height).coerceAtLeast(0)
        if (maxX == 0 && maxY == 0) return

        val minJumpX = maxX * 0.35f
        val minJumpY = maxY * 0.35f

        var x = 0f
        var y = 0f
        var tries = 0
        do {
            x = Random.nextInt(maxX + 1).toFloat()
            y = Random.nextInt(maxY + 1).toFloat()
            tries++
        } while (
            tries < 25 && lastX >= 0f &&
            abs(x - lastX) < minJumpX && abs(y - lastY) < minJumpY
        )

        lastX = x
        lastY = y
        dismiss.x = x
        dismiss.y = y
    }

    // ---------------- sound & vibration ----------------

    private fun startAlerting() {
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        try {
            player = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            player = null
        }

        // Make sure the alarm stream is actually audible.
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) < max / 2) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.8).toInt(), 0)
            }
        } catch (e: Exception) { /* some OEMs block this */ }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 700, 500)
        try {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) { /* no vibrator */ }
    }

    private fun stopAlerting() {
        try { player?.stop() } catch (e: Exception) {}
        player?.release()
        player = null
        try { vibrator?.cancel() } catch (e: Exception) {}
    }

    private fun finishAlarm() {
        stopAlerting()
        if (!isPreview) Notifier.clear(this, reminderId)
        handler.removeCallbacksAndMessages(null)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlerting()
        handler.removeCallbacksAndMessages(null)
        wakeLock?.let { if (it.isHeld) it.release() }
    }
}

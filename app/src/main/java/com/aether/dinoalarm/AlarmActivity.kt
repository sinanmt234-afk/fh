package com.aether.dinoalarm

import android.app.Activity
import android.content.*
import android.graphics.Color
import android.os.*
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class AlarmActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var info: TextView
    private var score = 0
    private var registered = false
    private val rcv = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { finish() } }
    private val tick = object : Runnable {
        override fun run() {
            val left = ((AlarmService.endAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            info.text = "Reach 100 points to stop the alarm\nScore $score / 100   ·   auto-off in ${left}s"
            h.postDelayed(this, 250)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (!AlarmService.ringing) { finish(); return }
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val f = IntentFilter(AlarmService.STOPPED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rcv, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(rcv, f)
        registered = true

        val root = FrameLayout(this)
        root.addView(DinoView(this, { score = it }, {
            startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
            finish()
        }))
        info = TextView(this).apply {
            setTextColor(Color.parseColor("#535353")); textSize = 16f; gravity = Gravity.CENTER
            setPadding(24, 90, 24, 0)
        }
        root.addView(info, FrameLayout.LayoutParams(-1, -2))
        setContentView(root)
        h.post(tick)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* blocked: only the game can stop the alarm */ }

    override fun onUserLeaveHint() {
        if (AlarmService.ringing) h.postDelayed({
            try { startActivity(Intent(this, AlarmActivity::class.java)) } catch (_: Exception) {}
        }, 400)
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        if (registered) unregisterReceiver(rcv)
        super.onDestroy()
    }
}

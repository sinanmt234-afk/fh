package com.aether.dinoalarm

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private var pickId = -1
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#111111"))
            setPadding(dp(16), dp(48), dp(16), dp(16))
        }
        root.addView(TextView(this).apply { text = "Dino Alarm"; textSize = 30f; setTextColor(Color.WHITE) })
        root.addView(TextView(this).apply {
            text = "Alarms stop only after 100 points in the Dino game (or after 1 minute). No snooze."
            textSize = 13f; setTextColor(Color.GRAY); setPadding(0, dp(4), 0, dp(12))
        })
        root.addView(Button(this).apply {
            text = "+ Add alarm"
            setOnClickListener {
                TimePickerDialog(this@MainActivity, { _, h, m ->
                    val a = Alarm(Store.nextId(context), h, m, true, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString())
                    Store.save(context, Store.all(context) + a)
                    Scheduler.set(context, a); render()
                }, 7, 0, false).show()
            }
        })
        val crash = getSharedPreferences("crash", 0).getString("log", null)
        if (crash != null) root.addView(TextView(this).apply {
            text = "Last error (tap to clear, send me a screenshot):\n" + crash.take(700)
            textSize = 10f; setTextColor(Color.parseColor("#F28B82")); setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { getSharedPreferences("crash", 0).edit().clear().apply(); visibility = View.GONE }
        })
        root.addView(Button(this).apply {
            text = "Test alarm in 10 sec (then lock phone)"
            setOnClickListener {
                Scheduler.at(context, 0, System.currentTimeMillis() + 10_000)
                Toast.makeText(context, "Rings in 10 seconds", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "1. Remove battery limit"
            setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) } catch (e: Exception) {}
            }
        })
        root.addView(Button(this).apply {
            text = "2. Allow full-screen alarm"
            setOnClickListener {
                try { if (Build.VERSION.SDK_INT >= 34) startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) } catch (e: Exception) {}
            }
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) })
        setContentView(root)
        render()
    }

    private fun soundName(u: String) = try {
        RingtoneManager.getRingtone(this, Uri.parse(u)).getTitle(this)
    } catch (_: Exception) { "Default alarm" }

    private fun render() {
        list.removeAllViews()
        for (a in Store.all(this).sortedBy { it.hour * 60 + it.minute }) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
                background = GradientDrawable().apply { setColor(Color.parseColor("#1E1E1E")); cornerRadius = dp(16).toFloat() }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            }
            val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val h12 = if (a.hour % 12 == 0) 12 else a.hour % 12
            top.addView(TextView(this).apply {
                text = "%d:%02d %s".format(h12, a.minute, if (a.hour < 12) "AM" else "PM")
                textSize = 34f; setTextColor(if (a.enabled) Color.WHITE else Color.GRAY)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(Switch(this).apply {
                isChecked = a.enabled
                setOnCheckedChangeListener { _, on ->
                    a.enabled = on; Store.save(context, Store.all(context).map { if (it.id == a.id) a else it })
                    if (on) Scheduler.set(context, a) else Scheduler.cancel(context, a.id)
                    render()
                }
            })
            card.addView(top)
            card.addView(TextView(this).apply {
                text = "♪ " + soundName(a.uri); textSize = 15f; setTextColor(Color.parseColor("#8AB4F8"))
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener {
                    pickId = a.id
                    startActivityForResult(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(a.uri)), 2)
                }
            })
            card.addView(TextView(this).apply {
                text = "Delete"; textSize = 14f; setTextColor(Color.parseColor("#F28B82"))
                setOnClickListener {
                    Scheduler.cancel(context, a.id)
                    Store.save(context, Store.all(context).filter { it.id != a.id }); render()
                }
            })
            list.addView(card)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 2 && res == Activity.RESULT_OK) {
            val u = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) ?: return
            Store.save(this, Store.all(this).map { if (it.id == pickId) it.apply { uri = u.toString() } else it })
            render()
        }
    }
}

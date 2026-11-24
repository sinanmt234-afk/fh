package com.aether.dinoalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Alarm(val id: Int, var hour: Int, var minute: Int, var enabled: Boolean, var uri: String)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("alarms", 0)
    fun all(c: Context): MutableList<Alarm> {
        val arr = JSONArray(sp(c).getString("list", "[]"))
        val l = mutableListOf<Alarm>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            l.add(Alarm(o.getInt("id"), o.getInt("h"), o.getInt("m"), o.getBoolean("e"), o.getString("u")))
        }
        return l
    }
    fun save(c: Context, l: List<Alarm>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute).put("e", it.enabled).put("u", it.uri)) }
        sp(c).edit().putString("list", a.toString()).apply()
    }
    fun get(c: Context, id: Int) = all(c).firstOrNull { it.id == id }
    fun nextId(c: Context) = (all(c).maxOfOrNull { it.id } ?: 0) + 1
}

object Scheduler {
    private fun pi(c: Context, id: Int) = PendingIntent.getBroadcast(
        c, id, Intent(c, AlarmReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun set(c: Context, a: Alarm): Long {
        val t = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, a.hour); set(Calendar.MINUTE, a.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        at(c, a.id, t.timeInMillis)
        return t.timeInMillis
    }
    fun at(c: Context, id: Int, time: Long) {
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        c.getSystemService(AlarmManager::class.java)
            .setAlarmClock(AlarmManager.AlarmClockInfo(time, show), pi(c, id))
    }
    fun cancel(c: Context, id: Int) = c.getSystemService(AlarmManager::class.java).cancel(pi(c, id))
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        val a = Store.get(c, id)
        if (a != null) { if (!a.enabled) return; Scheduler.set(c, a) } // repeats daily
        try { c.startForegroundService(Intent(c, AlarmService::class.java).putExtra("id", id)) }
        catch (e: Throwable) { Crash.record(c, "receiver", e) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Store.all(c).filter { it.enabled }.forEach { Scheduler.set(c, it) }
    }
}

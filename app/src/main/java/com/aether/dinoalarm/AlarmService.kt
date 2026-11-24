package com.aether.dinoalarm

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.net.Uri
import android.os.*

class AlarmService : Service() {
    companion object {
        @Volatile var ringing = false
        @Volatile var endAt = 0L
        const val STOPPED = "com.aether.dinoalarm.STOPPED"
        const val ACTION_STOP = "stop"
    }
    private var mp: MediaPlayer? = null
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int =
        try { run(i) } catch (e: Throwable) { Crash.record(this, "service", e); stopSelf(); START_NOT_STICKY }

    private var rt: Ringtone? = null

    private fun run(i: Intent?): Int {
        if (i?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (ringing) return START_NOT_STICKY
        ringing = true
        endAt = System.currentTimeMillis() + 60_000

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("alarm", "Alarms", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
        val open = Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, "alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm ringing")
            .setContentText("Score 100 in the Dino game to stop it")
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true)
            .setFullScreenIntent(pi, true).setContentIntent(pi).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        play(Store.get(this, i?.getIntExtra("id", -1) ?: -1)?.uri)
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 400), 0))
        try { startActivity(open) } catch (_: Exception) {}
        h.postDelayed({ stopSelf() }, 60_000) // auto-off after 1 minute, no snooze
        return START_NOT_STICKY
    }

    private fun play(uri: String?) {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        for (u in listOf(uri?.let { Uri.parse(it) } ?: fallback, fallback)) {
            try {
                mp = MediaPlayer().apply {
                    setAudioAttributes(attrs); setDataSource(this@AlarmService, u)
                    isLooping = true; prepare(); start()
                }
                return
            } catch (e: Exception) { Crash.record(this, "sound", e); mp?.release(); mp = null }
        }
        try {
            rt = RingtoneManager.getRingtone(this, fallback).apply { audioAttributes = attrs; isLooping = true; play() }
        } catch (e: Exception) { Crash.record(this, "ringtone", e) }
    }

    override fun onDestroy() {
        ringing = false
        h.removeCallbacksAndMessages(null)
        mp?.release(); mp = null; rt?.stop()
        getSystemService(Vibrator::class.java)?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        sendBroadcast(Intent(STOPPED).setPackage(packageName))
        super.onDestroy()
    }
}

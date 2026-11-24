package com.aether.dinoalarm

import android.app.Application
import android.content.Context

object Crash {
    fun record(c: Context, where: String, e: Throwable) {
        c.getSharedPreferences("crash", 0).edit()
            .putString("log", "$where: " + android.util.Log.getStackTraceString(e).take(3000)).commit()
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e -> Crash.record(this, "crash", e); old?.uncaughtException(t, e) }
    }
}

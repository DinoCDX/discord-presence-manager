package com.dpm.app

import android.content.Context
import android.content.Intent

object Procs {
    const val ACTION_KICK = "com.dpm.app.KICK"
    const val ACTION_SETTINGS = "com.dpm.app.SETTINGS_CHANGED"

    @Volatile
    var inService = false

    @Volatile
    var appContext: Context? = null

    fun kick() {
        val c = appContext ?: return
        c.sendBroadcast(Intent(ACTION_KICK).setPackage(c.packageName))
    }

    fun settingsChanged(c: Context) {
        c.sendBroadcast(Intent(ACTION_SETTINGS).setPackage(c.packageName))
    }
}

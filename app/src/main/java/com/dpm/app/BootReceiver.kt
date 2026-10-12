package com.dpm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (Store.get(c, "boot_start", "1") != "1") return
        if (Store.token(c) == null || !Presence.running(c)) return
        try {
            PresenceService.ensure(c)
        } catch (e: Exception) {
        }
    }
}

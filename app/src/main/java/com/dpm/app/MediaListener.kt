package com.dpm.app

import android.service.notification.NotificationListenerService

class MediaListener : NotificationListenerService() {
    companion object {
        @Volatile
        var instance: MediaListener? = null
    }

    override fun onListenerConnected() {
        instance = this
        try {
            if (!Presence.running(this)) {
                requestUnbind()
                return
            }
            if (Store.get(this, "boot_start", "1") == "1" && Store.token(this) != null && !PresenceService.alive) {
                PresenceService.ensure(this)
            }
        } catch (e: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        instance = null
    }
}

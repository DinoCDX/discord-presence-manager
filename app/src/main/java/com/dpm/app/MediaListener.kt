package com.dpm.app

import android.service.notification.NotificationListenerService

class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        try {
            if (Store.get(this, "boot_start", "1") == "1" && Store.token(this) != null &&
                Presence.running(this) && !PresenceService.alive
            ) {
                PresenceService.ensure(this)
            }
        } catch (e: Exception) {
        }
    }
}

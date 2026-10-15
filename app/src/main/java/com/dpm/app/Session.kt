package com.dpm.app

import android.app.Activity

fun Activity.startPresenceIfWanted() {
    if (Presence.running(this)) PresenceService.ensure(this)
}

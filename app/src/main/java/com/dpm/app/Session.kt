package com.dpm.app

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Activity.startPresenceIfWanted() {
    if (Presence.running(this)) PresenceService.ensure(this)
}

suspend fun Activity.completeLogin(token: String): Boolean {
    val r = withContext(Dispatchers.IO) { Api.me(token) }
    if (r.code != 200) {
        toast("Token rejected (${r.code})")
        return false
    }
    val o = r.obj()
    val name = if (!o.isNull("global_name")) o.getString("global_name") else o.optString("username")
    Store.addAccount(this, token, name, o.optString("id"))
    GatewayClient.connect(token)
    startPresenceIfWanted()
    go(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    finish()
    return true
}

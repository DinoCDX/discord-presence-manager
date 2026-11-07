package com.dpm.app

import kotlinx.coroutines.flow.MutableSharedFlow

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/** Messages go to the in-app snackbar while the main screen is listening, otherwise to a system toast. */
object Notes {
    val flow = MutableSharedFlow<String>(extraBufferCapacity = 8)
}

fun Context.toast(message: String) {
    if (Notes.flow.subscriptionCount.value > 0 && Notes.flow.tryEmit(message)) return
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Suppress("DEPRECATION")
fun Activity.go(i: Intent) {
    startActivity(i)
    overridePendingTransition(R.anim.enter, R.anim.exit)
}

fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}

fun Activity?.notificationsBlockedForGood(): Boolean =
    this != null && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
        this,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

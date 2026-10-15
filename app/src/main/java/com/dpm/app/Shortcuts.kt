package com.dpm.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews

object Toggle {
    const val ACTION = "com.dpm.app.TOGGLE"

    fun flip(c: Context) {
        if (Store.token(c) == null) {
            c.startActivity(Intent(c, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (Presence.running(c)) {
            Presence.setRunning(c, false)
            PresenceService.halt(c)
        } else {
            Presence.setRunning(c, true)
            Presence.setPaused(c, false)
            try {
                PresenceService.ensure(c)
            } catch (e: Exception) {
                c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Bus.bump()
        refresh(c)
    }

    fun refresh(c: Context) {
        try {
            TileService.requestListeningState(c, ComponentName(c, PresenceTile::class.java))
        } catch (e: Exception) {
        }
        try {
            val mgr = AppWidgetManager.getInstance(c)
            val ids = mgr.getAppWidgetIds(ComponentName(c, PresenceWidget::class.java))
            if (ids.isNotEmpty()) PresenceWidget.update(c, mgr, ids)
        } catch (e: Exception) {
        }
    }

    fun summary(c: Context): String = when {
        Store.token(c) == null -> "Log in first"
        !Presence.running(c) -> "Off"
        Presence.paused(c) -> "Paused"
        else -> {
            val i = Dynamic.found.values.firstNotNullOfOrNull { it }
            i?.let { "${Presence.verb(it.type)} ${it.name}" } ?: "On"
        }
    }
}

class PresenceTile : TileService() {
    override fun onStartListening() = paint()

    override fun onClick() {
        Toggle.flip(applicationContext)
        paint()
    }

    private fun paint() {
        val t = qsTile ?: return
        val on = Presence.running(this)
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Discord presence"
        t.subtitle = Toggle.summary(this)
        t.updateTile()
    }
}

class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action == Toggle.ACTION) Toggle.flip(c.applicationContext)
    }
}

class PresenceWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = update(c, m, ids)

    companion object {
        fun update(c: Context, m: AppWidgetManager, ids: IntArray) {
            val on = Presence.running(c)
            val v = RemoteViews(c.packageName, R.layout.widget_presence)
            v.setTextViewText(R.id.w_title, if (on) "Presence on" else "Presence off")
            v.setTextViewText(R.id.w_sub, Toggle.summary(c))
            v.setTextViewText(R.id.w_button, if (on) "Turn off" else "Turn on")
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val toggle = PendingIntent.getBroadcast(
                c, 10, Intent(c, ToggleReceiver::class.java).setAction(Toggle.ACTION), flags
            )
            val open = PendingIntent.getActivity(c, 11, Intent(c, MainActivity::class.java), flags)
            v.setOnClickPendingIntent(R.id.w_button, toggle)
            v.setOnClickPendingIntent(R.id.w_root, open)
            ids.forEach { m.updateAppWidget(it, v) }
        }
    }
}

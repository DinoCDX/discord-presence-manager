package com.dpm.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

object Toggle {
    const val ACTION = "com.dpm.app.TOGGLE"
    const val MODE_ACTION = "com.dpm.app.WIDGET_MODE"

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

    fun flipMode(c: Context) {
        Presence.setMode(c, if (Presence.mode(c) == "static") "dynamic" else "static")
        PresenceService.kick()
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
        val app = c.applicationContext
        when (intent.action) {
            Toggle.ACTION -> Toggle.flip(app)
            Toggle.MODE_ACTION -> Toggle.flipMode(app)
        }
    }
}

class PresenceWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = update(c, m, ids)

    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, newOptions: Bundle) =
        update(c, m, intArrayOf(id))

    companion object {
        private const val LARGE_MIN_HEIGHT_DP = 140

        fun update(c: Context, m: AppWidgetManager, ids: IntArray) {
            ids.forEach { id ->
                val height = m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
                val large = height >= LARGE_MIN_HEIGHT_DP
                val v = RemoteViews(c.packageName, if (large) R.layout.widget_presence_large else R.layout.widget_presence)
                bind(c, v, large)
                m.updateAppWidget(id, v)
            }
        }

        private fun bind(c: Context, v: RemoteViews, large: Boolean) {
            val on = Presence.running(c)
            val paused = Presence.paused(c)
            val static = Presence.mode(c) == "static"
            val dotLive = on && !paused
            val loggedOut = Store.token(c) == null
            val live = Dynamic.found.values.firstNotNullOfOrNull { it }
            val fallback = if (Store.get(c, "static_fallback", "0") == "1") Dynamic.staticInfo(c) else null
            val info = if (static) Dynamic.staticInfo(c) else (live ?: fallback)

            v.setTextViewText(
                R.id.w_title,
                when {
                    loggedOut -> "Log in to start"
                    !on -> "Presence off"
                    paused -> "Presence paused"
                    else -> "Presence on"
                }
            )
            v.setTextColor(R.id.w_dot, ContextCompat.getColor(c, if (dotLive) R.color.w_live else R.color.w_idle))
            v.setTextViewText(R.id.w_sub, subtitle(loggedOut, on, info, static))
            v.setTextViewText(R.id.w_mode, if (static) "Static" else "Dynamic")
            v.setTextViewText(R.id.w_button, if (on) "Turn off" else "Turn on")
            if (large) bindPreview(c, v, info, static)

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            v.setOnClickPendingIntent(
                R.id.w_button,
                PendingIntent.getBroadcast(c, 10, Intent(c, ToggleReceiver::class.java).setAction(Toggle.ACTION), flags)
            )
            v.setOnClickPendingIntent(
                R.id.w_mode,
                PendingIntent.getBroadcast(c, 12, Intent(c, ToggleReceiver::class.java).setAction(Toggle.MODE_ACTION), flags)
            )
            v.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(c, 11, Intent(c, MainActivity::class.java), flags)
            )
        }

        private fun subtitle(loggedOut: Boolean, on: Boolean, info: Info?, static: Boolean): String = when {
            loggedOut -> "Open DPM to log in"
            !on -> "Off"
            info == null -> if (static) "No static name set" else "Nothing playing right now"
            else -> "${Presence.verb(info.type)} ${info.name}"
        }

        private fun bindPreview(c: Context, v: RemoteViews, info: Info?, static: Boolean) {
            v.setTextViewText(
                R.id.w_pv_verb,
                info?.let { Presence.verb(it.type).uppercase() } ?: if (static) "STATIC" else "DYNAMIC"
            )
            v.setTextViewText(R.id.w_pv_name, info?.name ?: "Nothing to show yet")
            v.setTextViewText(R.id.w_pv_detail, listOfNotNull(info?.details, info?.state).joinToString("\n"))
            val art = art(c, info)
            if (art != null) {
                v.setImageViewBitmap(R.id.w_art, art)
                v.setViewVisibility(R.id.w_art, View.VISIBLE)
            } else {
                v.setViewVisibility(R.id.w_art, View.GONE)
            }
        }

        private fun art(c: Context, info: Info?): Bitmap? {
            if (info == null) return null
            val cover = info.bitmap?.takeIf { info.artUri?.startsWith("https://") == true }
            val raw = cover
                ?: info.pkg?.let { AppIcons.get(c, it, 192) }
                ?: info.largeFile?.let { StaticFiles.file(c, it) }?.let { BitmapFactory.decodeFile(it.absolutePath) }
                ?: return null
            val side = 192
            val scaled = Bitmap.createScaledBitmap(raw, side, side, true)
            val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawRoundRect(RectF(0f, 0f, side.toFloat(), side.toFloat()), 40f, 40f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaled, 0f, 0f, paint)
            return out
        }
    }
}

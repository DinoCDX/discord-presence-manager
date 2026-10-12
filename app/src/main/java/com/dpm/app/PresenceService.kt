package com.dpm.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray

class PresenceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var last = ""
    private var notifKey = ""
    private var fgStarted = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            kick()
        }
    }

    companion object {
        const val CHANNEL = "presence"
        const val ACTION_STOP = "stop"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"

        private val kicks = Channel<Unit>(Channel.CONFLATED)

        @Volatile
        var alive = false
            private set

        fun ensure(c: Context) {
            c.startForegroundService(Intent(c, PresenceService::class.java))
        }

        fun halt(c: Context) {
            c.stopService(Intent(c, PresenceService::class.java))
        }

        fun kick() {
            kicks.trySend(Unit)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Presence.setRunning(this, false)
                Bus.bump()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                Presence.setPaused(this, true)
                Bus.bump()
            }
            ACTION_RESUME -> {
                Presence.setPaused(this, false)
                Bus.bump()
            }
        }
        val token = Store.token(this)
        if (token == null) {
            startFg(buildNotification(null, "Not logged in", null))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        alive = true
        startFg(buildNotification(null, "Starting", null))
        GatewayClient.connect(token)
        last = ""
        notifKey = ""
        if (job?.isActive != true) {
            job = scope.launch {
                while (isActive) {
                    try {
                        tick()
                    } catch (e: Exception) {
                    }
                    val interactive = getSystemService(PowerManager::class.java)?.isInteractive != false
                    withTimeoutOrNull(if (interactive) 5_000L else 15_000L) { kicks.receive() }
                }
            }
        }
        kicks.trySend(Unit)
        return START_STICKY
    }

    private fun tick() {
        if (Store.get(this, "only_online", "0") == "1") UserStatus.refresh(this)
        val gate = Logic.gate(this)
        val info = if (gate == null) Logic.info(this) else null
        val arr = JSONArray()
        if (info != null) arr.put(Dynamic.toJson(this, info))
        val s = arr.toString()
        if (s != last) {
            last = s
            GatewayClient.setPresence(arr)
        }
        val key = "$gate|${info?.type}|${info?.name}|${info?.details}|${info?.state}|${info?.pkg}|${info?.bitmap != null}"
        if (key != notifKey) {
            notifKey = key
            val n = buildNotification(info, gate, if (gate == null) info else null)
            getSystemService(NotificationManager::class.java).notify(1, n)
        }
    }

    private fun pending(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this,
            code,
            Intent(this, PresenceService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun buildNotification(info: Info?, gate: String?, shown: Info?): android.app.Notification {
        val paused = Presence.paused(this)
        val title: String
        val text: String
        when {
            shown != null -> {
                title = "${Presence.verb(shown.type)} ${shown.name}"
                text = listOfNotNull(shown.details, shown.state).joinToString(" \u00B7 ").ifEmpty { "Showing on Discord" }
            }
            paused -> {
                title = "Presence paused"
                text = "Nothing is shown on Discord"
            }
            gate != null -> {
                title = "Presence waiting"
                text = gate
            }
            else -> {
                title = "Presence active"
                text = "Nothing to show right now"
            }
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_presence)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(
                0,
                if (paused) "Resume" else "Pause",
                pending(if (paused) ACTION_RESUME else ACTION_PAUSE, 1)
            )
            .addAction(0, "Stop", pending(ACTION_STOP, 2))
        val big = shown?.let { s ->
            s.bitmap?.let { AppIcons.square(it, 256) } ?: s.pkg?.let { AppIcons.get(this, it, 256) }
        }
        if (big != null) b.setLargeIcon(big as Bitmap)
        return b.build()
    }

    private fun startFg(n: android.app.Notification) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Presence", NotificationManager.IMPORTANCE_LOW))
        if (fgStarted) {
            nm.notify(1, n)
            return
        }
        fgStarted = true
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        alive = false
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
        }
        job?.cancel()
        scope.cancel()
        GatewayClient.disconnect()
        super.onDestroy()
    }
}

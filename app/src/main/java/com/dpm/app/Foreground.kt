package com.dpm.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager

object Foreground {
    private const val SCREEN_NON_INTERACTIVE = 16

    private val resumed = HashMap<String, Long>()
    private var cursor = 0L

    @Synchronized
    fun pkg(c: Context): String? {
        val pm = c.getSystemService(PowerManager::class.java)
        if (!pm.isInteractive) return null
        val um = c.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val from = if (cursor == 0L) now - 6 * 3_600_000L else cursor - 1_000L
        val events = try { um.queryEvents(from, now) } catch (e: SecurityException) { return null }
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val id = e.packageName + "/" + (e.className ?: "")
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumed[id] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> resumed.remove(id)
                UsageEvents.Event.DEVICE_SHUTDOWN,
                SCREEN_NON_INTERACTIVE -> resumed.clear()
            }
        }
        cursor = now
        return resumed.maxByOrNull { it.value }?.key?.substringBefore('/')
    }
}

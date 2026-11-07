package com.dpm.app

import android.content.Context
import android.os.FileObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Live {
    class Snapshot(val info: Info?, val gate: String?, val found: Map<String, Info?>, val winner: String?) {
        fun signature(): String = listOf(
            gate, info?.key, info?.state, info?.paused, info?.start, info?.end, info?.details, winner,
            found.entries.joinToString(",") { "${it.key}=${it.value?.key}" }
        ).joinToString("|")

        companion object {
            val EMPTY = Snapshot(null, null, emptyMap(), null)
        }
    }

    private val conn = MutableStateFlow("Disconnected")
    val connection: StateFlow<String> = conn.asStateFlow()

    private val sent = MutableStateFlow(0L)
    val lastSent: StateFlow<Long> = sent.asStateFlow()

    private val ticks = MutableStateFlow(0)
    val tick: StateFlow<Int> = ticks.asStateFlow()

    @Volatile
    private var aliveAt = 0L

    private var observer: FileObserver? = null

    fun isAlive(): Boolean = System.currentTimeMillis() - aliveAt < 45_000L

    fun compute(c: Context): Snapshot {
        val gate = Logic.gate(c)
        val info = Logic.info(c)
        return Snapshot(info, gate, Dynamic.found, Dynamic.winner)
    }

    fun setConnection(s: String) {
        Procs.appContext?.let { Shared.put(it, "live_conn", s) }
    }

    fun markSent(at: Long) {
        Procs.appContext?.let { Shared.put(it, "live_sent", at.toString()) }
    }

    fun heartbeat() {
        Procs.appContext?.let { Shared.put(it, "live_alive", System.currentTimeMillis().toString()) }
    }

    fun stopped() {
        Procs.appContext?.let { Shared.putAll(it, mapOf("live_alive" to "0", "live_conn" to "Disconnected")) }
    }

    fun setVisible(c: Context, v: Boolean) {
        if (v) refresh(c)
    }

    fun watch(c: Context) {
        if (observer != null) return
        refresh(c)
        observer = object : FileObserver(c.filesDir, FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                when (path) {
                    "shared.json" -> refresh(c)
                    "logs.txt" -> Logs.touch()
                }
            }
        }.also { it.startWatching() }
    }

    fun refresh(c: Context) {
        conn.value = Shared.get(c, "live_conn", "Disconnected")
        sent.value = Shared.get(c, "live_sent", "0").toLongOrNull() ?: 0L
        aliveAt = Shared.get(c, "live_alive", "0").toLongOrNull() ?: 0L
        ticks.value = ticks.value + 1
    }

    fun sentLabel(at: Long, now: Long): String {
        if (at <= 0L) return "Nothing sent to Discord yet"
        val s = ((now - at) / 1000).coerceAtLeast(0L)
        return when {
            s < 60 -> "Last sent ${s}s ago"
            s < 3600 -> "Last sent ${s / 60} min ago"
            else -> "Last sent ${s / 3600} h ago"
        }
    }

    fun plain(state: String): String = when {
        state.startsWith("Connected") -> "Connected"
        state.startsWith("Connecting") -> "Connecting"
        state.startsWith("Token rejected") -> "Log in again"
        state.startsWith("Not logged in") -> "Log in to Discord"
        state.startsWith("Offline") -> "No connection, retrying"
        state.startsWith("Rate limited") -> "Discord is slowing updates"
        state.startsWith("Add a Discord app") -> "Add a Discord app in Settings"
        state == "Disconnected" -> "Not connected"
        else -> "Trouble reaching Discord, retrying"
    }
}

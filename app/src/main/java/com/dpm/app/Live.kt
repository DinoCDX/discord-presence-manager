package com.dpm.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Live {
    class Snapshot(val info: Info?, val gate: String?, val found: Map<String, Info?>, val winner: String?) {
        companion object {
            val EMPTY = Snapshot(null, null, emptyMap(), null)
        }
    }

    private val snapshot = MutableStateFlow(Snapshot.EMPTY)
    val flow: StateFlow<Snapshot> = snapshot.asStateFlow()

    private val conn = MutableStateFlow("Disconnected")
    val connection: StateFlow<String> = conn.asStateFlow()

    private val sent = MutableStateFlow(0L)
    val lastSent: StateFlow<Long> = sent.asStateFlow()

    private var lastKey = ""

    @Synchronized
    fun publish(s: Snapshot) {
        val p = s.info
        val key = listOf(
            s.gate, p?.key, p?.state, p?.paused, p?.start, p?.end, p?.details,
            s.winner, s.found.entries.joinToString(",") { "${it.key}=${it.value?.key}" }
        ).joinToString("|")
        if (key == lastKey) return
        lastKey = key
        snapshot.value = s
    }

    @Volatile
    var uiVisible = false
        private set

    fun setVisible(v: Boolean) {
        uiVisible = v
        if (v) PresenceService.kick()
    }

    fun setConnection(s: String) {
        conn.value = s
    }

    fun markSent(at: Long) {
        sent.value = at
    }

    fun compute(c: Context): Snapshot {
        val gate = Logic.gate(c)
        val info = Logic.info(c)
        return Snapshot(info, gate, Dynamic.found, Dynamic.winner)
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

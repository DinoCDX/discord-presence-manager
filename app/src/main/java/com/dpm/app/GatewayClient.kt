package com.dpm.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GatewayClient {
    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ws: WebSocket? = null
    private var token: String? = null
    private var seq: Any = JSONObject.NULL
    private var hb: Job? = null
    private var retry: Job? = null
    private var activities = JSONArray()
    private var status = "online"
    private var wanted = false

    @Volatile
    private var ready = false

    @Volatile
    private var acked = true

    private var attempts = 0

    @Volatile
    var state = "Disconnected"
        private set

    var onState: ((String) -> Unit)? = null

    @Synchronized
    fun connect(t: String) {
        if (wanted && token == t && ws != null) return
        token = t
        wanted = true
        attempts = 0
        seq = JSONObject.NULL
        open()
    }

    @Synchronized
    fun disconnect() {
        wanted = false
        hb?.cancel()
        retry?.cancel()
        val old = ws
        ws = null
        ready = false
        old?.close(1000, null)
        setState("Disconnected")
    }

    fun setPresence(a: JSONArray) {
        activities = a
        if (ready) ws?.send(JSONObject().put("op", 3).put("d", presence()).toString())
    }

    private fun presence(): JSONObject =
        JSONObject()
            .put("status", status)
            .put("since", 0)
            .put("activities", activities)
            .put("afk", false)

    private fun setState(s: String) {
        state = s
        onState?.invoke(s)
    }

    private fun open() {
        val old = ws
        ws = null
        old?.cancel()
        hb?.cancel()
        ready = false
        setState("Connecting")
        val req = Request.Builder()
            .url("wss://gateway.discord.gg/?v=9&encoding=json")
            .header("User-Agent", Api.UA)
            .header("Origin", "https://discord.com")
            .build()
        ws = http.newWebSocket(req, Handler())
    }

    private fun startHeartbeat(interval: Long) {
        hb?.cancel()
        acked = true
        hb = scope.launch {
            delay((interval * Math.random()).toLong())
            while (isActive) {
                if (!acked) {
                    val dead = ws
                    if (dead != null) {
                        dead.cancel()
                        lost(dead, 0)
                    }
                    return@launch
                }
                acked = false
                ws?.send("{\"op\":1,\"d\":$seq}")
                delay(interval)
            }
        }
    }

    private fun identify() {
        val props = JSONObject()
            .put("os", "Windows")
            .put("browser", "Chrome")
            .put("device", "")
            .put("system_locale", "en-US")
            .put("browser_user_agent", Api.UA)
            .put("browser_version", "124.0.0.0")
            .put("os_version", "10")
            .put("referrer", "")
            .put("referring_domain", "")
            .put("release_channel", "stable")
            .put("client_build_number", Api.BUILD)
        val d = JSONObject()
            .put("token", token)
            .put("capabilities", 16381)
            .put("properties", props)
            .put("presence", presence())
            .put("compress", false)
            .put("client_state", JSONObject().put("guild_versions", JSONObject()))
        ws?.send(JSONObject().put("op", 2).put("d", d).toString())
    }

    private fun lost(w: WebSocket, code: Int) {
        if (w !== ws) return
        hb?.cancel()
        ready = false
        if (code == 4004) {
            wanted = false
            setState("Token rejected by Discord")
            return
        }
        if (wanted) {
            setState("Reconnecting")
            retry?.cancel()
            val wait = minOf(60_000L, 5_000L shl minOf(attempts, 4))
            attempts++
            retry = scope.launch {
                delay(wait)
                if (wanted) open()
            }
        }
    }

    private class Handler : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== ws) return
            val o = try { JSONObject(text) } catch (e: Exception) { return }
            when (o.optInt("op", -1)) {
                10 -> {
                    startHeartbeat(o.getJSONObject("d").getLong("heartbeat_interval"))
                    identify()
                }
                1 -> webSocket.send("{\"op\":1,\"d\":$seq}")
                11 -> acked = true
                7 -> webSocket.close(4000, "reconnect")
                9 -> scope.launch {
                    delay(2000)
                    identify()
                }
                0 -> {
                    if (!o.isNull("s")) seq = o.get("s")
                    val t = o.optString("t")
                    val d = o.optJSONObject("d") ?: JSONObject()
                    if (t == "READY") {
                        ready = true
                        attempts = 0
                        val name = d.optJSONObject("user")?.optString("username") ?: "?"
                        setState("Connected as $name")
                    }
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            lost(webSocket, code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            lost(webSocket, 0)
        }
    }
}

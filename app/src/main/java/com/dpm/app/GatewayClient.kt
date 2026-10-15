package com.dpm.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object GatewayClient {
    private const val HEADLESS_URL = "https://discord.com/api/v10/users/@me/headless-sessions"
    private const val HEADLESS_DELETE_URL = "https://discord.com/api/v10/users/@me/headless-sessions/delete"
    private const val KEEP_ALIVE_MS = 14 * 60 * 1000L
    private const val DEBOUNCE_MS = 1_500L

    private const val PLATFORM = "android"

    private val http = Api.http
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var token: String? = null
    private var sessionToken: String? = null
    private var accountName: String = "?"
    private var hb: Job? = null
    private var pushJob: Job? = null
    private var retry: Job? = null

    private var activities = JSONArray()
    private var lastSentActivitiesJson: String? = null
    private var lastPushTimeMs = 0L

    private var wanted = false
    private var attempts = 0

    private var renewals = 0

    @Volatile
    var defaultAppId: String = ""

    @Volatile
    private var ready = false

    @Volatile
    var state = "Disconnected"
        private set

    var onState: ((String) -> Unit)? = null

    val tokenRejected: Boolean get() = state.startsWith("Token rejected")

    @Synchronized
    fun connect(t: String) {
        if (wanted && token == t && ready) return
        if (t != token) sessionToken = null
        token = t
        wanted = true
        attempts = 0
        renewals = 0
        lastSentActivitiesJson = null
        open()
    }

    @Synchronized
    fun disconnect() {
        wanted = false
        hb?.cancel()
        pushJob?.cancel()
        retry?.cancel()
        ready = false
        lastSentActivitiesJson = null
        val currentBearer = token
        val currentSession = sessionToken
        sessionToken = null
        setState("Disconnected")

        if (!currentBearer.isNullOrEmpty() && !currentSession.isNullOrEmpty()) {
            scope.launch {
                deleteRemoteSession(currentBearer, currentSession)
            }
        }
    }

    @Synchronized
    fun setPresence(a: JSONArray) {
        val incomingJson = a.toString()
        activities = a

        if (ready && incomingJson == lastSentActivitiesJson &&
            System.currentTimeMillis() - lastPushTimeMs < KEEP_ALIVE_MS
        ) {
            return
        }

        if (wanted && token != null) {
            pushJob?.cancel()
            pushJob = scope.launch {
                delay(DEBOUNCE_MS)
                pushPresence(force = false)
            }
        }
    }

    private fun setState(s: String) {
        state = s
        onState?.invoke(s)
    }

    private fun open() {
        hb?.cancel()
        retry?.cancel()
        ready = false
        setState("Connecting")

        scope.launch {
            val bearer = token ?: return@launch
            val req = Request.Builder()
                .url(DiscordOAuth.ME)
                .header("Authorization", "Bearer $bearer")
                .header("User-Agent", Api.UA)
                .build()

            var retryAfterMs = 0L
            var errText: String? = null
            val code = try {
                http.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val me = JSONObject(body)
                        accountName = if (!me.isNull("global_name")) {
                            me.getString("global_name")
                        } else {
                            me.optString("username", "?")
                        }
                    } else if (resp.code == 429) {
                        retryAfterMs = parseRetryAfterMs(body, resp.header("Retry-After"))
                    } else {
                        errText = discordError(resp.code, body)
                    }
                    resp.code
                }
            } catch (_: Exception) {
                0
            }

            Logs.add("GET users/@me -> HTTP $code")
            if (!wanted || token != bearer) return@launch

            when (code) {
                in 200..299 -> {
                    ready = true
                    attempts = 0
                    renewals = 0
                    setState("Connected as $accountName")
                    startKeepAlive()
                }
                401 -> renewAndReconnect(bearer)
                429 -> scheduleRetry(429, retryAfterMs, retryPushOnly = false)
                else -> scheduleRetry(code, 0L, retryPushOnly = false, message = errText)
            }
        }
    }

    private fun startKeepAlive() {
        hb?.cancel()
        hb = scope.launch {
            while (isActive && wanted) {
                pushPresence(force = true)
                delay(KEEP_ALIVE_MS)
            }
        }
    }

    private suspend fun pushPresence(force: Boolean) {
        val bearer = token ?: return
        val currentJson = activities.toString()

        if (!force && currentJson == lastSentActivitiesJson &&
            System.currentTimeMillis() - lastPushTimeMs < KEEP_ALIVE_MS
        ) {
            return
        }

        if (activities.length() == 0) {
            val existingSession = sessionToken
            lastSentActivitiesJson = currentJson
            if (existingSession != null) {
                sessionToken = null
                deleteRemoteSession(bearer, existingSession)
            }
            return
        }

        if (!Art.validId(defaultAppId) && (0 until activities.length()).any {
                !Art.validId(activities.getJSONObject(it).optString("application_id"))
            }) {
            scheduleRetry(-1, 0L, retryPushOnly = true, message = "Add a Discord app in Settings")
            return
        }

        val normalizedActivities = JSONArray()
        for (i in 0 until activities.length()) {
            val act = JSONObject(activities.getJSONObject(i).toString())
            if (act.optString("application_id").isEmpty() && Art.validId(defaultAppId)) {
                act.put("application_id", defaultAppId)
            }
            if (act.optString("platform").isEmpty()) act.put("platform", PLATFORM)
            normalizedActivities.put(act)
        }

        val payload = JSONObject().apply {
            put("activities", normalizedActivities)
            sessionToken?.let { put("token", it) }
        }

        Logs.add("POST headless-sessions session=${sessionToken != null} activities=$normalizedActivities")

        val req = Request.Builder()
            .url(HEADLESS_URL)
            .header("Authorization", "Bearer $bearer")
            .header("User-Agent", Api.UA)
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        var retryAfterMs = 0L
        var errText: String? = null
        val code = try {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful && body.isNotEmpty()) {
                    val json = JSONObject(body)
                    if (json.has("token")) {
                        sessionToken = json.getString("token")
                    }
                } else if (resp.code == 429) {
                    retryAfterMs = parseRetryAfterMs(body, resp.header("Retry-After"))
                } else if (!resp.isSuccessful) {
                    errText = discordError(resp.code, body)
                }
                resp.code
            }
        } catch (_: Exception) {
            0
        }

        if (!wanted || token != bearer) return

        Logs.add("POST headless-sessions -> HTTP $code")
        if (errText != null) Log.w("DPM", "Presence push failed: $errText")

        when (code) {
            400 -> if (sessionToken != null) {
                sessionToken = null
                pushPresence(force = true)
            } else {
                scheduleRetry(code, 0L, retryPushOnly = true, message = errText)
            }

            in 200..299 -> {
                ready = true
                attempts = 0
                lastSentActivitiesJson = currentJson
                lastPushTimeMs = System.currentTimeMillis()
                setState("Connected as $accountName")
            }
            401 -> {
                ready = false
                renewAndReconnect(bearer)
            }
            429 -> scheduleRetry(429, retryAfterMs, retryPushOnly = true)
            else -> scheduleRetry(code, 0L, retryPushOnly = true, message = errText)
        }
    }

    private fun parseRetryAfterMs(body: String, headerVal: String?): Long {
        try {
            if (body.isNotEmpty()) {
                val sec = JSONObject(body).optDouble("retry_after", 0.0)
                if (sec > 0) return (sec * 1000L).toLong() + 500L
            }
        } catch (_: Exception) {}
        val h = headerVal?.toDoubleOrNull()
        if (h != null && h > 0) return (h * 1000L).toLong() + 500L
        return 10_000L
    }

    private fun deleteRemoteSession(bearer: String, sessToken: String) {
        try {
            val payload = JSONObject().put("token", sessToken)
            val req = Request.Builder()
                .url(HEADLESS_DELETE_URL)
                .header("Authorization", "Bearer $bearer")
                .header("User-Agent", Api.UA)
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    private suspend fun renewAndReconnect(expired: String) {
        val fresh = try {
            DiscordOAuth.renew(DPMApp.ctx, expired)
        } catch (_: Exception) {
            if (wanted && token == expired) scheduleRetry(0, 0L, retryPushOnly = false)
            return
        }
        if (!wanted || token != expired) return
        if (fresh == null || renewals >= 2) {
            wanted = false
            ready = false
            setState("Token rejected by Discord")
            return
        }
        renewals++
        token = fresh
        open()
    }

    private fun discordError(code: Int, body: String): String {
        Logs.add("Discord HTTP $code: ${body.take(2000)}")
        val o = try { JSONObject(body) } catch (_: Exception) { return "HTTP $code" }
        val msg = o.optString("message", "").ifEmpty { "HTTP $code" }
        val field = firstFieldError(o.optJSONObject("errors"), "")
        return if (field.isEmpty()) msg else "$msg ($field)"
    }

    private fun firstFieldError(o: JSONObject?, path: String): String {
        if (o == null) return ""
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val p = if (path.isEmpty()) k else "$path.$k"
            if (k == "_errors") {
                val first = o.optJSONArray(k)?.optJSONObject(0)?.optString("message").orEmpty()
                if (first.isNotEmpty()) return "$path: $first"
            } else {
                val inner = o.optJSONObject(k)
                if (inner != null) {
                    val r = firstFieldError(inner, p)
                    if (r.isNotEmpty()) return r
                }
            }
        }
        return ""
    }

    private fun scheduleRetry(code: Int, retryAfterMs: Long, retryPushOnly: Boolean, message: String? = null) {
        if (!wanted) return
        val wait = if (retryAfterMs > 0L) {
            retryAfterMs
        } else {
            minOf(60_000L, 5_000L shl minOf(attempts, 4))
        }
        attempts++
        setState(
            when (code) {
                0 -> "Offline, retrying in ${wait / 1000}s"
                429 -> "Rate limited, waiting ${wait / 1000}s"
                else -> message ?: "Reconnecting (HTTP $code)"
            }
        )
        retry?.cancel()
        retry = scope.launch {
            delay(wait)
            if (!wanted) return@launch
            if (retryPushOnly && accountName != "?") {
                pushPresence(force = true)
            } else {
                open()
            }
        }
    }
}

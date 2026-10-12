package com.dpm.app

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object Api {
    const val BASE = "https://discord.com/api/v9"
    const val BUILD = 290000
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val client = OkHttpClient()
    private val jsonType = "application/json".toMediaType()

    private val superProps: String by lazy {
        val o = JSONObject()
            .put("os", "Windows")
            .put("browser", "Chrome")
            .put("device", "")
            .put("system_locale", "en-US")
            .put("browser_user_agent", UA)
            .put("browser_version", "124.0.0.0")
            .put("os_version", "10")
            .put("referrer", "")
            .put("referring_domain", "")
            .put("release_channel", "stable")
            .put("client_build_number", BUILD)
        Base64.encodeToString(o.toString().toByteArray(), Base64.NO_WRAP)
    }

    class Resp(val code: Int, val body: String) {
        fun obj(): JSONObject = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
    }

    private fun base(path: String, token: String?): Request.Builder {
        val b = Request.Builder()
            .url(BASE + path)
            .header("User-Agent", UA)
            .header("X-Super-Properties", superProps)
            .header("X-Discord-Locale", "en-US")
            .header("Origin", "https://discord.com")
            .header("Referer", "https://discord.com/channels/@me")
        if (token != null) b.header("Authorization", token)
        return b
    }

    private fun exec(req: Request): Resp =
        try {
            client.newCall(req).execute().use { Resp(it.code, it.body?.string() ?: "") }
        } catch (e: IOException) {
            Resp(-1, e.message ?: "network error")
        }

    fun call(method: String, path: String, token: String?, body: JSONObject? = null): Resp {
        val rb = when {
            body != null -> body.toString().toRequestBody(jsonType)
            method == "GET" -> null
            else -> "".toRequestBody(jsonType)
        }
        return exec(base(path, token).method(method, rb).build())
    }

    fun me(token: String): Resp = call("GET", "/users/@me", token)
}

package com.dpm.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object Api {
    const val BASE = "https://discord.com/api/v10"
    const val UA = "Discord-Android/260000;RNA"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    class Resp(val code: Int, val body: String) {
        fun obj(): JSONObject = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
    }

    fun authHeader(token: String): String {
        val t = token.trim()

        if (t.startsWith("Token ", ignoreCase = true)) return t.substring(6).trim()
        return if (t.startsWith("Bearer ", ignoreCase = true) || t.startsWith("Bot ", ignoreCase = true)) t else "Bearer $t"
    }

    private fun base(path: String, token: String?): Request.Builder {
        val b = Request.Builder()
            .url(BASE + path)
            .header("User-Agent", UA)
            .header("X-Discord-Locale", "en-US")
        if (!token.isNullOrEmpty()) {
            b.header("Authorization", authHeader(token))
        }
        return b
    }

    private fun exec(req: Request): Resp =
        try {
            http.newCall(req).execute().use { Resp(it.code, it.body?.string() ?: "") }
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

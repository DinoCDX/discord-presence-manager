package com.dpm.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object Art {
    class Resolved(val big: String, val small: String?)

    private class Entry(val r: Resolved?, val at: Long)

    private const val RETRY_MS = 120_000L
    private const val MAX_ASSETS = 280
    private const val SIZE = 512

    private val cache = ConcurrentHashMap<String, Entry>()
    private val ids = ConcurrentHashMap<String, String>()

    @Volatile
    private var loadedFor = ""

    @Volatile
    var status: String = ""
        private set

    fun appId(c: Context): String = Store.get(c, "app_id", "").trim()

    fun reset() {
        cache.clear()
        ids.clear()
        loadedFor = ""
        status = ""
    }

    private fun sha(b: ByteArray, n: Int): String =
        MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }.take(n)

    private fun sig(b: Bitmap?): String {
        if (b == null) return "-"
        var h = b.width * 31 + b.height
        try {
            if (b.config != Bitmap.Config.HARDWARE) {
                for (x in 0 until 4) for (y in 0 until 4) {
                    h = h * 31 + b.getPixel(b.width * (2 * x + 1) / 8, b.height * (2 * y + 1) / 8)
                }
            }
        } catch (e: Exception) {
        }
        return h.toString()
    }

    private fun explain(code: Int): String = when (code) {
        -1 -> "No connection"
        404 -> "Application not found. Check the ID."
        401, 403 -> "Not allowed. The application must belong to the logged in account."
        else -> "Discord error $code"
    }

    private fun load(token: String, app: String): Boolean {
        if (loadedFor == app) return true
        val r = Api.call("GET", "/oauth2/applications/$app/assets", token)
        if (r.code != 200) {
            status = explain(r.code)
            return false
        }
        ids.clear()
        try {
            val a = JSONArray(r.body)
            for (n in 0 until a.length()) {
                val o = a.optJSONObject(n) ?: continue
                val name = o.optString("name")
                val id = o.optString("id")
                if (name.isNotEmpty() && id.isNotEmpty()) ids[name] = id
            }
        } catch (e: Exception) {
        }
        loadedFor = app
        status = "Connected, ${ids.size} images uploaded"
        return true
    }

    private fun makeRoom(token: String, app: String) {
        if (ids.size < MAX_ASSETS) return
        val old = ids.entries.filter { it.key.startsWith("art") }.sortedBy { it.value.toLongOrNull() ?: 0L }
        for (e in old.take(ids.size - MAX_ASSETS + 20)) {
            Api.call("DELETE", "/oauth2/applications/$app/assets/${e.value}", token)
            ids.remove(e.key)
        }
    }

    private fun asset(token: String, app: String, name: String, mime: String, bytes: () -> ByteArray): String? {
        ids[name]?.let { return it }
        makeRoom(token, app)
        val data = "data:$mime;base64," + Base64.encodeToString(bytes(), Base64.NO_WRAP)
        val r = Api.call(
            "POST",
            "/oauth2/applications/$app/assets",
            token,
            JSONObject().put("name", name).put("image", data).put("type", "1")
        )
        if (r.code !in 200..299) {
            status = "Upload failed. ${explain(r.code)}"
            return null
        }
        val id = r.obj().optString("id")
        if (id.isEmpty()) return null
        ids[name] = id
        status = "Connected, ${ids.size} images uploaded"
        return id
    }

    fun check(c: Context): String {
        val app = appId(c)
        val token = Store.token(c) ?: return "Not logged in"
        if (app.isEmpty()) return "Enter an application ID first"
        loadedFor = ""
        return if (load(token, app)) status else status
    }

    @Synchronized
    fun resolve(c: Context, token: String, i: Info): Resolved? {
        val app = appId(c)
        val pkg = i.pkg
        if (app.isEmpty() || pkg == null) return null
        val k = "$app|$pkg|${i.key}|${sig(i.bitmap)}"
        val now = System.currentTimeMillis()
        cache[k]?.let { if (it.r != null || now - it.at < RETRY_MS) return it.r }
        val r = build(c, token, app, pkg, i.bitmap)
        cache[k] = Entry(r, now)
        return r
    }

    @Synchronized
    fun resolveStatic(c: Context, token: String, i: Info): Resolved? {
        val app = appId(c)
        if (app.isEmpty()) return null
        val large = StaticFiles.file(c, i.largeFile.orEmpty())
        val small = StaticFiles.file(c, i.smallFile.orEmpty())
        if (large == null && small == null) return null
        val k = "$app|static|${large?.name}|${large?.length()}|${small?.name}|${small?.length()}"
        val now = System.currentTimeMillis()
        cache[k]?.let { if (it.r != null || now - it.at < RETRY_MS) return it.r }
        val r = if (!load(token, app)) {
            null
        } else {
            val big = large?.let { f -> val b = f.readBytes(); asset(token, app, "st" + sha(b, 24), "image/jpeg") { b } }
            val sm = small?.let { f -> val b = f.readBytes(); asset(token, app, "st" + sha(b, 24), "image/jpeg") { b } }
            when {
                big != null -> Resolved(big, sm)
                sm != null -> Resolved(sm, null)
                else -> null
            }
        }
        cache[k] = Entry(r, now)
        return r
    }

    private fun build(c: Context, token: String, app: String, pkg: String, cover: Bitmap?): Resolved? {
        if (!load(token, app)) return null
        val icon = asset(token, app, "ic" + sha(pkg.toByteArray(), 24), "image/png") {
            AppIcons.png(AppIcons.render(c, pkg, SIZE) ?: Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888))
        }
        val art = cover?.let { b ->
            val bytes = AppIcons.jpeg(AppIcons.square(b, SIZE))
            asset(token, app, "art" + sha(bytes, 24), "image/jpeg") { bytes }
        }
        return when {
            art != null -> Resolved(art, icon)
            icon != null -> Resolved(icon, null)
            else -> null
        }
    }
}

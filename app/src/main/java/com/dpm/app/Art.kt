package com.dpm.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object Art {
    class Resolved(val big: String, val small: String?)

    private class Entry(val r: Resolved?, val at: Long)

    const val DEFAULT_IMAGE_URL = "https://raw.githubusercontent.com/DinoCDX/discord-presence-manager/main/docs/assets/icon-512.png"
    const val NEED_TOKEN = "Add your Discord login token in Settings to upload pictures"
    private const val RETRY_MS = 120_000L
    private const val MAX_ASSETS = 280
    private const val SIZE = 512

    private val cache = ConcurrentHashMap<String, Entry>()
    private val ids = ConcurrentHashMap<String, String>()
    private val externalPaths = ConcurrentHashMap<String, String>()
    private val extLock = Any()
    private const val EXT_KEY = "external_paths"
    private const val EXT_MAX = 400
    private const val EXT_TTL_MS = 7L * 24 * 60 * 60 * 1000
    @Volatile
    private var extLoaded = false
    @Volatile
    private var extBlockedUntil = 0L
    private val idPattern = Regex("\\d{17,20}")

    @Volatile
    private var loadedFor = ""

    @Volatile
    var status: String = ""
        private set

    fun validId(s: String): Boolean = idPattern.matches(s)

    fun appId(c: Context): String {
        val stored = Store.activeApp(c)?.id.orEmpty()
        if (validId(stored)) return stored
        val built = DiscordOAuth.clientId(c)
        return if (validId(built)) built else ""
    }

    fun ownAppId(c: Context): String {
        val stored = Store.activeApp(c)?.id.orEmpty()
        return if (validId(stored)) stored else ""
    }

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

    private fun explain(code: Int, body: String): String {
        val detail = body.replace('\n', ' ').take(160)
        val what = when (code) {
            -1 -> "No connection"
            404 -> "Application not found. Check the ID."
            401 -> "The upload token was rejected. Paste a current token in Settings, under Developer."
            403 -> "That account can't manage this application"
            else -> "Discord error $code"
        }
        return "$what (HTTP $code): $detail"
    }

    private fun blank(): Bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)

    private fun load(token: String, app: String): Boolean {
        if (loadedFor == app) return true
        val r = Api.call("GET", "/oauth2/applications/$app/assets", token)
        if (r.code != 200) {
            status = explain(r.code, r.body)
            Logs.add("assets list ${r.code} ${r.body.take(300)}")
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
            JSONObject().put("name", name).put("image", data).put("type", 1)
        )
        if (r.code !in 200..299) {
            status = "Upload failed. ${explain(r.code, r.body)}"
            Logs.add("asset upload ${r.code} ${r.body.take(300)}")
            return null
        }
        val id = r.obj().optString("id")
        if (id.isEmpty()) return null
        ids[name] = id
        status = "Connected, ${ids.size} images uploaded"
        return id
    }

    fun assetToken(c: Context): String? {
        val t = Store.get(c, "upload_token", "").trim().removePrefix("Token ").trim()
        return if (t.isNotEmpty()) "Token $t" else null
    }

    private fun uploadToken(c: Context): String? = if (ownAppId(c).isEmpty()) null else assetToken(c)

    private fun loadExternal(c: Context) {
        val raw = Store.get(c, EXT_KEY, "")
        if (raw.isEmpty()) return
        val now = System.currentTimeMillis()
        try {
            val o = JSONObject(raw)
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = o.optString(k)
                val at = v.substringBefore('|').toLongOrNull() ?: 0L
                if (now - at < EXT_TTL_MS) externalPaths[k] = v
            }
        } catch (e: Exception) {
        }
    }

    private fun saveExternal(c: Context) {
        val kept = externalPaths.entries.sortedByDescending { it.value.substringBefore('|').toLongOrNull() ?: 0L }
            .take(EXT_MAX)
        externalPaths.keys.retainAll(kept.map { it.key }.toSet())
        val o = JSONObject()
        kept.forEach { o.put(it.key, it.value) }
        Store.put(c, EXT_KEY, o.toString())
    }

    fun external(c: Context, app: String, url: String): String? {
        val key = "$app|$url"
        synchronized(extLock) {
            if (!extLoaded) {
                loadExternal(c)
                extLoaded = true
            }
            externalPaths[key]?.let { return it.substringAfter('|') }
        }
        if (System.currentTimeMillis() < extBlockedUntil) return null
        val token = Store.token(c) ?: return null
        val r = Api.call(
            "POST",
            "/applications/$app/external-assets",
            token,
            JSONObject().put("urls", JSONArray().put(url))
        )
        if (r.code == 429) {
            val waitMs = (r.obj().optDouble("retry_after", 60.0) * 1000).toLong() + 1000L
            extBlockedUntil = System.currentTimeMillis() + waitMs
            Logs.add("external-assets rate limited, pausing for ${waitMs / 1000}s")
            return null
        }
        if (r.code !in 200..299) {
            Logs.add("external-assets ${r.code} ${r.body.take(300)}")
            return null
        }
        val path = try {
            JSONArray(r.body).getJSONObject(0).getString("external_asset_path")
        } catch (e: Exception) {
            return null
        }
        val mp = "mp:$path"
        synchronized(extLock) {
            externalPaths[key] = "${System.currentTimeMillis()}|$mp"
            saveExternal(c)
        }
        return mp
    }

    fun check(c: Context): String {
        val app = ownAppId(c)
        if (app.isEmpty()) return "Add a Discord app in Settings first"
        val token = assetToken(c) ?: run {
            status = NEED_TOKEN
            return NEED_TOKEN
        }
        loadedFor = ""
        load(token, app)
        return status
    }

    @Synchronized
    fun resolve(c: Context, i: Info): Resolved? {
        val app = appId(c)
        val pkg = i.pkg ?: return null
        if (!validId(app)) return null
        val k = "$app|$pkg|${i.key}|${i.artUri}|${sig(i.bitmap)}"
        val now = System.currentTimeMillis()
        cache[k]?.let { if (it.r != null || now - it.at < RETRY_MS) return it.r }
        val r = mediaImages(c, app, pkg, i, uploadToken(c))
        cache[k] = Entry(r, now)
        return r
    }

    private fun mediaImages(c: Context, app: String, pkg: String, i: Info, tok: String?): Resolved? {
        val large = i.artUri?.takeIf { it.startsWith("https://") }?.let { external(c, app, it) }
            ?: tok?.let { uploadCover(it, app, i.bitmap) }
            ?: Apps.def(pkg)?.icon?.let { external(c, app, it) }
            ?: external(c, app, DEFAULT_IMAGE_URL)
            ?: return null
        val small = tok?.let { uploadIcon(c, it, app, pkg) }
        return Resolved(large, small)
    }

    private fun uploadCover(tok: String, app: String, cover: Bitmap?): String? {
        if (cover == null || !load(tok, app)) return null
        val bytes = AppIcons.jpeg(AppIcons.square(cover, SIZE))
        return asset(tok, app, "art" + sha(bytes, 24), "image/jpeg") { bytes }
    }

    private fun uploadIcon(c: Context, tok: String, app: String, pkg: String): String? {
        if (!load(tok, app)) return null
        return asset(tok, app, "ic" + sha(pkg.toByteArray(), 24), "image/png") {
            AppIcons.png(AppIcons.render(c, pkg, SIZE) ?: blank())
        }
    }

    @Synchronized
    fun resolveStatic(c: Context, i: Info): Resolved? {
        val app = appId(c)
        if (!validId(app)) return null
        val tok = uploadToken(c)
        val largeName = i.largeFile.orEmpty()
        val smallName = i.smallFile.orEmpty()
        val k = "$app|static|$largeName|$smallName|${tok != null}"
        val now = System.currentTimeMillis()
        cache[k]?.let { if (it.r != null || now - it.at < RETRY_MS) return it.r }
        val big = fromLink(c, app, largeName)
            ?: tok?.let { uploadFile(it, app, StaticFiles.file(c, largeName)) }
            ?: external(c, app, DEFAULT_IMAGE_URL)
        val small = fromLink(c, app, smallName)
            ?: tok?.let { uploadFile(it, app, StaticFiles.file(c, smallName)) }
        val r = big?.let { Resolved(it, small) }
        cache[k] = Entry(r, now)
        return r
    }

    private fun fromLink(c: Context, app: String, name: String): String? =
        if (name.startsWith("https://")) external(c, app, name) else null

    private fun uploadFile(tok: String, app: String, f: File?): String? {
        if (f == null || !load(tok, app)) return null
        val b = f.readBytes()
        return asset(tok, app, "st" + sha(b, 24), "image/jpeg") { b }
    }
}

package com.dpm.app

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object Art {
    class Resolved(val big: String, val small: String?)

    private class Entry(val r: Resolved?, val at: Long)

    const val DEFAULT_IMAGE_URL = "https://raw.githubusercontent.com/DinoCDX/discord-presence-manager/main/docs/assets/icon-512.png"
    private const val RETRY_MS = 120_000L
    private const val SIZE = 512

    private val cache = ConcurrentHashMap<String, Entry>()
    private val lookups = ConcurrentHashMap<String, String>()
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

    fun validId(s: String): Boolean = idPattern.matches(s)

    fun appId(c: Context): String {
        val stored = Store.activeApp(c)?.id.orEmpty()
        if (validId(stored)) return stored
        val built = DiscordOAuth.clientId(c)
        return if (validId(built)) built else ""
    }

    fun coverLookup(title: String, artist: String): String? {
        val t = title.trim()
        val a = artist.trim()
        if (t.isEmpty() || a.isEmpty()) return null
        val key = "$t|$a"
        lookups[key]?.let { return it.ifEmpty { null } }
        val found = try {
            fetchCover(t, a)
        } catch (e: Exception) {
            null
        }
        lookups[key] = found.orEmpty()
        return found
    }

    private fun fetchCover(title: String, artist: String): String? {
        val term = URLEncoder.encode("$artist $title", "UTF-8")
        val conn = URL("https://itunes.apple.com/search?term=$term&entity=song&limit=3")
            .openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).optJSONArray("results") ?: return null
            for (k in 0 until results.length()) {
                val r = results.getJSONObject(k)
                if (!sameArtist(r.optString("artistName"), artist)) continue
                val art = r.optString("artworkUrl100").takeIf { it.startsWith("https://") } ?: continue
                return art.replace("100x100bb", "600x600bb")
            }
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun sameArtist(found: String, wanted: String): Boolean {
        val f = found.lowercase()
        val w = wanted.lowercase()
        return f.isNotEmpty() && (f.contains(w) || w.contains(f))
    }

    fun reset() {
        cache.clear()
    }

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

    fun external(c: Context, app: String, input: String): String? {
        val url = normalizeImageUrl(input)
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

    @Synchronized
    fun resolve(c: Context, i: Info): Resolved? {
        val app = appId(c)
        val pkg = i.pkg ?: return null
        if (!validId(app)) return null
        val k = "$app|$pkg|${i.key}|${i.artUri}|${sig(i.bitmap)}"
        val now = System.currentTimeMillis()
        cache[k]?.let { if (it.r != null || now - it.at < RETRY_MS) return it.r }
        val r = mediaImages(c, app, pkg, i)
        cache[k] = Entry(r, now)
        return r
    }

    private fun mediaImages(c: Context, app: String, pkg: String, i: Info): Resolved? {
        val icon = Apps.def(pkg)?.icon?.let { external(c, app, it) }
        val cover = i.artUri?.takeIf { it.startsWith("https://") }?.let { external(c, app, it) }
            ?: coverLookup(i.details.orEmpty(), i.state.orEmpty())?.let { external(c, app, it) }
        if (cover != null) return Resolved(cover, icon)
        val big = icon ?: external(c, app, DEFAULT_IMAGE_URL) ?: return null
        return Resolved(big, null)
    }

    @Synchronized
    fun resolveStatic(c: Context, i: Info): Resolved? {
        val app = appId(c)
        if (!validId(app)) return null
        val largeName = i.largeFile.orEmpty()
        val smallName = i.smallFile.orEmpty()
        val k = "$app|static|$largeName|$smallName"
        val now = System.currentTimeMillis()
        cache[k]?.let { if (it.r != null || now - it.at < RETRY_MS) return it.r }
        val big = fromLink(c, app, largeName) ?: external(c, app, DEFAULT_IMAGE_URL)
        val small = fromLink(c, app, smallName)
        val r = big?.let { Resolved(it, small) }
        cache[k] = Entry(r, now)
        return r
    }

    private fun fromLink(c: Context, app: String, name: String): String? =
        if (name.startsWith("https://")) external(c, app, name) else null
}

private val blobPage = Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$")
private val imageExtensions = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".avif")

fun normalizeImageUrl(input: String): String {
    val t = input.trim()
    val m = blobPage.find(t) ?: return t
    return "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}"
}

fun imageUrlProblem(input: String): String? {
    val u = normalizeImageUrl(input)
    if (!u.startsWith("https://")) return "Start the link with https://"
    val path = u.substringBefore('?').substringBefore('#').lowercase()
    if (imageExtensions.none { path.endsWith(it) }) {
        return "Use a direct link to a .png, .jpg, .gif, .webp or .avif file"
    }
    return null
}

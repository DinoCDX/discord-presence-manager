package com.dpm.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class Info(
    val type: Int,
    val name: String,
    val details: String? = null,
    val state: String? = null,
    val album: String? = null,
    val start: Long? = null,
    val end: Long? = null,
    val pkg: String? = null,
    val artUri: String? = null,
    val bitmap: Bitmap? = null
) {
    val key: String get() = "$name|$details|$state"
}

object Dynamic {
    private var lastPkg = ""
    private var since = 0L
    private var mKey = ""
    private var mStart = 0L
    private var homePkg: String? = null
    private var homeAt = 0L
    private val labels = ConcurrentHashMap<String, String>()
    private val categories = ConcurrentHashMap<String, Int>()

    fun staticInfo(c: Context): Info? {
        val name = Store.get(c, "s_name", "").trim()
        if (name.isEmpty()) return null
        val timer = Store.get(c, "s_timer", "0") == "1"
        val started = Store.get(c, "s_started", "0").toLongOrNull() ?: 0L
        return Info(
            type = Store.get(c, "s_type", "0").toIntOrNull() ?: 0,
            name = name,
            details = Store.get(c, "s_details", "").trim().ifEmpty { null },
            state = Store.get(c, "s_state", "").trim().ifEmpty { null },
            start = if (timer && started > 0L) started else null
        )
    }

    @Synchronized
    fun current(c: Context): Info? {
        val fg = Foreground.pkg(c)
        return media(c, fg) ?: app(c, fg)
    }

    fun toJson(c: Context, i: Info): JSONObject {
        val o = JSONObject().put("name", i.name).put("type", i.type)
        i.details?.let { o.put("details", it) }
        i.state?.let { o.put("state", it) }
        if (i.start != null) {
            val ts = JSONObject().put("start", i.start)
            i.end?.let { ts.put("end", it) }
            o.put("timestamps", ts)
        }
        if (i.pkg != null && Store.get(c, "show_art", "1") == "1") {
            val token = Store.token(c)
            if (token != null) addAssets(c, o, i, token)
        }
        return o
    }

    private fun addAssets(c: Context, o: JSONObject, i: Info, token: String) {
        val r = Art.resolve(c, token, i) ?: return
        val assets = JSONObject().put("large_image", r.big)
        (i.album ?: i.details)?.let { assets.put("large_text", it) }
        r.small?.let { assets.put("small_image", it).put("small_text", i.name) }
        o.put("assets", assets)
        o.put("application_id", Art.appId(c))
    }

    private fun appLabel(c: Context, pkg: String): String =
        labels.getOrPut(pkg) {
            try {
                val pm = c.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
        }

    private fun categoryOrNull(c: Context, pkg: String): Int? =
        categories[pkg] ?: try {
            c.packageManager.getApplicationInfo(pkg, 0).category.also { categories[pkg] = it }
        } catch (e: Exception) {
            null
        }

    private fun category(c: Context, pkg: String): Int = categoryOrNull(c, pkg) ?: ApplicationInfo.CATEGORY_UNDEFINED

    private fun home(c: Context): String? {
        val now = System.currentTimeMillis()
        if (now - homeAt > 60_000L) {
            homePkg = c.packageManager
                .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                ?.activityInfo?.packageName
            homeAt = now
        }
        return homePkg
    }

    private fun text(md: MediaMetadata, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> md.getString(k)?.takeIf { it.isNotBlank() } }

    private fun media(c: Context, fg: String?): Info? {
        return try {
            val ignored = Store.pkgs(c, "ignore_apps")
            val msm = c.getSystemService(MediaSessionManager::class.java)
            val playing = msm.getActiveSessions(ComponentName(c, MediaListener::class.java)).filter {
                it.playbackState?.state == PlaybackState.STATE_PLAYING && it.packageName !in ignored
            }
            val ctl = playing.firstOrNull { it.packageName == fg } ?: playing.firstOrNull() ?: return null
            val md = ctl.metadata ?: return null
            val title = text(md, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: return null
            val artist = text(
                md,
                MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_AUTHOR,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
            )
            val album = md.getString(MediaMetadata.METADATA_KEY_ALBUM)
            val dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val ps = ctl.playbackState ?: return null
            val now = System.currentTimeMillis()
            val key = "${ctl.packageName}|$title|$artist|$dur"
            var start: Long
            if (ps.position >= 0) {
                var pos = ps.position
                if (ps.lastPositionUpdateTime > 0) {
                    pos += ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
                }
                pos = if (dur > 0) pos.coerceIn(0L, dur) else pos.coerceAtLeast(0L)
                start = now - pos
                if (key == mKey && abs(start - mStart) < 3000) start = mStart
            } else {
                start = if (key == mKey) mStart else now
            }
            mKey = key
            mStart = start
            val uri = listOf(
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
            ).mapNotNull { md.getString(it) }.firstOrNull { it.startsWith("http") }
            val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val pkg = ctl.packageName
            Info(
                type = if (Apps.isWatching(pkg, category(c, pkg))) 3 else 2,
                name = appLabel(c, pkg),
                details = title,
                state = artist,
                album = album,
                start = start,
                end = if (dur > 0) start + dur else null,
                pkg = pkg,
                artUri = uri,
                bitmap = bmp
            )
        } catch (e: SecurityException) {
            null
        }
    }

    private fun app(c: Context, pkg: String?): Info? {
        if (pkg == null) {
            lastPkg = ""
            return null
        }
        if (pkg == c.packageName || pkg == "com.android.systemui") return null
        if (pkg == home(c)) {
            lastPkg = ""
            return null
        }
        if (pkg in Store.pkgs(c, "ignore_apps")) return null
        val cat = categoryOrNull(c, pkg) ?: return null
        val picked = pkg in Store.pkgs(c, "show_apps")
        val all = Store.get(c, "all_apps", "0") == "1"
        val auto = Store.get(c, "auto_apps", "1") == "1" && Apps.autoShow(pkg, cat)
        val game = cat == ApplicationInfo.CATEGORY_GAME
        if (!game && !picked && !all && !auto) return null
        if (pkg != lastPkg) {
            lastPkg = pkg
            since = System.currentTimeMillis()
        }
        val custom = Customs.all(c)[pkg]
        return Info(
            type = custom?.type ?: Apps.typeFor(pkg, cat),
            name = custom?.name ?: appLabel(c, pkg),
            details = custom?.details ?: Apps.def(pkg)?.details,
            state = custom?.state,
            start = since,
            pkg = pkg
        )
    }
}

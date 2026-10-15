package com.dpm.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    class Available(
        val tag: String,
        val notes: String,
        val url: String,
        val size: Long,
        val sha256: String?,
        val page: String
    ) : UpdateState()
    class Downloading(val tag: String, val done: Long, val total: Long) : UpdateState()
    class Ready(val tag: String, val file: File) : UpdateState()
    class Failed(val message: String) : UpdateState()
}

object Updater {
    const val REPO_URL = "https://github.com/DinoCDX/discord-presence-manager"
    private const val API = "https://api.github.com/repos/DinoCDX/discord-presence-manager/releases/latest"
    private const val CHECK_EVERY_MS = 12 * 3_600_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()

    var state: UpdateState by mutableStateOf(UpdateState.Idle)
        private set

    fun installedVersion(c: Context): String =
        try {
            c.packageManager.getPackageInfo(c.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.')
            .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }

    private fun newer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun autoCheck(c: Context) {
        if (Store.get(c, "upd_auto", "1") != "1") return
        val last = Store.get(c, "upd_last", "0").toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS) return
        check(c)
    }

    fun check(c: Context) {
        val s = state
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        val app = c.applicationContext
        state = UpdateState.Checking
        scope.launch { state = fetch(app) }
    }

    private fun fetch(c: Context): UpdateState {
        val req = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DPM")
            .build()
        return try {
            client.newCall(req).execute().use { r ->
                Store.put(c, "upd_last", System.currentTimeMillis().toString())
                if (r.code == 404) return UpdateState.UpToDate
                if (!r.isSuccessful) return UpdateState.Failed("GitHub returned ${r.code}")
                val o = JSONObject(r.body?.string().orEmpty())
                val tag = o.optString("tag_name")
                if (tag.isEmpty() || !newer(tag, installedVersion(c))) return UpdateState.UpToDate
                val list = o.optJSONArray("assets")
                val apk = (0 until (list?.length() ?: 0))
                    .map { list!!.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }
                    ?: return UpdateState.Failed("Release $tag has no APK attached")
                val digest = apk.optString("digest")
                UpdateState.Available(
                    tag = tag,
                    notes = o.optString("body").trim(),
                    url = apk.getString("browser_download_url"),
                    size = apk.optLong("size"),
                    sha256 = if (digest.startsWith("sha256:")) digest.removePrefix("sha256:") else null,
                    page = o.optString("html_url")
                )
            }
        } catch (e: Exception) {
            UpdateState.Failed(e.message ?: "Update check failed")
        }
    }

    fun download(c: Context) {
        val s = state as? UpdateState.Available ?: return
        val app = c.applicationContext
        state = UpdateState.Downloading(s.tag, 0L, s.size)
        scope.launch {
            state = try {
                fetchApk(app, s)
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: "Download failed")
            }
        }
    }

    private fun fetchApk(c: Context, s: UpdateState.Available): UpdateState {
        val dir = File(c.cacheDir, "updates")
        dir.deleteRecursively()
        dir.mkdirs()
        val out = File(dir, "DPM-${s.tag}.apk")
        val md = MessageDigest.getInstance("SHA-256")
        val req = Request.Builder().url(s.url).header("User-Agent", "DPM").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return UpdateState.Failed("Download failed (${r.code})")
            val body = r.body ?: return UpdateState.Failed("Empty download")
            val total = if (s.size > 0L) s.size else body.contentLength()
            var done = 0L
            var shown = 0L
            body.byteStream().use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - shown > 250L) {
                            shown = now
                            state = UpdateState.Downloading(s.tag, done, total)
                        }
                    }
                }
            }
            if (s.size > 0L && done != s.size) {
                out.delete()
                return UpdateState.Failed("Download was incomplete")
            }
        }
        val sum = md.digest().joinToString("") { "%02x".format(it) }
        if (s.sha256 != null && !sum.equals(s.sha256, ignoreCase = true)) {
            out.delete()
            return UpdateState.Failed("Checksum did not match")
        }
        val pkg = c.packageManager.getPackageArchiveInfo(out.absolutePath, 0)?.packageName
        if (pkg != c.packageName) {
            out.delete()
            return UpdateState.Failed("The downloaded file is not a DPM build")
        }
        return UpdateState.Ready(s.tag, out)
    }

    fun install(a: Activity) {
        val s = state as? UpdateState.Ready ?: return
        if (!a.packageManager.canRequestPackageInstalls()) {
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.files", s.file)
        a.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}

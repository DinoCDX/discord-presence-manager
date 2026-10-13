package com.dpm.app

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object Bus {
    val flow = MutableStateFlow(0L)
    fun bump() {
        flow.value = System.nanoTime()
    }
}

object Presence {
    fun mode(c: Context): String = if (Store.get(c, "mode", "dynamic") == "static") "static" else "dynamic"

    fun setMode(c: Context, m: String) = Store.put(c, "mode", m)

    fun running(c: Context): Boolean {
        val r = Store.get(c, "running", "")
        return if (r.isEmpty()) Store.get(c, "mode", "off") != "off" else r == "1"
    }

    fun setRunning(c: Context, on: Boolean) = Store.put(c, "running", if (on) "1" else "0")

    fun paused(c: Context): Boolean = Store.get(c, "paused", "0") == "1"

    fun setPaused(c: Context, p: Boolean) = Store.put(c, "paused", if (p) "1" else "0")

    fun verb(t: Int): String = when (t) {
        2 -> "Listening to"
        3 -> "Watching"
        5 -> "Competing in"
        else -> "Playing"
    }
}

class Win(val days: Int, val start: Int, val end: Int)

object Schedule {
    fun on(c: Context): Boolean = Store.get(c, "sched_on", "0") == "1"

    fun setOn(c: Context, on: Boolean) = Store.put(c, "sched_on", if (on) "1" else "0")

    private var memoRaw: String? = null
    private var memo: List<Win> = emptyList()

    @Synchronized
    fun windows(c: Context): List<Win> {
        val raw = Store.get(c, "sched_windows", "[]")
        if (raw == memoRaw) return memo
        val list = try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Win(o.getInt("d"), o.getInt("s"), o.getInt("e"))
            }
        } catch (e: Exception) {
            emptyList()
        }
        memoRaw = raw
        memo = list
        return list
    }

    fun save(c: Context, list: List<Win>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("d", it.days).put("s", it.start).put("e", it.end)) }
        Store.put(c, "sched_windows", a.toString())
    }

    fun active(c: Context, cal: Calendar = Calendar.getInstance()): Boolean {
        val day = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val prev = (day + 6) % 7
        val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return windows(c).any { w ->
            val today = w.days shr day and 1 == 1
            val yesterday = w.days shr prev and 1 == 1
            when {
                w.start == w.end -> today
                w.start < w.end -> today && m >= w.start && m < w.end
                else -> (today && m >= w.start) || (yesterday && m < w.end)
            }
        }
    }

    fun clock(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    private val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun daysLabel(days: Int): String = when (days) {
        127 -> "Every day"
        31 -> "Weekdays"
        96 -> "Weekends"
        else -> names.filterIndexed { i, _ -> days shr i and 1 == 1 }.joinToString(" ")
    }
}

class Custom(val type: Int, val name: String?, val details: String?, val state: String?)

object Sources {
    const val MUSIC = "music"
    const val GAME = "game"
    const val APP = "app"
    val all = listOf(MUSIC, GAME, APP)

    fun label(k: String) = when (k) {
        MUSIC -> "Music and video"
        GAME -> "Games"
        else -> "Other apps"
    }

    fun hint(k: String) = when (k) {
        MUSIC -> "Whatever is playing right now"
        GAME -> "The game on your screen"
        else -> "Other apps you allowed in the Apps tab"
    }

    fun order(c: Context): List<String> {
        val saved = Store.get(c, "src_order", "").split(",").filter { it in all }.distinct()
        return saved + all.filter { it !in saved }
    }

    fun setOrder(c: Context, list: List<String>) = Store.put(c, "src_order", list.joinToString(","))

    fun enabled(c: Context, k: String): Boolean = Store.get(c, "src_$k", "1") == "1"

    fun setEnabled(c: Context, k: String, on: Boolean) = Store.put(c, "src_$k", if (on) "1" else "0")
}

object Customs {
    private var memoRaw: String? = null
    private var memo: Map<String, Custom> = emptyMap()

    @Synchronized
    fun all(c: Context): Map<String, Custom> {
        val raw = Store.get(c, "app_custom", "{}")
        if (raw == memoRaw) return memo
        val out = try {
            val o = JSONObject(raw)
            val map = HashMap<String, Custom>()
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = o.getJSONObject(k)
                map[k] = Custom(
                    v.optInt("t", 0),
                    v.optString("n").ifBlank { null },
                    v.optString("d").ifBlank { null },
                    v.optString("s").ifBlank { null }
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
        memoRaw = raw
        memo = out
        return out
    }

    fun save(c: Context, m: Map<String, Custom>) {
        val o = JSONObject()
        m.forEach { (k, v) ->
            o.put(
                k,
                JSONObject().put("t", v.type).put("n", v.name.orEmpty()).put("d", v.details.orEmpty()).put("s", v.state.orEmpty())
            )
        }
        Store.put(c, "app_custom", o.toString())
    }
}

object UserStatus {
    @Volatile
    var status: String? = null
        private set

    @Volatile
    private var at = 0L

    fun refresh(c: Context) {
        val now = System.currentTimeMillis()
        if (now - at < 60_000L) return
        at = now
        val t = Store.token(c) ?: return
        val r = Api.call("GET", "/users/@me/settings", t)
        if (r.code == 200) status = r.obj().optString("status").ifEmpty { null }
    }
}

object Logic {
    fun gate(c: Context): String? {
        if (!Presence.running(c)) return "Presence is off"
        if (Presence.paused(c)) return "Paused"
        if (Schedule.on(c) && !Schedule.active(c)) return "Outside your scheduled times"
        if (Store.get(c, "only_online", "0") == "1") {
            val s = UserStatus.status
            if (s != null && s != "online") return "Your Discord status is not Online"
        }
        return null
    }

    fun info(c: Context): Info? =
        if (Presence.mode(c) == "static") Dynamic.staticInfo(c) else Dynamic.current(c)
}

object Perms {
    fun notifications(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun usage(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun listener(c: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    fun battery(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(c.packageName) == true
}

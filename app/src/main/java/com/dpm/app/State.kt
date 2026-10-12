package com.dpm.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class UiState(private val c: Context) {
    var accounts by mutableStateOf(listOf<Account>())
    var activeId by mutableStateOf("")

    @set:JvmName("assignRunning") var running by mutableStateOf(false)
        private set
    @set:JvmName("assignPaused") var paused by mutableStateOf(false)
        private set
    @set:JvmName("assignMode") var mode by mutableStateOf("dynamic")
        private set

    var sType by mutableIntStateOf(0)
    var sName by mutableStateOf("")
    var sDetails by mutableStateOf("")
    var sState by mutableStateOf("")
    var sTimer by mutableStateOf(false)
        private set
    var sStarted by mutableLongStateOf(0L)
        private set
    var savedAt by mutableLongStateOf(0L)
        private set

    @set:JvmName("assignAutoApps") var autoApps by mutableStateOf(true)
        private set
    @set:JvmName("assignAllApps") var allApps by mutableStateOf(false)
        private set
    @set:JvmName("assignImages") var images by mutableStateOf(true)
        private set
    @set:JvmName("assignOnlyOnline") var onlyOnline by mutableStateOf(false)
        private set
    @set:JvmName("assignBootStart") var bootStart by mutableStateOf(true)
        private set
    @set:JvmName("assignAppId") var appId by mutableStateOf("")
        private set

    @set:JvmName("assignSchedOn") var schedOn by mutableStateOf(false)
        private set
    @set:JvmName("assignWindows") var windows by mutableStateOf(listOf<Win>())
        private set

    var showApps by mutableStateOf(setOf<String>())
    var ignoreApps by mutableStateOf(setOf<String>())
    var customs by mutableStateOf(mapOf<String, Custom>())

    var themeMode by mutableStateOf("system")
    @set:JvmName("assignDyn") var dyn by mutableStateOf(true)
        private set
    @set:JvmName("assignOnboarded") var onboarded by mutableStateOf(true)
        private set

    var permTick by mutableIntStateOf(0)

    init {
        reload()
        sType = Store.get(c, "s_type", "0").toIntOrNull() ?: 0
        sName = Store.get(c, "s_name", "")
        sDetails = Store.get(c, "s_details", "")
        sState = Store.get(c, "s_state", "")
        sTimer = flag("s_timer", false)
        sStarted = Store.get(c, "s_started", "0").toLongOrNull() ?: 0L
    }

    private fun flag(k: String, d: Boolean) = Store.get(c, k, if (d) "1" else "0") == "1"

    private fun put(k: String, v: Boolean) = Store.put(c, k, if (v) "1" else "0")

    fun reload() {
        accounts = Store.accounts(c)
        activeId = Store.active(c)?.id.orEmpty()
        running = Presence.running(c)
        paused = Presence.paused(c)
        mode = Presence.mode(c)
        autoApps = flag("auto_apps", true)
        allApps = flag("all_apps", false)
        images = flag("show_art", true)
        onlyOnline = flag("only_online", false)
        bootStart = flag("boot_start", true)
        appId = Store.get(c, "app_id", "")
        schedOn = Schedule.on(c)
        windows = Schedule.windows(c)
        showApps = Store.pkgs(c, "show_apps")
        ignoreApps = Store.pkgs(c, "ignore_apps")
        customs = Customs.all(c)
        themeMode = Store.get(c, "theme", "system")
        dyn = flag("dyn", true)
        onboarded = flag("onboarded", false)
    }

    fun setRunning(on: Boolean) {
        running = on
        Presence.setRunning(c, on)
        if (on) {
            if (paused) setPaused(false)
            PresenceService.ensure(c)
        } else {
            PresenceService.halt(c)
        }
    }

    fun setPaused(p: Boolean) {
        paused = p
        Presence.setPaused(c, p)
        PresenceService.kick()
    }

    fun setMode(m: String) {
        mode = m
        Presence.setMode(c, m)
        PresenceService.kick()
    }

    fun saveStatic() {
        val unchanged = Store.get(c, "s_type", "0") == sType.toString() &&
            Store.get(c, "s_name", "") == sName &&
            Store.get(c, "s_details", "") == sDetails &&
            Store.get(c, "s_state", "") == sState
        if (unchanged) return
        Store.put(c, "s_type", sType.toString())
        Store.put(c, "s_name", sName)
        Store.put(c, "s_details", sDetails)
        Store.put(c, "s_state", sState)
        markSaved()
        PresenceService.kick()
    }

    fun clearStatic() {
        sName = ""
        sDetails = ""
        sState = ""
        saveStatic()
    }

    fun setTimer(v: Boolean) {
        sTimer = v
        put("s_timer", v)
        if (v) {
            sStarted = System.currentTimeMillis()
            Store.put(c, "s_started", sStarted.toString())
        }
        markSaved()
        PresenceService.kick()
    }

    fun staticPreview(): Info? {
        val n = sName.trim()
        if (n.isEmpty()) return null
        return Info(
            type = sType,
            name = n,
            details = sDetails.trim().ifEmpty { null },
            state = sState.trim().ifEmpty { null },
            start = if (sTimer && sStarted > 0L) sStarted else null
        )
    }

    private fun markSaved() {
        savedAt = System.currentTimeMillis()
    }

    fun setAutoApps(v: Boolean) { autoApps = v; put("auto_apps", v); PresenceService.kick() }
    fun setAllApps(v: Boolean) { allApps = v; put("all_apps", v); PresenceService.kick() }
    fun setImages(v: Boolean) { images = v; put("show_art", v); PresenceService.kick() }
    fun setOnlyOnline(v: Boolean) { onlyOnline = v; put("only_online", v); PresenceService.kick() }
    fun setBootStart(v: Boolean) { bootStart = v; put("boot_start", v) }

    fun setAppId(v: String) {
        appId = v
        Store.put(c, "app_id", v.trim())
        Art.reset()
        PresenceService.kick()
    }

    fun setSchedOn(v: Boolean) {
        schedOn = v
        Schedule.setOn(c, v)
        PresenceService.kick()
    }

    fun setWindows(list: List<Win>) {
        windows = list
        Schedule.save(c, list)
        PresenceService.kick()
    }

    fun appMode(pkg: String): Int = if (pkg in ignoreApps) 2 else if (pkg in showApps) 1 else 0

    fun setAppMode(pkg: String, m: Int) {
        showApps = if (m == 1) showApps + pkg else showApps - pkg
        ignoreApps = if (m == 2) ignoreApps + pkg else ignoreApps - pkg
        Store.putPkgs(c, "show_apps", showApps)
        Store.putPkgs(c, "ignore_apps", ignoreApps)
        markSaved()
        PresenceService.kick()
    }

    fun allowApps(pkgs: Collection<String>, on: Boolean, keep: Set<String> = emptySet()) {
        showApps = if (on) {
            showApps + pkgs.filter { it !in ignoreApps }
        } else {
            val locked = customs.keys + keep
            showApps - pkgs.filter { it !in locked }.toSet()
        }
        Store.putPkgs(c, "show_apps", showApps)
        PresenceService.kick()
    }

    fun setCustom(pkg: String, v: Custom?) {
        customs = if (v == null) customs - pkg else customs + (pkg to v)
        Customs.save(c, customs)
        markSaved()
        PresenceService.kick()
    }

    fun setTheme(v: String) { themeMode = v; Store.put(c, "theme", v) }
    fun setDyn(v: Boolean) { dyn = v; put("dyn", v) }
    fun setOnboarded(v: Boolean) { onboarded = v; put("onboarded", v) }
}

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
    var sTimerMode by mutableStateOf("off")
        private set
    var sStarted by mutableLongStateOf(0L)
        private set
    var sEnd by mutableLongStateOf(0L)
        private set
    var sDur by mutableIntStateOf(60)
        private set
    var sLarge by mutableStateOf("")
        private set
    var sSmall by mutableStateOf("")
        private set
    var sLargeText by mutableStateOf("")
    var sB1L by mutableStateOf("")
    var sB1U by mutableStateOf("")
    var sB2L by mutableStateOf("")
    var sB2U by mutableStateOf("")
    var presets by mutableStateOf(listOf<Preset>())
        private set
    @set:JvmName("assignBtnVideo") var btnVideo by mutableStateOf(true)
        private set
    @set:JvmName("assignBtnRepo") var btnRepo by mutableStateOf(true)
        private set
    var savedAt by mutableLongStateOf(0L)
        private set

    @set:JvmName("assignAutoApps") var autoApps by mutableStateOf(true)
        private set
    @set:JvmName("assignAllApps") var allApps by mutableStateOf(false)
        private set
    @set:JvmName("assignImages") var images by mutableStateOf(false)
        private set
    @set:JvmName("assignOnlyOnline") var onlyOnline by mutableStateOf(false)
        private set
    @set:JvmName("assignBootStart") var bootStart by mutableStateOf(true)
        private set
    @set:JvmName("assignAppId") var appId by mutableStateOf("")
        private set
    var apps by mutableStateOf(listOf<DiscordApp>())
        private set

    @set:JvmName("assignSchedOn") var schedOn by mutableStateOf(false)
        private set
    @set:JvmName("assignWindows") var windows by mutableStateOf(listOf<Win>())
        private set

    var srcOrder by mutableStateOf(Sources.all)
        private set
    var srcOn by mutableStateOf(mapOf<String, Boolean>())
        private set

    var showApps by mutableStateOf(setOf<String>())
    var ignoreApps by mutableStateOf(setOf<String>())
    var customs by mutableStateOf(mapOf<String, Custom>())

    var themeMode by mutableStateOf("system")
    @set:JvmName("assignOnboarded") var onboarded by mutableStateOf(true)
        private set

    var devMode by mutableStateOf(false)
        private set

    var fallbackStatic by mutableStateOf(false)
        private set

    var permTick by mutableIntStateOf(0)

    init {
        reload()
        sType = Store.get(c, "s_type", "0").toIntOrNull() ?: 0
        sName = Store.get(c, "s_name", "")
        sDetails = Store.get(c, "s_details", "")
        sState = Store.get(c, "s_state", "")
        sTimerMode = Store.get(c, "s_tmode", if (flag("s_timer", false)) "up" else "off")
        sStarted = Store.get(c, "s_started", "0").toLongOrNull() ?: 0L
        sEnd = Store.get(c, "s_end", "0").toLongOrNull() ?: 0L
        sDur = Store.get(c, "s_dur", "60").toIntOrNull() ?: 60
        sLarge = Store.get(c, "s_large", "")
        sSmall = Store.get(c, "s_small", "")
        sLargeText = Store.get(c, "s_large_text", "")
        sB1L = Store.get(c, "s_b1l", "")
        sB1U = Store.get(c, "s_b1u", "")
        sB2L = Store.get(c, "s_b2l", "")
        sB2U = Store.get(c, "s_b2u", "")
        presets = Presets.all(c)
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
        images = flag("show_art", false)
        onlyOnline = flag("only_online", false)
        bootStart = flag("boot_start", true)
        apps = Store.apps(c)
        appId = Store.activeApp(c)?.id.orEmpty()
        schedOn = Schedule.on(c)
        windows = Schedule.windows(c)
        srcOrder = Sources.order(c)
        srcOn = Sources.all.associateWith { Sources.enabled(c, it) }
        showApps = Store.pkgs(c, "show_apps")
        ignoreApps = Store.pkgs(c, "ignore_apps")
        customs = Customs.all(c)
        themeMode = Store.get(c, "theme", "system")
        onboarded = flag("onboarded", false)
        btnVideo = flag("btn_video", true)
        btnRepo = flag("btn_repo", true)
        devMode = flag("dev_mode", false)
        fallbackStatic = flag("static_fallback", false)
    }

    fun setRunning(on: Boolean) {
        running = on
        Presence.setRunning(c, on)
        Toggle.refresh(c)
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

    private fun staticValues(): Map<String, String> = mapOf(
        "s_type" to sType.toString(),
        "s_name" to sName,
        "s_details" to sDetails,
        "s_state" to sState,
        "s_tmode" to sTimerMode,
        "s_started" to sStarted.toString(),
        "s_end" to sEnd.toString(),
        "s_dur" to sDur.toString(),
        "s_large" to sLarge,
        "s_small" to sSmall,
        "s_large_text" to sLargeText,
        "s_b1l" to sB1L.trim(),
        "s_b1u" to sB1U.trim(),
        "s_b2l" to sB2L.trim(),
        "s_b2u" to sB2U.trim()
    )

    fun saveStatic() {
        var changed = false
        staticValues().forEach { (k, v) ->
            if (Store.get(c, k, if (k == "s_dur") "60" else if (k == "s_tmode") "off" else if (k == "s_started" || k == "s_end") "0" else if (k == "s_type") "0" else "") != v) {
                Store.put(c, k, v)
                changed = true
            }
        }
        if (!changed) return
        markSaved()
        PresenceService.kick()
    }

    fun clearStatic() {
        sName = ""
        sDetails = ""
        sState = ""
        sLargeText = ""
        saveStatic()
    }

    fun setTimerMode(m: String) {
        sTimerMode = m
        if (m == "up" && sStarted <= 0L) sStarted = System.currentTimeMillis()
        saveStatic()
    }

    fun resetElapsed() {
        sStarted = System.currentTimeMillis()
        saveStatic()
    }

    fun setStartedAt(hour: Int, minute: Int) {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, minute)
        cal.set(java.util.Calendar.SECOND, 0)
        var t = cal.timeInMillis
        if (t > System.currentTimeMillis()) t -= 24L * 60 * 60 * 1000
        sStarted = t
        saveStatic()
    }

    fun setDuration(min: Int) {
        sDur = min.coerceIn(1, 24 * 60)
        saveStatic()
    }

    fun startCountdown() {
        sEnd = System.currentTimeMillis() + sDur * 60_000L
        sTimerMode = "down"
        saveStatic()
    }

    fun setImage(large: Boolean, name: String) {
        val old = if (large) sLarge else sSmall
        if (large) sLarge = name else sSmall = name
        saveStatic()
        if (old != name) StaticFiles.prune(c, old)
    }

    fun savePreset(label: String) {
        val p = Preset(
            System.currentTimeMillis().toString(), label.trim().ifEmpty { sName.ifEmpty { "Preset" } },
            sType, sName, sDetails, sState, sTimerMode, sDur, sLarge, sSmall, sB1L, sB1U, sB2L, sB2U, sLargeText
        )
        presets = presets + p
        Presets.save(c, presets)
    }

    fun applyPreset(p: Preset) {
        sType = p.type
        sName = p.name
        sDetails = p.details
        sState = p.state
        sDur = p.durMin
        sLarge = p.large
        sSmall = p.small
        sLargeText = p.largeText
        sB1L = p.b1l
        sB1U = p.b1u
        sB2L = p.b2l
        sB2U = p.b2u
        sTimerMode = p.timerMode
        val now = System.currentTimeMillis()
        if (p.timerMode == "up") sStarted = now
        if (p.timerMode == "down") sEnd = now + p.durMin * 60_000L
        saveStatic()
    }

    fun deletePreset(p: Preset) {
        presets = presets.filter { it.id != p.id }
        Presets.save(c, presets)
        StaticFiles.prune(c, p.large)
        StaticFiles.prune(c, p.small)
    }

    fun staticPreview(): Info? {
        val n = sName.trim()
        if (n.isEmpty()) return null
        val now = System.currentTimeMillis()
        val btns = listOf(sB1L.trim() to sB1U.trim(), sB2L.trim() to sB2U.trim())
            .filter { it.first.isNotEmpty() && it.second.startsWith("http") }
        return Info(
            type = sType,
            name = n,
            details = sDetails.trim().ifEmpty { null },
            state = sState.trim().ifEmpty { null },
            start = if (sTimerMode == "up" && sStarted > 0L && sType != 5) sStarted else null,
            end = if (sTimerMode == "down" && sEnd > now && sType != 5) sEnd else null,
            buttons = btns,
            largeFile = sLarge.ifEmpty { null },
            smallFile = sSmall.ifEmpty { null },
            largeText = sLargeText.trim().ifEmpty { null }
        )
    }

    private fun markSaved() {
        savedAt = System.currentTimeMillis()
    }

    fun setSourceOn(k: String, on: Boolean) {
        srcOn = srcOn + (k to on)
        Sources.setEnabled(c, k, on)
        PresenceService.kick()
    }

    fun moveSource(k: String, by: Int) {
        val list = srcOrder.toMutableList()
        val i = list.indexOf(k)
        val j = i + by
        if (i < 0 || j !in list.indices) return
        list.removeAt(i)
        list.add(j, k)
        srcOrder = list
        Sources.setOrder(c, list)
        PresenceService.kick()
    }

    fun setBtnVideo(v: Boolean) { btnVideo = v; put("btn_video", v); PresenceService.kick() }
    fun setBtnRepo(v: Boolean) { btnRepo = v; put("btn_repo", v); PresenceService.kick() }

    fun setAutoApps(v: Boolean) { autoApps = v; put("auto_apps", v); PresenceService.kick() }
    fun setAllApps(v: Boolean) { allApps = v; put("all_apps", v); PresenceService.kick() }
    fun setImages(v: Boolean) { images = v; put("show_art", v); PresenceService.kick() }
    fun setOnlyOnline(v: Boolean) { onlyOnline = v; put("only_online", v); PresenceService.kick() }
    fun setBootStart(v: Boolean) { bootStart = v; put("boot_start", v) }

    fun addApp(id: String, name: String) {
        if (!Art.validId(id)) return
        val list = Store.apps(c)
        if (list.none { it.id == id }) Store.saveApps(c, list + DiscordApp(id, name.trim()))
        setActiveApp(id)
    }

    fun setActiveApp(id: String) {
        Store.put(c, "active_app", id)
        apps = Store.apps(c)
        appId = id
        Art.reset()
        PresenceService.kick()
    }

    fun removeApp(id: String) {
        val rest = Store.apps(c).filter { it.id != id }
        Store.saveApps(c, rest)
        if (appId == id) Store.put(c, "active_app", rest.firstOrNull()?.id.orEmpty())
        apps = rest
        appId = Store.activeApp(c)?.id.orEmpty()
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
    fun switchDevMode(v: Boolean) { devMode = v; put("dev_mode", v) }

    fun switchFallback(v: Boolean) { fallbackStatic = v; put("static_fallback", v); PresenceService.kick() }

    fun setOnboarded(v: Boolean) { onboarded = v; put("onboarded", v) }
}

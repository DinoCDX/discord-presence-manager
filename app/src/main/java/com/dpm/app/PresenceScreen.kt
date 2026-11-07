package com.dpm.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog

@Composable
fun PresenceScreen(ui: UiState, openApps: () -> Unit, scroll: ScrollState = rememberScrollState()) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    val conn by Live.connection.collectAsState()
    var fresh by remember { mutableStateOf<Live.Snapshot?>(null) }
    LaunchedEffect(ui.running, ui.paused, ui.mode, ui.schedOn, ui.onlyOnline, ui.fallbackStatic, ui.netRule, ui.permTick) {
        Live.tick.collect {
            val next = withContext(Dispatchers.IO) { Live.compute(ctx) }
            if (next.signature() != fresh?.signature()) fresh = next
        }
    }
    val shown = fresh ?: Live.Snapshot(null, null, emptyMap(), null)
    val preview = shown.info
    val gate = shown.gate
    val found = shown.found
    val winner = shown.winner
    val rejected = conn.startsWith("Token rejected")
    val timed = preview != null && (preview.start != null || preview.end != null)
    val nowState = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timed, ui.running) {
        while (isActive) {
            nowState.longValue = System.currentTimeMillis()
            delay(if (timed || ui.running) 1_000L else 15_000L)
        }
    }

    LaunchedEffect(ui.sType, ui.sName, ui.sDetails, ui.sState, ui.sB1L, ui.sB1U, ui.sB2L, ui.sB2U) {
        ui.saveStatic()
    }
    DisposableEffect(Unit) {
        onDispose { ui.flushStatic() }
    }
    val live = if (ui.mode == "static") ui.staticPreview() else preview

    val notifOk = remember(ui.permTick) { Perms.notifications(ctx) }
    var askedTick by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askedTick++
        if (!granted && ctx.activity().notificationsBlockedForGood()) ctx.openNotificationSettings()
    }
    val notifGranted = notifOk || askedTick > 0 && Perms.notifications(ctx)

    Column(
        Modifier.verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!notifGranted && Build.VERSION.SDK_INT >= 33) {
            Banner(
                "Notifications are off",
                "The notification shows what is being shared and lets you pause or stop from anywhere.",
                "Allow notifications"
            ) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        if (rejected) {
            Banner(
                "Discord signed you out",
                "Your login is no longer valid, so nothing can be shown. Log in again to continue.",
                "Log in again"
            ) {
                ctx.startActivity(android.content.Intent(ctx, LoginActivity::class.java).putExtra("add", true))
            }
        }
        UpdateBanner()

        HeroCard(ui, ui.running && gate == null && live != null, gate, live != null, Live.plain(conn), nowState)

        SectionCard("Preview") {
            TimedPreview(
                info = live,
                nowState = nowState,
                empty = if (ui.mode == "static") "Add a name below to see a preview" else "Nothing to show right now",
                imagesOn = ui.images,
                hasAppId = ui.appId.isNotBlank()
            )
        }

        SectionCard("Source", Modifier.spatialSize()) {
            Segmented(listOf("Dynamic", "Static"), if (ui.mode == "dynamic") 0 else 1, {
                ui.setMode(if (it == 0) "dynamic" else "static")
            })
            Spacer(Modifier.height(8.dp))
            if (ui.mode == "static") {
                TimedStatic(ui, nowState)
            } else {
                ExpOutlinedButton(onClick = openApps, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage apps")
                }
                ToggleRow(
                    "Use static when nothing is playing",
                    "Your static status shows when no app or media is active",
                    ui.fallbackStatic
                ) { ui.switchFallback(it) }
            }
        }

        if (ui.mode == "dynamic") {
            SourcesCard(ui, found, winner, live = ui.running && gate == null)
        }

        OptionsCard(ui)
    }
}

@Composable
private fun UpdateBanner() {
    val ctx = LocalContext.current
    var dismissedTag by remember { mutableStateOf(Store.get(ctx, "upd_dismissed", "")) }
    var showNotes by remember { mutableStateOf(false) }
    when (val u = Updater.state) {
        is UpdateState.Available -> if (dismissedTag != u.tag) Banner(
            "Update ${u.tag}",
            "A new version of DPM is available.",
            "Download",
            secondary = "Changelog",
            onSecondary = { showNotes = true },
            onDismiss = {
                Store.put(ctx, "upd_dismissed", u.tag)
                dismissedTag = u.tag
            }
        ) { Updater.download(ctx) }
        is UpdateState.Ready -> if (dismissedTag != u.tag) Banner(
            "Update ${u.tag} is ready",
            "It has been downloaded and checked.",
            "Install",
            onDismiss = {
                Store.put(ctx, "upd_dismissed", u.tag)
                dismissedTag = u.tag
            }
        ) { ctx.activity()?.let { Updater.install(it) } }
        else -> {}
    }
    if (showNotes) ChangelogDialog(Updater.latestTag, Updater.latestNotes) { showNotes = false }
}

@Composable
private fun HeroCard(ui: UiState, liveNow: Boolean, gate: String?, hasLive: Boolean, status: String, nowState: androidx.compose.runtime.MutableLongState) {
    val cs = MaterialTheme.colorScheme
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val heroBg by animateColorAsState(if (liveNow) cs.primaryContainer else cs.surfaceContainerHigh, fade, label = "heroBg")
    val heroTitle by animateColorAsState(if (liveNow) cs.onPrimaryContainer else cs.onSurface, fade, label = "heroTitle")
    val heroSub by animateColorAsState(if (liveNow) cs.onPrimaryContainer else cs.onSurfaceVariant, fade, label = "heroSub")
    val motion = rememberInfiniteTransition(label = "hero")
    val pulse by motion.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse"
    )
    val spin by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)),
        label = "spin"
    )
    val sentAt by Live.lastSent.collectAsState()
    val statusText = when {
        !ui.running -> "Off"
        gate != null -> gate
        hasLive -> "Live on Discord"
        else -> "On, nothing to show right now"
    }
    val detail = if (ui.running && gate == null) "$status  \u00B7  ${Live.sentLabel(sentAt, nowState.longValue)}" else null

    Card(
        modifier = Modifier.fillMaxWidth().spatialSize(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = heroBg)
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Decoration sits behind the content and never takes up layout space.
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 64.dp, y = 64.dp)
                        .size(190.dp)
                        .graphicsLayer { rotationZ = if (liveNow) spin else 0f }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(cs.primary.copy(alpha = if (liveNow) 0.14f else 0.07f))
                )
            }
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialShapes.Clover4Leaf.toShape(),
                        color = if (liveNow) cs.primary else cs.secondaryContainer
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_presence),
                            null,
                            tint = if (liveNow) cs.onPrimary else cs.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp).size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Share my presence", style = MaterialTheme.typography.titleLargeEmphasized, color = heroTitle)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (liveNow) {
                                Box(
                                    Modifier
                                        .padding(end = 8.dp)
                                        .size(8.dp)
                                        .graphicsLayer { alpha = pulse }
                                        .clip(CircleShape)
                                        .background(heroTitle)
                                )
                            }
                            Text(statusText, style = MaterialTheme.typography.bodyLarge, color = heroSub)
                        }
                    }
                    Switch(
                        checked = ui.running,
                        onCheckedChange = { ui.setRunning(it) },
                        thumbContent = if (ui.running) {
                            { Icon(Icons.Default.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                        } else {
                            null
                        }
                    )
                }
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = heroSub,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                AnimatedVisibility(ui.running) {
                    PauseControl(ui)
                }
            }
        }
    }
}

@Composable
private fun OptionsCard(ui: UiState) {
    SectionCard("Options", Modifier.spatialSize()) {
        ToggleRow(
            "Only at set times",
            if (ui.schedOn) "Outside these times nothing is shown" else "Shared whenever it is on",
            ui.schedOn
        ) { ui.setSchedOn(it) }
        AnimatedVisibility(ui.schedOn) {
            ScheduleList(ui)
        }
        ToggleRow(
            "Only when Discord status is Online",
            "Nothing is shown while you are Idle, Do Not Disturb or Invisible",
            ui.onlyOnline
        ) { ui.setOnlyOnline(it) }
        Text(
            "Share on",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp)
        )
        Spacer(Modifier.height(6.dp))
        val rules = listOf(Net.ANY, Net.WIFI, Net.MOBILE)
        Segmented(
            listOf("Any", "Wi-Fi", "Mobile data"),
            rules.indexOf(ui.netRule).coerceAtLeast(0),
            { ui.setNetRule(rules[it]) }
        )
    }
}

@Composable
private fun PauseControl(ui: UiState) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var clockDialog by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (ui.paused) {
            ExpTonalButton(onClick = { ui.setPaused(false) }, modifier = Modifier.fillMaxWidth()) { Text("Resume") }
        } else {
            ExpTonalButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("Pause") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(
                    "For 30 minutes" to 30 * 60_000L,
                    "For 1 hour" to 60 * 60_000L,
                    "Until I resume" to 0L
                ).forEach { (label, ms) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            menu = false
                            ui.pauseFor(ms)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Until a time...") },
                    onClick = {
                        menu = false
                        clockDialog = true
                    }
                )
            }
        }
    }
    if (clockDialog) {
        val now = Calendar.getInstance()
        val state = rememberTimePickerState(
            initialHour = now.get(Calendar.HOUR_OF_DAY),
            initialMinute = now.get(Calendar.MINUTE),
            is24Hour = android.text.format.DateFormat.is24HourFormat(ctx)
        )
        AlertDialog(
            onDismissRequest = { clockDialog = false },
            title = { Text("Pause until") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    clockDialog = false
                    ui.pauseFor(msUntil(state.hour, state.minute))
                }) { Text("Pause") }
            },
            dismissButton = {
                TextButton(onClick = { clockDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun msUntil(hour: Int, minute: Int): Long {
    val now = System.currentTimeMillis()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (target.timeInMillis <= now) target.add(Calendar.DAY_OF_MONTH, 1)
    return target.timeInMillis - now
}

@Composable
private fun ScheduleList(ui: UiState) {
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column {
        if (ui.windows.isEmpty()) {
            Text(
                "No times yet, so nothing will be shared.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.error
            )
        }
        ui.windows.forEachIndexed { i, w ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Schedule.daysLabel(w.days), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${Schedule.clock(w.start)} to ${Schedule.clock(w.end)}" +
                            if (w.end < w.start) " (next day)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = { editing = i }) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = { ui.setWindows(ui.windows.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Default.Close, "Remove")
                }
            }
        }
        ExpTextButton(onClick = { adding = true }) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add time window")
        }
    }
    if (adding) {
        WindowDialog(null, { adding = false }) { w ->
            ui.setWindows(ui.windows + w)
            adding = false
        }
    }
    editing?.let { idx ->
        ui.windows.getOrNull(idx)?.let { cur ->
            WindowDialog(cur, { editing = null }) { w ->
                ui.setWindows(ui.windows.mapIndexed { j, o -> if (j == idx) w else o })
                editing = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowDialog(initial: Win?, onDismiss: () -> Unit, onSave: (Win) -> Unit) {
    var days by remember { mutableIntStateOf(initial?.days ?: 31) }
    val s0 = initial?.start ?: (9 * 60)
    val e0 = initial?.end ?: (17 * 60)
    val startState = rememberTimePickerState(s0 / 60, s0 % 60, true)
    val endState = rememberTimePickerState(e0 / 60, e0 % 60, true)
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLargeIncreased,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (initial == null) "Add time window" else "Edit time window",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    names.forEachIndexed { i, n ->
                        FilterChip(
                            selected = days shr i and 1 == 1,
                            onClick = { days = days xor (1 shl i) },
                            label = { Text(n) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Starts", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                TimeInput(startState)
                Spacer(Modifier.height(8.dp))
                Text("Ends", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                TimeInput(endState)
                Text(
                    "If it ends before it starts, it runs into the next day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ExpTextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    ExpButton(
                        enabled = days != 0,
                        onClick = {
                            onSave(Win(days, startState.hour * 60 + startState.minute, endState.hour * 60 + endState.minute))
                        }
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun SentLabel(nowState: androidx.compose.runtime.MutableLongState, color: Color) {
    val sentAt by Live.lastSent.collectAsState()
    Text(
        Live.sentLabel(sentAt, nowState.longValue),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun TimedPreview(
    info: Info?,
    nowState: androidx.compose.runtime.MutableLongState,
    empty: String,
    imagesOn: Boolean,
    hasAppId: Boolean
) {
    PresenceCard(info = info, empty = empty, imagesOn = imagesOn, hasAppId = hasAppId, nowState = nowState)
}

@Composable
private fun TimedStatic(ui: UiState, nowState: androidx.compose.runtime.MutableLongState) {
    StaticEditor(ui, nowState)
}

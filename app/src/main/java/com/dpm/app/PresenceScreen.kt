package com.dpm.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun PresenceScreen(ui: UiState, openApps: () -> Unit, scroll: ScrollState = rememberScrollState()) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var preview by remember { mutableStateOf<Info?>(null) }
    var gate by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Map<String, Info?>>(emptyMap()) }
    var winner by remember { mutableStateOf<String?>(null) }
    var rejected by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val p = withContext(Dispatchers.IO) { Logic.info(ctx) }
                preview = p
                found = Dynamic.found
                winner = Dynamic.winner
                rejected = GatewayClient.tokenRejected
                gate = Logic.gate(ctx)
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    LaunchedEffect(ui.sType, ui.sName, ui.sDetails, ui.sState, ui.sB1L, ui.sB1U, ui.sB2L, ui.sB2U) {
        delay(400)
        ui.saveStatic()
    }
    DisposableEffect(Unit) {
        onDispose { ui.saveStatic() }
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
        Modifier.verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Gap14
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
                ctx.startActivity(
                    android.content.Intent(ctx, LoginActivity::class.java).putExtra("add", true)
                )
            }
        }

        var dismissedTag by remember { mutableStateOf(Store.get(ctx, "upd_dismissed", "")) }
        when (val u = Updater.state) {
            is UpdateState.Available -> if (dismissedTag != u.tag) Banner(
                "Update ${u.tag}",
                "A new version of DPM is available.",
                "Download",
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

        val liveNow = ui.running && gate == null && live != null
        val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
        val heroBg by animateColorAsState(if (liveNow) cs.primaryContainer else cs.surfaceContainer, fade, label = "heroBg")
        val heroTitle by animateColorAsState(if (liveNow) cs.onPrimaryContainer else cs.onSurface, fade, label = "heroTitle")
        val heroSub by animateColorAsState(if (liveNow) cs.onPrimaryContainer else cs.onSurfaceVariant, fade, label = "heroSub")
        Card(
            modifier = Modifier.fillMaxWidth().spatialSize(),
            shape = MaterialTheme.shapes.extraLargeIncreased,
            colors = CardDefaults.cardColors(containerColor = heroBg)
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Share my presence", style = MaterialTheme.typography.titleLargeEmphasized, color = heroTitle)
                        Text(
                            when {
                                !ui.running -> "Off"
                                gate != null -> gate!!
                                live != null -> "Live on Discord"
                                else -> "On, nothing to show right now"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = heroSub
                        )
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
                AnimatedVisibility(ui.running) {
                    ExpTonalButton(
                        onClick = { ui.setPaused(!ui.paused) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) { Text(if (ui.paused) "Resume" else "Pause") }
                }
            }
        }

        SectionCard("What Discord shows") {
            PresenceCard(
                info = live,
                now = now,
                empty = if (ui.mode == "static") "Add a name below to see a preview" else "Nothing to show right now",
                imagesOn = ui.images,
                hasAppId = ui.appId.isNotBlank()
            )
        }

        SectionCard("Source", Modifier.spatialSize()) {
            Segmented(listOf("Dynamic", "Static"), if (ui.mode == "dynamic") 0 else 1, {
                ui.setMode(if (it == 0) "dynamic" else "static")
            })
            Spacer(Modifier.height(12.dp))
            if (ui.mode == "static") {
                StaticEditor(ui, now)
            } else {
                Text(
                    "Follows what you are doing. Pick apps in the Apps tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                ToggleRow("Show images", "App icon and cover art", ui.images) { ui.setImages(it) }
                ExpOutlinedButton(onClick = openApps, modifier = Modifier.fillMaxWidth()) { Text("Manage apps") }
            }
        }

        if (ui.mode == "dynamic") {
            SourcesCard(ui, found, winner, live = ui.running && gate == null)
        }

        SectionCard("Schedule", Modifier.spatialSize()) {
            ToggleRow(
                "Only share at set times",
                if (ui.schedOn) "Outside these times nothing is shown" else "Presence is shared whenever it is on",
                ui.schedOn
            ) { ui.setSchedOn(it) }
            AnimatedVisibility(ui.schedOn) {
                Column {
                    if (ui.windows.isEmpty()) {
                        Text(
                            "No times yet, so nothing will be shared. Add one below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.error
                        )
                    }
                    var editing by remember { mutableStateOf<Int?>(null) }
                    var adding by remember { mutableStateOf(false) }
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
            }
        }

        SectionCard("Options") {
            ToggleRow(
                "Only when my Discord status is Online",
                "Nothing is shown while your status is Idle, Do Not Disturb or Invisible",
                ui.onlyOnline
            ) { ui.setOnlyOnline(it) }
        }
        Spacer(Modifier.height(8.dp))
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

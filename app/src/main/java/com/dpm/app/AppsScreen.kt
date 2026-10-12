package com.dpm.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private class AppEntry(val pkg: String, val label: String, val category: Int)

private const val LIMIT = 128

private fun ruleShown(a: AppEntry, ui: UiState): Boolean =
    a.category == ApplicationInfo.CATEGORY_GAME ||
        (ui.autoApps && Apps.autoShow(a.pkg, a.category)) ||
        ui.allApps

private fun shownNow(a: AppEntry, ui: UiState): Boolean =
    when (ui.appMode(a.pkg)) {
        1 -> true
        2 -> false
        else -> ruleShown(a, ui)
    }

private fun sameCustom(x: Custom?, y: Custom?): Boolean =
    if (x == null || y == null) {
        x == null && y == null
    } else {
        x.type == y.type && x.name == y.name && x.details == y.details && x.state == y.state
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(ui: UiState, listState: LazyListState = rememberLazyListState()) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    val apps by produceState(listOf<AppEntry>()) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(q, 0)
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.activityInfo.applicationInfo.category) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }

    val popular = remember(apps) { apps.filter { Apps.autoShow(it.pkg, it.category) }.map { it.pkg } }
    val everyone = remember(apps) { apps.map { it.pkg } }

    LaunchedEffect(apps) {
        if (apps.isNotEmpty() && Store.get(ctx, "popular_seeded", "0") != "1") {
            Store.put(ctx, "popular_seeded", "1")
            if (ui.autoApps) ui.allowApps(popular, true)
        }
    }

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<AppEntry?>(null) }

    val usageOk = remember(ui.permTick) { Perms.usage(ctx) }
    val listenerOk = remember(ui.permTick) { Perms.listener(ctx) }

    val shown = apps.filter { a ->
        (query.isBlank() || a.label.contains(query, true) || a.pkg.contains(query, true)) &&
            when (filter) {
                1 -> ui.appMode(a.pkg) == 1
                2 -> ui.appMode(a.pkg) == 2
                3 -> a.pkg in ui.customs
                else -> true
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("Apps", "Choose what Discord can show") }

        if (!usageOk) {
            item {
                Banner(
                    "Usage access needed",
                    "Lets the app see which app or game is on screen.",
                    "Open settings"
                ) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }
        }
        if (!listenerOk) {
            item {
                Banner(
                    "Notification access needed",
                    "Lets the app read what music or video is playing.",
                    "Open settings"
                ) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
        }

        item {
            SectionCard {
                ToggleRow(
                    "Allow popular apps",
                    if (popular.isEmpty()) "YouTube, Instagram, TikTok and similar" else "${popular.size} installed, like YouTube, Instagram and TikTok",
                    ui.autoApps
                ) { on ->
                    ui.setAutoApps(on)
                    ui.allowApps(popular, on, if (ui.allApps) popular.toSet() else emptySet())
                }
                ToggleRow(
                    "Allow all other apps",
                    if (everyone.isEmpty()) "Every app you open can show up" else "${everyone.size} installed. Anything you ignore stays hidden",
                    ui.allApps
                ) { on ->
                    ui.setAllApps(on)
                    ui.allowApps(everyone, on, if (ui.autoApps) popular.toSet() else emptySet())
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Allowed", "Ignored", "Custom").forEachIndexed { i, n ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(n) })
                }
            }
        }

        if (apps.isEmpty()) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp)) }
        } else if (shown.isEmpty()) {
            item { Text("No apps match", color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        }

        items(shown, key = { it.pkg }) { a ->
            val mode = ui.appMode(a.pkg)
            val custom = a.pkg in ui.customs
            val sub = when (mode) {
                1 -> "Allowed"
                2 -> "Ignored"
                else -> when {
                    a.category == ApplicationInfo.CATEGORY_GAME || (ui.autoApps && Apps.autoShow(a.pkg, a.category)) ->
                        "Shown automatically"
                    ui.allApps -> "Shown (all apps)"
                    else -> "Hidden by default"
                }
            } + if (custom) " \u00B7 custom text" else ""
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = a }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(a.pkg, 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (mode == 2) cs.error else cs.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = shownNow(a, ui),
                    onCheckedChange = { on ->
                        ui.setAppMode(a.pkg, if (on) 1 else if (ruleShown(a, ui)) 2 else 0)
                    }
                )
            }
        }
    }

    open?.let { a ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = null }, sheetState = sheet) {
            AppSheet(a, ui)
        }
    }
}

@Composable
private fun AppSheet(a: AppEntry, ui: UiState) {
    val cs = MaterialTheme.colorScheme
    val existing = ui.customs[a.pkg]
    var mode by remember { mutableIntStateOf(ui.appMode(a.pkg)) }
    var customOn by remember { mutableStateOf(existing != null) }
    var type by remember { mutableIntStateOf(existing?.type ?: Apps.typeFor(a.pkg, a.category)) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var details by remember { mutableStateOf(existing?.details.orEmpty()) }
    var state by remember { mutableStateOf(existing?.state.orEmpty()) }

    val born = remember { System.currentTimeMillis() }
    var now by remember { mutableLongStateOf(born) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val custom = if (customOn) {
        Custom(type, name.trim().ifEmpty { null }, details.trim().ifEmpty { null }, state.trim().ifEmpty { null })
    } else {
        null
    }

    val commit: () -> Unit = {
        if (mode != ui.appMode(a.pkg)) ui.setAppMode(a.pkg, mode)
        if (!sameCustom(custom, ui.customs[a.pkg])) ui.setCustom(a.pkg, custom)
    }
    val latest = rememberUpdatedState(commit)
    LaunchedEffect(mode, customOn, type, name, details, state) {
        delay(300)
        commit()
    }
    DisposableEffect(Unit) {
        onDispose { latest.value.invoke() }
    }

    val willShow = when (mode) {
        1 -> true
        2 -> false
        else -> ruleShown(a, ui)
    }
    val effType = custom?.type ?: Apps.typeFor(a.pkg, a.category)
    val sample = custom == null && (effType == 2 || effType == 3)
    val sampleStart = born - 72_000L
    val info = if (willShow) {
        Info(
            type = effType,
            name = custom?.name ?: a.label,
            details = custom?.details ?: Apps.def(a.pkg)?.details
                ?: if (sample) (if (effType == 2) "Song title" else "Video title") else null,
            state = custom?.state ?: if (sample && effType == 2) "Artist" else null,
            album = if (sample && effType == 2) "Album" else null,
            start = if (sample) sampleStart else born,
            end = if (sample) sampleStart + 215_000L else null,
            pkg = a.pkg
        )
    } else {
        null
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(a.pkg, 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(a.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SavedTag(ui, now)
        }

        Spacer(Modifier.height(16.dp))
        SectionCard("Preview") {
            PresenceCard(
                info = info,
                now = now,
                empty = if (mode == 2) "Ignored. This app is never shown." else "Hidden by default. Set it to Allowed to show it.",
                imagesOn = ui.images,
                hasAppId = ui.appId.isNotBlank()
            )
            if (sample && willShow) {
                Text(
                    "While it plays, the real title and artist are used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Show this app", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        Spacer(Modifier.height(8.dp))
        Segmented(listOf("Auto", "Allowed", "Ignored"), mode, { mode = it })
        Text(
            when (mode) {
                1 -> "Shown whenever it is on screen."
                2 -> "Never shown, even if it is playing media."
                else -> "Follows the automatic rules and the switches on the Apps tab."
            },
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(16.dp))
        ToggleRow("Custom presence", "Your own text while this app is on screen", customOn) {
            customOn = it
            if (it && mode == 0) mode = 1
        }
        AnimatedVisibility(customOn) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TypeChips(type) { type = it }
                Field("Name", name, hint = "Leave empty to use ${a.label}") { name = it.take(LIMIT) }
                Field("Details", details) { details = it.take(LIMIT) }
                Field("State", state, last = true) { state = it.take(LIMIT) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

package com.dpm.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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

private fun groupShape(index: Int, last: Int): RoundedCornerShape {
    val edge = 24.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) edge else inner,
        topEnd = if (index == 0) edge else inner,
        bottomStart = if (index == last) edge else inner,
        bottomEnd = if (index == last) edge else inner
    )
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
    var searching by remember { mutableStateOf(false) }
    var popularOpen by remember { mutableStateOf(false) }
    val popularEntries = remember(apps) { apps.filter { Apps.autoShow(it.pkg, it.category) } }
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }
    LaunchedEffect(searching) {
        if (searching) listState.animateScrollToItem(1)
    }

    val usageOk = remember(ui.permTick) { Perms.usage(ctx) }
    val listenerOk = remember(ui.permTick) { Perms.listener(ctx) }

    val shownCount = apps.count { shownNow(it, ui) }
    val hiddenCount = apps.size - shownCount
    val customCount = apps.count { it.pkg in ui.customs }

    fun matchesQuery(a: AppEntry) = query.isBlank() || a.label.contains(query, true) || a.pkg.contains(query, true)
    fun matchesFilter(a: AppEntry) = filter != 3 || a.pkg in ui.customs

    val shownGroup = apps.filter { matchesQuery(it) && matchesFilter(it) && shownNow(it, ui) }
    val hiddenGroup = apps.filter { matchesQuery(it) && matchesFilter(it) && !shownNow(it, ui) }

    Column(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "header") {
            Column {
                if (!usageOk) {
                    Banner(
                        "Usage access needed",
                        "Lets the app see which app or game is on screen.",
                        "Open settings"
                    ) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    Spacer(Modifier.height(8.dp))
                }
                if (!listenerOk) {
                    Banner(
                        "Notification access needed",
                        "Lets the app read what music or video is playing.",
                        "Open settings"
                    ) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    Spacer(Modifier.height(8.dp))
                }
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (apps.isEmpty()) "Loading apps" else "$shownCount shown",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = cs.primary
                        )
                        if (apps.isNotEmpty()) {
                            Text(
                                "  ·  $hiddenCount hidden",
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { popularOpen = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Popular apps", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (popularEntries.isEmpty()) "None installed. Tap to see the list."
                                else "${popularEntries.size} installed. Tap to choose which ones.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = ui.autoApps,
                            onCheckedChange = { on ->
                                ui.setAutoApps(on)
                                ui.allowApps(popular, on, if (ui.allApps) popular.toSet() else emptySet())
                            }
                        )
                    }
                    ToggleRow(
                        "Every other app",
                        "Any app you open can show up. Switch off the ones you don't want.",
                        ui.allApps
                    ) { on ->
                        ui.setAllApps(on)
                        ui.allowApps(everyone, on, if (ui.autoApps) popular.toSet() else emptySet())
                    }
                }
                if (!searching) {
                    Spacer(Modifier.height(8.dp))
                    SearchPill { searching = true }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "All" to apps.size,
                    "Shown" to shownCount,
                    "Hidden" to hiddenCount,
                    "Custom" to customCount
                ).forEachIndexed { i, (label, count) ->
                    FilterChip(
                        selected = filter == i,
                        onClick = { filter = i },
                        label = { Text(if (apps.isEmpty()) label else "$label $count") },
                        leadingIcon = if (filter == i) {
                            { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        },
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }
        }

        if (apps.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        } else if (shownGroup.isEmpty() && hiddenGroup.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        if (query.isBlank()) "No apps in this view" else "Nothing matches \"$query\"",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = cs.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Try another filter, or clear the search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (shownGroup.isNotEmpty()) {
            item { GroupHeader("Shown on Discord", shownGroup.size) }
            appRows(shownGroup, ui, cs) { open = it }
        }
        if (hiddenGroup.isNotEmpty()) {
            item { GroupHeader("Hidden", hiddenGroup.size) }
            appRows(hiddenGroup, ui, cs) { open = it }
        }
    }
    AnimatedVisibility(visible = searching) {
        SearchBar(
            query = query,
            onQuery = { query = it },
            onDone = {
                searching = false
                query = ""
            }
        )
    }
    }

    if (popularOpen) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { popularOpen = false }, sheetState = sheet) {
            PopularSheet(popularEntries, ui)
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
private fun GroupHeader(title: String, count: Int) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = cs.primary)
        Spacer(Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
    }
}

private fun LazyListScope.appRows(
    list: List<AppEntry>,
    ui: UiState,
    cs: androidx.compose.material3.ColorScheme,
    onOpen: (AppEntry) -> Unit
) {
    itemsIndexed(list, key = { _, a -> a.pkg }) { index, a ->
        val mode = ui.appMode(a.pkg)
        val custom = a.pkg in ui.customs
        val on = shownNow(a, ui)
        val auto = a.category == ApplicationInfo.CATEGORY_GAME || (ui.autoApps && Apps.autoShow(a.pkg, a.category))
        val reason = when {
            mode == 2 -> "Ignored, never shown"
            mode == 1 -> "Always shown"
            on && auto -> "Shown automatically"
            on -> "Shown with \"Every other app\""
            else -> "Hidden by default"
        } + if (custom) " · custom text" else ""

        Row(
            Modifier
                .fillMaxWidth()
                .clip(groupShape(index, list.lastIndex))
                .background(cs.surfaceContainer)
                .clickable { onOpen(a) }
                .padding(vertical = 10.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(a.pkg, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(a.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mode == 2) cs.error else cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = on,
                onCheckedChange = { v ->

                    ui.setAppMode(a.pkg, if (v) 1 else if (ruleShown(a, ui)) 2 else 0)
                },
                thumbContent = if (on) {
                    { Icon(Icons.Default.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                }
            )
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
                Text(a.label, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SavedTag(ui, now)
        }

        Spacer(Modifier.height(16.dp))
        SectionCard("Preview") {
            PresenceCard(
                info = info,
                now = now,
                empty = if (mode == 2) "Ignored. This app is never shown." else "Hidden right now. Choose Always to show it.",
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
        Text("When to show it", style = MaterialTheme.typography.titleMediumEmphasized, color = cs.primary)
        Spacer(Modifier.height(8.dp))
        Segmented(listOf("Automatic", "Always", "Never"), mode, { mode = it })
        Text(
            when (mode) {
                1 -> "Shown whenever it is on screen, even if the popular or every-app switches are off."
                2 -> "Never shown, even if it is playing media."
                else -> "Follows the switches on the Apps screen."
            },
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(16.dp))
        ToggleRow("Custom text", "Your own name, details and state while this app is on screen", customOn) {
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

@Composable
private fun SearchPill(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text("Search apps", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear") }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedContainerColor = cs.surfaceContainerHigh,
                unfocusedContainerColor = cs.surfaceContainerHigh
            ),
            modifier = Modifier.weight(1f).focusRequester(focus)
        )
        Spacer(Modifier.width(8.dp))
        ExpTextButton(onClick = onDone) { Text("Done") }
    }
}

@Composable
private fun PopularSheet(entries: List<AppEntry>, ui: UiState) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text("Popular apps", style = MaterialTheme.typography.titleLargeEmphasized)
        Text(
            "These show up while they're on screen. Turn off any you don't want.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        if (entries.isEmpty()) {
            Text(
                "None of the popular apps are installed on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        }
        entries.forEach { a ->
            val on = shownNow(a, ui)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(a.pkg, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (on) "Shown" else "Turned off",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = { v -> ui.setAppMode(a.pkg, if (v) 1 else if (ruleShown(a, ui)) 2 else 0) }
                )
            }
        }
    }
}

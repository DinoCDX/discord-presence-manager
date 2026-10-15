package com.dpm.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    ui: UiState,
    onSwitch: (Account) -> Unit,
    onRemove: (Account) -> Unit,
    onAdd: () -> Unit,
    onReplayIntro: () -> Unit,
    scroll: ScrollState = rememberScrollState()
) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<Account?>(null) }

    Column(
        Modifier.verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Gap14
    ) {
        AccountsCard(ui, onSwitch, onAdd) { confirm = it }
        AppearanceCard(ui)
        PresenceSettingsCard(ui)
        PicturesCard(ui)
        UpdatesCard()
        AboutCard(ui, onReplayIntro)
        if (ui.devMode) DeveloperSection(ui)
        Spacer(Modifier.height(8.dp))
    }

    confirm?.let { a ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Remove ${a.name}?") },
            text = { Text("This only removes the account from this device.") },
            confirmButton = { ExpTextButton(onClick = { confirm = null; onRemove(a) }) { Text("Remove") } },
            dismissButton = { ExpTextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AccountsCard(ui: UiState, onSwitch: (Account) -> Unit, onAdd: () -> Unit, onRemoveRequest: (Account) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val active = ui.accounts.firstOrNull { it.id == ui.activeId }
    SectionCard("Accounts") {
        ui.accounts.forEach { a ->
            val isActive = a.id == ui.activeId
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !isActive) { onSwitch(a) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(a.name, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (isActive) "Active" else "Tap to switch",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) cs.primary else cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onRemoveRequest(a) }) { Icon(Icons.Default.Close, "Remove ${a.name}") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpTonalButton(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("Add account") }
            if (active != null) {
                ExpOutlinedButton(onClick = { onRemoveRequest(active) }, modifier = Modifier.weight(1f)) {
                    Text("Log out")
                }
            }
        }
    }
}

@Composable
private fun AppearanceCard(ui: UiState) {
    val modes = listOf("system", "light", "dark")
    SectionCard("Appearance") {
        Segmented(
            listOf("System", "Light", "Dark"),
            modes.indexOf(ui.themeMode).coerceAtLeast(0),
            onSelect = { ui.setTheme(modes[it]) }
        )
    }
}

@Composable
private fun PresenceSettingsCard(ui: UiState) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    SectionCard("Presence") {
        ToggleRow("YouTube button", "Opens a video link or search", ui.btnVideo) { ui.setBtnVideo(it) }
        ToggleRow("Get DPM button", "Links to the project page", ui.btnRepo) { ui.setBtnRepo(it) }
        ToggleRow("Start when the phone restarts", "Presence comes back on its own if it was on", ui.bootStart) { ui.setBootStart(it) }
        val batteryOk = remember(ui.permTick) { Perms.battery(ctx) }
        if (!batteryOk) {
            Text(
                "Android may stop DPM in the background. Allow background activity to keep presence running.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            ExpTonalButton(
                onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Allow background activity") }
        }
    }
}

@Composable
private fun PicturesCard(ui: UiState) {
    SectionCard("Pictures") {
        ToggleRow(
            "Send pictures",
            "Album art and video thumbnails from public links. Otherwise the DPM icon.",
            ui.images
        ) { ui.setImages(it) }
    }
}

@Composable
private fun UpdatesCard() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var auto by remember { mutableStateOf(Store.get(ctx, "upd_auto", "1") == "1") }
    val st = Updater.state
    val summary = when (st) {
        is UpdateState.Idle -> "Installed ${Updater.installedVersion(ctx)}"
        is UpdateState.Checking -> "Checking GitHub"
        is UpdateState.UpToDate -> "Up to date, ${Updater.installedVersion(ctx)}"
        is UpdateState.Available -> "Version ${st.tag} is available"
        is UpdateState.Downloading -> "Downloading ${st.tag}"
        is UpdateState.Ready -> "Version ${st.tag} is ready to install"
        is UpdateState.Failed -> st.message
    }
    SectionCard("Updates") {
        Text(
            summary,
            style = MaterialTheme.typography.bodyMedium,
            color = if (st is UpdateState.Failed) cs.error else cs.onSurfaceVariant
        )
        when (st) {
            is UpdateState.Checking -> LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            is UpdateState.Downloading -> LinearWavyProgressIndicator(
                progress = { if (st.total > 0L) (st.done.toFloat() / st.total).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            is UpdateState.Available -> Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpButton(onClick = { Updater.download(ctx) }) { Text("Download") }
                ExpOutlinedButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(st.page.ifEmpty { Updater.REPO_URL })))
                }) { Text("Release page") }
            }
            is UpdateState.Ready -> ExpButton(
                onClick = { ctx.activity()?.let { Updater.install(it) } },
                modifier = Modifier.padding(top = 12.dp)
            ) { Text("Install") }
            else -> ExpTonalButton(onClick = { Updater.check(ctx) }, modifier = Modifier.padding(top = 12.dp)) {
                Text("Check for updates")
            }
        }
        ToggleRow("Check automatically", "At most once every 12 hours", auto) {
            auto = it
            Store.put(ctx, "upd_auto", if (it) "1" else "0")
        }
    }
}

private const val DOCS_URL = "https://dinocdx.github.io/discord-presence-manager/"

@Composable
private fun AboutCard(ui: UiState, onReplayIntro: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
    var taps by remember { mutableIntStateOf(0) }
    SectionCard("About", Modifier.clickable {
        if (ui.devMode) return@clickable
        taps++
        if (taps >= 7) {
            taps = 0
            ui.switchDevMode(true)
            ctx.toast("Developer mode is on")
        }
    }) {
        Text("DPM $version", style = MaterialTheme.typography.titleMediumEmphasized)
        Text(
            "Made by DinoCDX. Open source, and bug reports and pull requests are welcome.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            "DPM is not affiliated with Discord.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpTonalButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOCS_URL))) }) {
                Text("Docs")
            }
            ExpOutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.REPO_URL))) }) {
                Text("Source code")
            }
            ExpTextButton(onClick = onReplayIntro) { Text("Intro") }
        }
    }
}

@Composable
private fun DeveloperSection(ui: UiState) {
    val cs = MaterialTheme.colorScheme
    Text(
        "Developer",
        style = MaterialTheme.typography.titleLargeEmphasized,
        color = cs.primary,
        modifier = Modifier.padding(top = 12.dp)
    )
    DevApps(ui)
    DevTest()
    DevLogs()
    ExpTextButton(onClick = { ui.switchDevMode(false) }) { Text("Turn off developer mode") }
}

@Composable
private fun DevApps(ui: UiState) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var newName by remember { mutableStateOf("") }
    var newId by remember { mutableStateOf("") }
    SectionCard("Your own Discord app (optional)") {
        Text(
            "DPM uses its own Discord app by default, so most people don't need this. Add your own only if you want presence to show under your app.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        ui.apps.forEach { app ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.name.ifBlank { "Discord app" } + "\n" + app.id,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (app.id == ui.appId) {
                    Text("Active", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                } else {
                    ExpTextButton(onClick = { ui.setActiveApp(app.id) }) { Text("Use") }
                }
                ExpTextButton(onClick = { ui.removeApp(app.id) }) { Text("Remove") }
            }
        }
        Field("Name (optional)", newName, Modifier.padding(top = 12.dp)) { newName = it.take(40) }
        Field("Application ID", newId, Modifier.padding(top = 8.dp)) { v -> newId = v.filter { it.isDigit() }.take(20) }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpTonalButton(enabled = Art.validId(newId), onClick = {
                ui.addApp(newId, newName)
                newId = ""
                newName = ""
            }) { Text("Add app") }
        }
    }
}

@Composable
private fun DevTest() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(ExternalTest.DEFAULT_URL) }
    var result by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    SectionCard("Test presence with an image") {
        Text(
            "Sends one test presence through the application your presence uses, with the image from this link. Pause presence first so DPM doesn't replace it.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        Field("Image URL", url, Modifier.padding(top = 8.dp)) { url = it.trim() }
        ExpTonalButton(
            enabled = !running && url.startsWith("https://"),
            onClick = {
                running = true
                scope.launch {
                    result = withContext(Dispatchers.IO) { ExternalTest.run(ctx, url) }
                    running = false
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) { Text(if (running) "Sending" else "Send test") }
        if (result.isNotBlank()) {
            Text(result, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun DevLogs() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var logText by remember { mutableStateOf(Logs.text()) }
    LaunchedEffect(Unit) {
        while (true) {
            logText = Logs.text()
            delay(1000)
        }
    }
    SectionCard("Logs") {
        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            SelectionContainer {
                Text(logText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            ExpTonalButton(
                onClick = {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DPM logs", Logs.text()))
                    ctx.toast("Logs copied")
                },
                modifier = Modifier.weight(1f)
            ) { Text("Copy") }
            ExpOutlinedButton(
                onClick = {
                    Logs.clear()
                    logText = Logs.text()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Clear") }
        }
    }
}

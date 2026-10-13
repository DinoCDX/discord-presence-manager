package com.dpm.app

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
    onCopyToken: (String) -> Unit,
    onReplayIntro: () -> Unit,
    scroll: ScrollState = rememberScrollState()
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<Account?>(null) }

    Column(
        Modifier.verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Gap14
    ) {
        SectionCard("Accounts") {
            ui.accounts.forEach { a ->
                val active = a.id == ui.activeId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !active) { onSwitch(a) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(a.name)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (active) "Active" else "Tap to switch",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active) cs.primary else cs.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { confirm = a }) { Icon(Icons.Default.Close, "Remove ${a.name}") }
                }
            }
            Spacer(Modifier.height(8.dp))
            ExpTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add account") }
        }

        SectionCard("Appearance") {
            Segmented(
                listOf("System", "Light", "Dark"),
                listOf("system", "light", "dark").indexOf(ui.themeMode).coerceAtLeast(0),
                onSelect = { ui.setTheme(listOf("system", "light", "dark")[it]) }
            )
        }

        SectionCard("Buttons on Discord") {
            ToggleRow(
                "YouTube button",
                "Video link or YouTube search",
                ui.btnVideo
            ) { ui.setBtnVideo(it) }
            ToggleRow(
                "Get DPM button",
                "Link to the GitHub page",
                ui.btnRepo
            ) { ui.setBtnRepo(it) }
        }

        SectionCard("Images") {
            ToggleRow("Send images", "App icon and cover art", ui.images) { ui.setImages(it) }
            Text(
                "Discord can't take pictures directly, so the app uploads them once to a Discord application of yours " +
                    "and refers to them by ID. To set one up: open the developer portal, create a New Application, " +
                    "and paste its Application ID here.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Field(
                "Application ID",
                ui.appId,
                Modifier.padding(top = 12.dp)
            ) { v -> ui.setAppId(v.filter { it.isDigit() }.take(25)) }

            var status by remember { mutableStateOf(Art.status) }
            var checking by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                while (true) {
                    status = Art.status
                    delay(2000)
                }
            }
            if (status.isNotBlank()) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.startsWith("Connected")) cs.primary else cs.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpOutlinedButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/developers/applications")))
                }) { Text("Developer portal") }
                ExpTonalButton(enabled = !checking && ui.appId.isNotBlank(), onClick = {
                    checking = true
                    scope.launch {
                        status = withContext(Dispatchers.IO) { Art.check(ctx) }
                        checking = false
                    }
                }) { Text(if (checking) "Checking" else "Check") }
            }
        }

        SectionCard("Background") {
            ToggleRow(
                "Start when the phone restarts",
                "Presence comes back on its own if it was on",
                ui.bootStart
            ) { ui.setBootStart(it) }
            val batteryOk = remember(ui.permTick) { Perms.battery(ctx) }
            Text(
                if (batteryOk) "Battery optimization is off for this app, so Android should not stop it."
                else "Android may stop the app in the background to save battery. Allowing background activity keeps presence running.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            if (!batteryOk) {
                ExpButton(
                    onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Allow background activity") }
            }
            Text(
                "Some phones (Xiaomi, Oppo, Vivo, Huawei, Samsung) also have their own Autostart or app launch setting that must be allowed in system settings.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        val active = ui.accounts.firstOrNull { it.id == ui.activeId }
        val tk = active?.token.orEmpty()
        var shown by remember { mutableStateOf(false) }
        SectionCard("Token") {
            Text(
                "Token for ${active?.name ?: "?"}. Anyone who has it controls your account, so never share it.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Text(
                if (shown) tk else "\u2022".repeat(32),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpOutlinedButton(onClick = { shown = !shown }, modifier = Modifier.weight(1f)) { Text(if (shown) "Hide" else "Show") }
                ExpTonalButton(onClick = { onCopyToken(tk) }, modifier = Modifier.weight(1f)) { Text("Copy") }
            }
        }

        SectionCard("Updates") {
            var auto by remember { mutableStateOf(Store.get(ctx, "upd_auto", "1") == "1") }
            val st = Updater.state
            Text("Installed: ${Updater.installedVersion(ctx)}", style = MaterialTheme.typography.bodyLarge)
            Text(
                when (st) {
                    is UpdateState.Idle -> "Not checked yet"
                    is UpdateState.Checking -> "Checking GitHub"
                    is UpdateState.UpToDate -> "You have the latest version"
                    is UpdateState.Available -> "Version ${st.tag} is available"
                    is UpdateState.Downloading -> "Downloading ${st.tag}"
                    is UpdateState.Ready -> "Version ${st.tag} is ready to install"
                    is UpdateState.Failed -> st.message
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (st is UpdateState.Failed) cs.error else cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (st is UpdateState.Available && st.notes.isNotBlank()) {
                Text(
                    st.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 8,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
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
                else -> ExpTonalButton(
                    onClick = { Updater.check(ctx) },
                    modifier = Modifier.padding(top = 12.dp)
                ) { Text("Check for updates") }
            }
            ToggleRow("Check automatically", "At most once every 12 hours, when the app opens", auto) {
                auto = it
                Store.put(ctx, "upd_auto", if (it) "1" else "0")
            }
        }

        SectionCard("About") {
            val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
            Text("DPM", style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                "Discord Presence Manager $version",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Text(
                "Logging in with a token or QR code, and sending presence from a user account, is against Discord's Terms of Service " +
                    "and can get an account limited or banned. Use at your own risk, ideally on an alternate account. " +
                    "DPM is an independent project and is not affiliated with Discord.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            ExpTextButton(onClick = onReplayIntro) { Text("Show intro again") }
        }

        SectionCard("Credits") {
            Text("Made by DinoCDX", style = MaterialTheme.typography.titleMediumEmphasized)
            Text(
                "DPM is open source. Bug reports and pull requests are welcome.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpOutlinedButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.OWNER_URL)))
                }) { Text("GitHub profile") }
                ExpTonalButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.REPO_URL)))
                }) { Text("Source code") }
            }
        }

        ExpTonalButton(
            onClick = { active?.let { confirm = it } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Log out of ${active?.name ?: "account"}") }
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

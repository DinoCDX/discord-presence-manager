package com.dpm.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

private const val STEPS = 3
private const val WELCOME_STEP = 0
private const val LOGIN_STEP = 1
private const val PERMISSIONS_STEP = 2

@Composable
fun Onboarding(ui: UiState, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var step by remember {
        mutableIntStateOf(Store.get(ctx, "onb_step", "0").toIntOrNull()?.coerceIn(0, STEPS - 1) ?: 0)
    }
    val active = ui.accounts.firstOrNull { it.id == ui.activeId }
    val loggedIn = active != null

    fun goTo(n: Int) {
        step = n
        Store.put(ctx, "onb_step", n.toString())
    }

    val notif = remember(ui.permTick) { Perms.notifications(ctx) }
    val usage = remember(ui.permTick) { Perms.usage(ctx) }
    val listener = remember(ui.permTick) { Perms.listener(ctx) }
    val battery = remember(ui.permTick) { Perms.battery(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ui.permTick++
        if (!granted && ctx.activity().notificationsBlockedForGood()) ctx.openNotificationSettings()
    }

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    fun finish(start: Boolean) {
        Store.put(ctx, "onb_step", "0")
        ui.setOnboarded(true)
        if (start) ui.setRunning(true)
        onDone()
    }

    Surface(Modifier.fillMaxSize(), color = cs.surface) {
        Column(Modifier.systemBarsPadding().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearWavyProgressIndicator(
                    progress = { (step + 1f) / STEPS },
                    modifier = Modifier.weight(1f)
                )
                if (loggedIn) {
                    Spacer(Modifier.width(12.dp))
                    ExpTextButton(onClick = { finish(false) }) { Text("Skip") }
                }
            }

            AnimatedContent(
                step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(spatial) { it / 8 * dir } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { -it / 8 * dir } + fadeOut(effects))
                },
                label = "step"
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
                    val icon = when (s) {
                        WELCOME_STEP -> R.drawable.ic_dpm_mark
                        LOGIN_STEP -> R.drawable.ic_presence
                        else -> R.drawable.ic_settings
                    }
                    val badge = when (s) {
                        WELCOME_STEP -> MaterialShapes.Cookie12Sided
                        LOGIN_STEP -> MaterialShapes.Clover4Leaf
                        else -> MaterialShapes.Flower
                    }
                    Surface(shape = badge.toShape(), color = cs.primaryContainer) {
                        Icon(
                            painterResource(icon),
                            null,
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.padding(26.dp).size(44.dp)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    when (s) {
                        WELCOME_STEP -> {
                            Head("Welcome to DPM")
                            Body("DPM (Discord Presence Manager) shows what you're doing on your phone as a Discord activity: music, video, games and apps.")
                            Body("Setup takes about a minute. You'll log in with Discord, then choose the permissions to allow. You can skip any of them and change them later.")
                            Spacer(Modifier.height(12.dp))
                            Body("Sending presence from a user account is against Discord's Terms of Service and can get the account limited or banned. Use an alternate account if you can.")
                        }
                        LOGIN_STEP -> LoginStep(active)
                        else -> {
                            Head("Permissions")
                            Body("Allow what you're comfortable with. Each one can be changed later.")
                            PermissionRow(
                                title = "Notifications",
                                body = "Shows what is being shared, with Pause and Stop buttons.",
                                ok = notif || Build.VERSION.SDK_INT < 33,
                                action = "Allow"
                            ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            PermissionRow(
                                title = "Usage access",
                                body = "Lets DPM see which app or game is on screen. Without it, only music and video show.",
                                ok = usage,
                                action = "Open settings"
                            ) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                            PermissionRow(
                                title = "Notification access",
                                body = "Lets DPM read the title, artist and cover of what's playing.",
                                ok = listener,
                                action = "Open settings"
                            ) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                            PermissionRow(
                                title = "Background activity",
                                body = "Stops Android from putting DPM to sleep while presence is on.",
                                ok = battery,
                                action = "Allow"
                            ) {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            ToggleRow("Start when the phone restarts", null, ui.bootStart) { ui.setBootStart(it) }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) ExpTextButton(onClick = { goTo(step - 1) }) { Text("Back") }
                Spacer(Modifier.weight(1f))
                if (step < STEPS - 1) {
                    ExpTonalButton(enabled = step != LOGIN_STEP || loggedIn, onClick = { goTo(step + 1) }) { Text("Next") }
                } else {
                    ExpButton(onClick = { finish(true) }) { Text("Start sharing") }
                }
            }
        }
    }
}

@Composable
private fun LoginStep(active: Account?) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val configured = DiscordOAuth.isConfigured(ctx)
    Head("Log in with Discord")
    Body("You approve DPM on Discord's own page, so captchas, two-factor codes and passkeys all work. DPM never sees your password.")
    if (active != null) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = cs.surfaceContainerHigh
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Logged in as ${active.name}", style = MaterialTheme.typography.titleMediumEmphasized, color = cs.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "You can add more accounts later in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }
    } else {
        ExpButton(enabled = configured, onClick = { ctx.activity()?.let { DiscordOAuth.start(it) } }) {
            Text("Continue with Discord")
        }
        if (!configured) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Discord sign-in isn't set up in this build yet.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.error
            )
        }
    }
}

@Composable
private fun Head(t: String) {
    Text(t, style = MaterialTheme.typography.headlineMediumEmphasized)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Body(t: String) {
    Text(t, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Status(ok: Boolean) {
    Text(
        if (ok) "Allowed" else "Not allowed yet",
        style = MaterialTheme.typography.labelLarge,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun PermissionRow(title: String, body: String, ok: Boolean, action: String, onAction: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerHigh
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Status(ok)
                Spacer(Modifier.weight(1f))
                if (!ok) ExpTonalButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

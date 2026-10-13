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
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

private const val STEPS = 6

@Composable
fun Onboarding(ui: UiState, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var step by rememberSaveable { mutableIntStateOf(0) }

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
                Spacer(Modifier.width(12.dp))
                ExpTextButton(onClick = { finish(false) }) { Text("Skip") }
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
                        0 -> R.drawable.ic_dpm_mark
                        1, 4 -> R.drawable.ic_settings
                        else -> R.drawable.ic_apps
                    }
                    val badge = when (s) {
                        0 -> MaterialShapes.Cookie12Sided
                        1 -> MaterialShapes.Cookie9Sided
                        2 -> MaterialShapes.Sunny
                        3 -> MaterialShapes.Clover4Leaf
                        4 -> MaterialShapes.Flower
                        else -> MaterialShapes.Cookie6Sided
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
                        0 -> {
                            Head("Welcome to DPM")
                            Body("DPM (Discord Presence Manager) shows what you're doing on your phone as a Discord activity: music, video, games and apps.")
                            Body("It needs a few permissions to do that. You can skip any of them and change them later.")
                            Spacer(Modifier.height(12.dp))
                            Body("Sending presence from a user account is against Discord's Terms of Service and can get the account limited or banned. Use an alternate account if you can.")
                        }
                        1 -> {
                            Head("Notifications")
                            Body("A notification shows what is being shared right now and has Pause and Stop buttons, so you stay in control from anywhere.")
                            if (Build.VERSION.SDK_INT < 33 || notif) {
                                Status(true)
                            } else {
                                Status(false)
                                ExpButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                            }
                        }
                        2 -> {
                            Head("Usage access")
                            Body("Lets the app see which app or game is on screen. Without it, only music and video are shown.")
                            Status(usage)
                            if (!usage) {
                                ExpButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Open settings") }
                            }
                        }
                        3 -> {
                            Head("Notification access")
                            Body("Lets the app read what music or video is playing, including the title, artist and cover.")
                            Status(listener)
                            if (!listener) {
                                ExpButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Open settings") }
                            }
                        }
                        4 -> {
                            Head("Keep it running")
                            Body("Android can stop apps in the background. Allow background activity, and let presence start by itself after a restart.")
                            Status(battery)
                            if (!battery) {
                                ExpButton(onClick = {
                                    ctx.startActivity(
                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                                    )
                                }) { Text("Allow background activity") }
                            }
                            Spacer(Modifier.height(12.dp))
                            ToggleRow("Start when the phone restarts", null, ui.bootStart) { ui.setBootStart(it) }
                        }
                        else -> {
                            Head("Images (optional)")
                            Body("To show app icons and cover art, create a free application in Discord's developer portal and paste its Application ID here. Pictures are uploaded from your phone to that application.")
                            Field("Application ID", ui.appId) { v -> ui.setAppId(v.filter { it.isDigit() }.take(25)) }
                            Spacer(Modifier.height(8.dp))
                            ExpOutlinedButton(onClick = {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/developers/applications")))
                            }) { Text("Open developer portal") }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) ExpTextButton(onClick = { step-- }) { Text("Back") }
                Spacer(Modifier.weight(1f))
                if (step < STEPS - 1) {
                    ExpTonalButton(onClick = { step++ }) { Text("Next") }
                } else {
                    ExpButton(onClick = { finish(true) }) { Text("Start sharing") }
                }
            }
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

package com.dpm.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : AppCompatActivity() {
    private lateinit var ui: UiState

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Store.token(this) == null) {
            go(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        ui = UiState(this)
        Updater.autoCheck(applicationContext)

        setContent {
            DPMTheme(ui.themeMode, ui.dyn) {
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                DisposableEffect(lifecycle) {
                    val obs = LifecycleEventObserver { _, e ->
                        if (e == Lifecycle.Event.ON_RESUME) {
                            ui.reload()
                            ui.permTick++
                        }
                    }
                    lifecycle.addObserver(obs)
                    onDispose { lifecycle.removeObserver(obs) }
                }
                LaunchedEffect(Unit) { Bus.flow.collect { ui.reload() } }

                if (!ui.onboarded) {
                    Onboarding(ui) { }
                } else {
                    Home()
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Home() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val appsList = rememberLazyListState()
        val presenceScroll = rememberScrollState()
        val settingsScroll = rememberScrollState()
        val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Scaffold(
            bottomBar = {
                AnimatedVisibility(visible = !imeOpen) {
                    NavigationBar {
                        val items = listOf(
                            "Presence" to R.drawable.ic_presence,
                            "Apps" to R.drawable.ic_apps,
                            "Settings" to R.drawable.ic_settings
                        )
                        items.forEachIndexed { i, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(painterResource(icon), null) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Crossfade(tab, modifier = Modifier.padding(pad).consumeWindowInsets(pad).imePadding(), label = "tab") { t ->
                when (t) {
                    0 -> PresenceScreen(ui, openApps = { tab = 1 }, scroll = presenceScroll)
                    1 -> AppsScreen(ui, appsList)
                    else -> SettingsScreen(
                        ui,
                        onSwitch = { switchTo(it) },
                        onRemove = { removeAccount(it) },
                        onAddBrowser = { go(Intent(this, WebLoginActivity::class.java)) },
                        onAddOther = { go(Intent(this, LoginActivity::class.java).putExtra("add", true)) },
                        onCopyToken = { copyToken(it) },
                        onReplayIntro = { ui.setOnboarded(false) },
                        scroll = settingsScroll
                    )
                }
            }
        }
    }

    private fun switchTo(a: Account) {
        Store.setActive(this, a.id)
        GatewayClient.connect(a.token)
        startPresenceIfWanted()
        ui.reload()
        toast("Switched to ${a.name}")
    }

    private fun removeAccount(a: Account) {
        val wasActive = Store.active(this)?.id == a.id
        Store.removeAccount(this, a.id)
        if (!wasActive) {
            ui.reload()
            return
        }
        val next = Store.active(this)
        if (next != null) {
            switchTo(next)
        } else {
            PresenceService.halt(this)
            GatewayClient.disconnect()
            go(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }

    private fun copyToken(tk: String) {
        val clip = ClipData.newPlainText("token", tk)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        toast("Token copied")
    }
}

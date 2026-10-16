package com.dpm.app

import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    private lateinit var ui: UiState

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ui = UiState(this)
        if (Store.token(this) == null && ui.onboarded) {
            go(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        Updater.autoCheck(applicationContext)

        setContent {
            DPMTheme(ui.themeMode, false) {
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

    @Composable
    private fun Home() {
        val pager = rememberPagerState(pageCount = { 3 })
        val scope = rememberCoroutineScope()
        val tab = pager.currentPage
        fun goTo(i: Int) { scope.launch { pager.animateScrollToPage(i) } }
        val appsList = rememberLazyListState()
        val presenceScroll = rememberScrollState()
        val settingsScroll = rememberScrollState()
        val titles = listOf("Presence", "Apps", "Settings")
        val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        var conn by remember { mutableStateOf(GatewayClient.state) }
        DisposableEffect(Unit) {
            GatewayClient.onState = { conn = it }
            onDispose { GatewayClient.onState = null }
        }

        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

        Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            titles[tab],
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (tab == 0) {
                            val up = conn.startsWith("Connected")
                            StatusPill(up, if (up) "Connected" else conn, Modifier.widthIn(max = 200.dp))
                        }
                    }
                    if (tab == 1) {
                        Text(
                            "Choose what Discord can show",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(visible = !imeOpen) {
                    ShortNavigationBar {
                        val items = listOf(
                            "Presence" to R.drawable.ic_presence,
                            "Apps" to R.drawable.ic_apps,
                            "Settings" to R.drawable.ic_settings
                        )
                        items.forEachIndexed { i, (label, icon) ->
                            ShortNavigationBarItem(
                                selected = tab == i,
                                onClick = { goTo(i) },
                                icon = { Icon(painterResource(icon), null) },
                                label = if (tab == i) ({ Text(label) }) else null
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize()) {
            DecorBackground(
                scrollY = {
                    when (pager.currentPage) {
                        0 -> presenceScroll.value.toFloat()
                        1 -> appsList.firstVisibleItemIndex * 220f + appsList.firstVisibleItemScrollOffset
                        else -> settingsScroll.value.toFloat()
                    }
                },
                pageX = { pager.currentPage + pager.currentPageOffsetFraction }
            )
            HorizontalPager(
                state = pager,
                modifier = Modifier.padding(pad).consumeWindowInsets(pad).imePadding(),
                beyondViewportPageCount = 0
            ) { t ->
                when (t) {
                    0 -> PresenceScreen(ui, openApps = { goTo(1) }, scroll = presenceScroll)
                    1 -> AppsScreen(ui, appsList)
                    else -> SettingsScreen(
                        ui,
                        onSwitch = { switchTo(it) },
                        onRemove = { removeAccount(it) },
                        onAdd = { go(Intent(this@MainActivity, LoginActivity::class.java).putExtra("add", true)) },
                        onReplayIntro = { ui.setOnboarded(false) },
                        scroll = settingsScroll
                    )
                }
            }
            }
        }
        AnimatedVisibility(
            visible = ui.presetsOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PresetsScreen(ui)
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
}

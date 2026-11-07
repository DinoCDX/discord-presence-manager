package com.dpm.app

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var ui: UiState

    override fun onStart() {
        super.onStart()
        Live.setVisible(this, true)
    }

    override fun onStop() {
        super.onStop()
        Live.setVisible(this, false)
    }

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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (Bench.recording(this@MainActivity)) {
                        withContext(Dispatchers.IO) { Bench.sample(this@MainActivity, app = true) }
                    }
                    delay(2_000L)
                }
            }
        }

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
        val spatialFloat = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val tab = pager.currentPage
        fun goTo(i: Int) { scope.launch { pager.animateScrollToPage(i, animationSpec = spatialFloat) } }
        val appsList = rememberLazyListState()
        val presenceScroll = rememberScrollState()
        val settingsScroll = rememberScrollState()
        val titles = listOf("Presence", "Apps", "Settings")
        val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val haptic = LocalHapticFeedback.current

        var conn by remember { mutableStateOf(GatewayClient.state) }
        DisposableEffect(Unit) {
            GatewayClient.onState = { conn = it }
            onDispose { GatewayClient.onState = null }
        }

        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) {
            Notes.flow.collect { snackbar.showSnackbar(it, duration = SnackbarDuration.Short) }
        }

        Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Box(
                    Modifier
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                        .heightIn(min = 48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Crossfade(targetState = tab, animationSpec = effects, label = "title") { i ->
                        Text(
                            titles[i],
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            maxLines = 1
                        )
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = MaterialTheme.shapes.large,
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(visible = !imeOpen) {
                    // The surface also covers the system navigation inset, so content behind the bar never shows through.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 6.dp
                    ) {
                        ShortNavigationBar(
                            modifier = Modifier.navigationBarsPadding().height(60.dp),
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            containerColor = Color.Transparent
                        ) {
                            val items = listOf(
                                "Presence" to R.drawable.ic_presence,
                                "Apps" to R.drawable.ic_apps,
                                "Settings" to R.drawable.ic_settings
                            )
                            items.forEachIndexed { i, (label, icon) ->
                                ShortNavigationBarItem(
                                    selected = tab == i,
                                    onClick = {
                                        if (tab != i) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        goTo(i)
                                    },
                                    icon = { Icon(painterResource(icon), contentDescription = label) },
                                    label = null
                                )
                            }
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
                flingBehavior = PagerDefaults.flingBehavior(state = pager, snapAnimationSpec = spatialFloat),
                beyondViewportPageCount = 1
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

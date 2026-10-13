package com.dpm.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {
    private var adding = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        adding = intent.getBooleanExtra("add", false)
        if (!adding && Store.token(this) != null) {
            go(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val themeMode = Store.get(this, "theme", "system")
        val dyn = false
        setContent {
            DPMTheme(themeMode, dyn) {
                LoginScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LoginScreen() {
        val cs = MaterialTheme.colorScheme
        val scope = rememberCoroutineScope()
        var tab by remember { mutableIntStateOf(0) }
        var token by remember { mutableStateOf("") }
        var busyToken by remember { mutableStateOf(false) }
        var qr by remember { mutableStateOf<Bitmap?>(null) }
        var qrStatus by remember { mutableStateOf("Preparing QR code") }
        var qrNonce by remember { mutableIntStateOf(0) }


        DisposableEffect(tab, qrNonce) {
            var auth: RemoteAuth? = null
            if (tab == 2) {
                qr = null
                qrStatus = "Preparing QR code"
                auth = RemoteAuth(
                    onQr = { url ->
                        val bmp = qrBitmap(url, 640)
                        runOnUiThread {
                            qr = bmp
                            qrStatus = "Scan with the Discord app on another device"
                        }
                    },
                    onStatus = { s -> runOnUiThread { qrStatus = s } },
                    onToken = { t -> scope.launch { completeLogin(t) } },
                    onFail = { m -> runOnUiThread { qrStatus = m } }
                ).also { it.start() }
            }
            onDispose { auth?.stop() }
        }

        Surface(Modifier.fillMaxSize(), color = cs.surface) {
            Column(Modifier.systemBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = if (adding) 4.dp else 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (adding) {
                        IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                    Text(
                        if (adding) "Add account" else "Log in to DPM",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                PrimaryTabRow(selectedTabIndex = tab, containerColor = cs.surface) {
                    listOf("Browser", "Token", "QR").forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }

                when (tab) {
                    0 -> Column(
                        Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Log in with Discord's own page", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        ExpButton(onClick = {
                            startActivity(
                                Intent(this@LoginActivity, BrowserLoginActivity::class.java)
                            )
                        }) { Text("Open Discord login") }
                    }
                    else -> Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = cs.errorContainer)
                        ) {
                            Text(
                                "Logging in with a token or QR code, and sending presence from a user account, is against Discord's " +
                                    "Terms of Service and can get your account limited or banned. Use at your own risk, ideally on an alternate account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        if (tab == 1) {
                            OutlinedTextField(
                                value = token,
                                onValueChange = { token = it },
                                label = { Text("Account token") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.large,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                            ExpButton(
                                enabled = !busyToken && token.isNotBlank(),
                                onClick = {
                                    busyToken = true
                                    scope.launch {
                                        val ok = completeLogin(token.trim().trim('"'))
                                        if (!ok) busyToken = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (busyToken) {
                                    LoadingIndicator(Modifier.size(24.dp))
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(if (busyToken) "Logging in" else "Log in")
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    Modifier.size(260.dp).clip(MaterialTheme.shapes.extraLarge).background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val b = qr
                                    if (b != null) {
                                        Image(b.asImageBitmap(), null, Modifier.size(240.dp))
                                    } else {
                                        LoadingIndicator()
                                    }
                                }
                                Text(qrStatus, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                ExpOutlinedButton(onClick = { qrNonce++ }) { Text("Refresh QR") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

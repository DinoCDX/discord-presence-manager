package com.dpm.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val adding = intent.getBooleanExtra("add", false)
        if (!adding && Store.token(this) != null) {
            go(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        val themeMode = Store.get(this, "theme", "system")
        val dyn = Store.get(this, "dyn", "1") == "1"
        setContent {
            DPMTheme(themeMode, dyn) {
                LoginScreen(adding)
            }
        }
    }

    @Composable
    private fun LoginScreen(adding: Boolean) {
        val cs = MaterialTheme.colorScheme
        val scope = rememberCoroutineScope()
        var tab by remember { mutableIntStateOf(0) }
        var token by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
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
            Column(
                Modifier
                    .systemBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!adding) {
                        Surface(shape = RoundedCornerShape(22.dp), color = cs.primaryContainer) {
                            Icon(
                                painterResource(R.drawable.ic_dpm_mark),
                                null,
                                tint = cs.onPrimaryContainer,
                                modifier = Modifier.padding(16.dp).size(40.dp)
                            )
                        }
                    }
                    Column {
                        Text(if (adding) "Add account" else "DPM", style = MaterialTheme.typography.headlineLarge)
                        if (!adding) {
                            Text("Discord Presence Manager", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                        }
                        Text(
                            "Control your Discord presence from your phone.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
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

                TabRow(selectedTabIndex = tab) {
                    listOf("Browser", "Token", "QR").forEachIndexed { i, n ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(n) })
                    }
                }

                when (tab) {
                    0 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Log in on Discord's own website inside the app. Captchas, 2FA and passkeys all work, " +
                                "and the token is picked up automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                        Button(
                            onClick = { go(Intent(this@LoginActivity, WebLoginActivity::class.java)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open Discord login") }
                    }
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("Account token") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = !busy && token.isNotBlank(),
                            onClick = {
                                busy = true
                                scope.launch {
                                    val ok = completeLogin(token.trim().trim('"'))
                                    if (!ok) busy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "Logging in" else "Log in") }
                    }
                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            Modifier.size(260.dp).clip(RoundedCornerShape(20.dp)).background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            val b = qr
                            if (b != null) {
                                Image(b.asImageBitmap(), null, Modifier.size(240.dp))
                            }
                        }
                        Text(qrStatus, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        OutlinedButton(onClick = { qrNonce++ }) { Text("Refresh QR") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

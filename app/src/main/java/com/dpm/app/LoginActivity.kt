package com.dpm.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

class LoginActivity : ComponentActivity() {
    private var adding = false

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
        setContent {
            DPMTheme(themeMode, false) {
                LoginScreen()
            }
        }
    }

    @Composable
    private fun LoginScreen() {
        val cs = MaterialTheme.colorScheme
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

                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(shape = MaterialShapes.Cookie12Sided.toShape(), color = cs.primaryContainer) {
                        Icon(
                            painterResource(R.drawable.ic_dpm_mark),
                            null,
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.padding(26.dp).size(44.dp)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Log in with your Discord account", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You approve DPM on Discord's own page. DPM never sees your password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    val configured = DiscordOAuth.isConfigured(this@LoginActivity)
                    ExpButton(
                        enabled = configured,
                        onClick = { DiscordOAuth.start(this@LoginActivity) }
                    ) { Text("Continue with Discord") }
                    if (!configured) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Discord sign-in isn't set up in this build yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

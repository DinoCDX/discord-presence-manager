package com.dpm.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class OAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val (expectedState, verifier) = DiscordOAuth.takePending(this)

        val error = data?.getQueryParameter("error")
        val code = data?.getQueryParameter("code")
        val state = data?.getQueryParameter("state")

        when {
            error != null -> return fail(
                if (error == "access_denied") "Login was cancelled" else "Discord returned an error: $error"
            )
            expectedState.isEmpty() || state != expectedState -> return fail(
                "This login link doesn't match the one you started. Try again."
            )
            code.isNullOrEmpty() || verifier.isEmpty() -> return fail("Discord didn't send a login code. Try again.")
        }

        lifecycleScope.launch {
            val result = DiscordOAuth.finish(this@OAuthCallbackActivity, code!!, verifier)
            if (result.error != null) {
                fail(result.error)
            } else {
                Store.token(this@OAuthCallbackActivity)?.let { GatewayClient.connect(it) }
                this@OAuthCallbackActivity.startPresenceIfWanted()
                toast("Logged in as ${result.accountName}")
                go(
                    Intent(this@OAuthCallbackActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }
        }
    }

    private fun fail(message: String) {
        toast(message)
        go(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}

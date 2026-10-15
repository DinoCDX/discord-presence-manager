package com.dpm.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

object DiscordOAuth {
    const val AUTHORIZE = "https://discord.com/oauth2/authorize"
    const val TOKEN = "https://discord.com/api/v10/oauth2/token"
    const val ME = "https://discord.com/api/v10/users/@me"

    const val SCOPE = "openid identify sdk.social_layer_presence"

    private const val KEY_STATE = "oauth_state"
    private const val KEY_VERIFIER = "oauth_verifier"

    private val rng = SecureRandom()
    private val refreshLock = Mutex()
    val http: OkHttpClient = Api.http

    fun clientId(c: Context): String = c.getString(R.string.discord_oauth_app_id)

    fun isConfigured(c: Context): Boolean = clientId(c).isNotEmpty()

    fun redirectUri(c: Context): String = "discord-${clientId(c)}:/authorize/callback"

    fun refreshKey(accountId: String): String = "oauth_refresh_$accountId"

    private fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun random(size: Int): String {
        val b = ByteArray(size)
        rng.nextBytes(b)
        return b64(b)
    }

    fun challenge(verifier: String): String =
        b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun start(activity: Activity) {
        val verifier = random(64)
        val state = random(24)
        Store.put(activity, KEY_VERIFIER, verifier)
        Store.put(activity, KEY_STATE, state)
        val uri = Uri.parse(AUTHORIZE).buildUpon()
            .appendQueryParameter("client_id", clientId(activity))
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri(activity))
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", challenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    fun takePending(c: Context): Pair<String, String> {
        val pending = Store.get(c, KEY_STATE, "") to Store.get(c, KEY_VERIFIER, "")
        Store.put(c, KEY_STATE, "")
        Store.put(c, KEY_VERIFIER, "")
        return pending
    }

    class Outcome(val accountName: String?, val error: String?)

    suspend fun finish(c: Context, code: String, verifier: String): Outcome = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("client_id", clientId(c))
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri(c))
                .add("code_verifier", verifier)
                .build()
            val tokenReq = Request.Builder().url(TOKEN).header("User-Agent", Api.UA).post(form).build()
            val (tokenCode, tokenBody) = http.newCall(tokenReq).execute().use { it.code to it.body?.string().orEmpty() }
            if (tokenCode !in 200..299) return@withContext Outcome(null, "Discord didn't accept the login (HTTP $tokenCode)")
            val tok = JSONObject(tokenBody)
            val access = tok.getString("access_token")
            val refresh = tok.optString("refresh_token")

            val meReq = Request.Builder().url(ME).header("Authorization", "Bearer $access").header("User-Agent", Api.UA).build()
            val (meCode, meBody) = http.newCall(meReq).execute().use { it.code to it.body?.string().orEmpty() }
            if (meCode !in 200..299) return@withContext Outcome(null, "Couldn't read your Discord profile (HTTP $meCode)")
            val me = JSONObject(meBody)
            val id = me.getString("id")
            val name = if (!me.isNull("global_name")) me.getString("global_name") else me.optString("username")

            Store.addAccount(c, access, name, id)
            Store.put(c, refreshKey(id), refresh)
            Store.put(c, "oauth_client_id", clientId(c))
            Outcome(name, null)
        } catch (e: Exception) {
            Outcome(null, "Login failed: ${e.message ?: "network error"}")
        }
    }

    suspend fun renew(c: Context, expired: String): String? = refreshLock.withLock {
        withContext(Dispatchers.IO) {
            val acc = Store.accounts(c).firstOrNull { it.token == expired }
                ?: return@withContext Store.token(c)?.takeIf { it != expired }

            val refresh = Store.get(c, refreshKey(acc.id), "")
            if (refresh.isEmpty()) return@withContext null

            val (newAccess, newRefresh) = refreshAccessToken(clientId(c), refresh) ?: return@withContext null
            Store.updateToken(c, acc.id, newAccess)
            Store.put(c, refreshKey(acc.id), newRefresh)
            newAccess
        }
    }

    private suspend fun refreshAccessToken(clientId: String, refreshToken: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            val form = FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()
            val req = Request.Builder().url(TOKEN).header("User-Agent", Api.UA).post(form).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    if (resp.code in 400..499 && resp.code != 429) return@withContext null
                    throw java.io.IOException("Discord HTTP ${resp.code} while refreshing")
                }
                val body = JSONObject(resp.body?.string().orEmpty())
                val newAccess = body.getString("access_token")
                val newRefresh = body.optString("refresh_token", refreshToken)
                newAccess to newRefresh
            }
        }
}

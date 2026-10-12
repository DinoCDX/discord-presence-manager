package com.dpm.app

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.MGF1ParameterSpec
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

fun qrBitmap(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (m.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}

class RemoteAuth(
    private val onQr: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onToken: (String) -> Unit,
    private val onFail: (String) -> Unit
) {
    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ws: WebSocket? = null
    private var hb: Job? = null
    private var keys: KeyPair? = null

    @Volatile
    private var done = false

    fun start() {
        scope.launch {
            val gen = KeyPairGenerator.getInstance("RSA")
            gen.initialize(2048)
            keys = gen.generateKeyPair()
            val req = Request.Builder()
                .url("wss://remote-auth-gateway.discord.gg/?v=2")
                .header("Origin", "https://discord.com")
                .header("User-Agent", Api.UA)
                .build()
            ws = http.newWebSocket(req, Listener())
        }
    }

    fun stop() {
        done = true
        hb?.cancel()
        ws?.close(1000, null)
        scope.cancel()
    }

    private fun decrypt(b64: String): ByteArray {
        val c = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val spec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
        c.init(Cipher.DECRYPT_MODE, keys!!.private, spec)
        return c.doFinal(Base64.decode(b64, Base64.DEFAULT))
    }

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val o = try { JSONObject(text) } catch (e: Exception) { return }
            try {
                when (o.optString("op")) {
                    "hello" -> {
                        val iv = o.getLong("heartbeat_interval")
                        hb = scope.launch {
                            while (isActive) {
                                delay(iv)
                                webSocket.send("{\"op\":\"heartbeat\"}")
                            }
                        }
                        val pub = Base64.encodeToString(keys!!.public.encoded, Base64.NO_WRAP)
                        webSocket.send(JSONObject().put("op", "init").put("encoded_public_key", pub).toString())
                    }
                    "nonce_proof" -> {
                        val nonce = decrypt(o.getString("encrypted_nonce"))
                        val proof = Base64.encodeToString(
                            MessageDigest.getInstance("SHA-256").digest(nonce),
                            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                        )
                        webSocket.send(JSONObject().put("op", "nonce_proof").put("proof", proof).toString())
                    }
                    "pending_remote_init" -> onQr("https://discord.com/ra/" + o.getString("fingerprint"))
                    "pending_ticket" -> {
                        val p = String(decrypt(o.getString("encrypted_user_payload")))
                        val name = p.split(":").drop(3).joinToString(":")
                        onStatus("Scanned by $name. Confirm on your other device.")
                    }
                    "pending_login" -> {
                        onStatus("Signing in")
                        val ticket = o.getString("ticket")
                        scope.launch {
                            val r = Api.call(
                                "POST",
                                "/users/@me/remote-auth/login",
                                null,
                                JSONObject().put("ticket", ticket)
                            ).obj()
                            when {
                                !r.isNull("encrypted_token") -> {
                                    done = true
                                    onToken(String(decrypt(r.getString("encrypted_token"))))
                                }
                                r.has("captcha_key") ->
                                    onFail("Discord wants a captcha for QR login right now. Use token login.")
                                else -> onFail(r.optString("message", "QR login failed"))
                            }
                        }
                    }
                    "cancel" -> {
                        done = true
                        onFail("Cancelled on the other device. Refresh the QR code.")
                    }
                }
            } catch (e: Exception) {
                onFail("QR error: ${e.message}")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!done) onFail("QR expired. Refresh to get a new one.")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!done) onFail("Connection failed: ${t.message}")
        }
    }
}

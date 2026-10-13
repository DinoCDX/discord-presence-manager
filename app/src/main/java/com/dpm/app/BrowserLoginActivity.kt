package com.dpm.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Plain-view browser login. No Compose around the WebView.
 * The token is taken from the Authorization header Discord's own page sends
 * to its API once you are logged in, so nothing is injected into the page.
 */
class BrowserLoginActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var bar: ProgressBar
    private lateinit var msg: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var done = false

    private val grab = """
        (function(){
          var t=null;
          try{var f=document.createElement('iframe');f.style.display='none';document.body.appendChild(f);t=f.contentWindow.localStorage.getItem('token');f.remove();}catch(e){}
          return t;
        })()
    """.trimIndent()

    private val poll = object : Runnable {
        override fun run() {
            if (done) return
            val u = web.url.orEmpty()
            if (u.contains("/channels") || u.contains("/app")) {
                web.evaluateJavascript(grab) { raw ->
                    val t = raw?.trim()?.trim('"').orEmpty()
                    if (t.length > 30 && t != "null") finishWith(t)
                }
            }
            handler.postDelayed(this, 2000)
        }
    }

    private fun finishWith(token: String) {
        if (done) return
        done = true
        lifecycleScope.launch {
            val ok = completeLogin(token)
            if (!ok) done = false
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1F22"))
            fitsSystemWindows = true
        }
        val header = TextView(this).apply {
            text = "←  Log in to Discord"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        val body = FrameLayout(this)
        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#1E1F22"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = Api.UA
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, p: Int) {
                    bar.progress = p
                    bar.visibility = if (p in 1..99) View.VISIBLE else View.GONE
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                    val host = req.url.host.orEmpty()
                    if (!done && host.endsWith("discord.com")) {
                        val auth = req.requestHeaders["Authorization"] ?: req.requestHeaders["authorization"]
                        if (auth != null && auth.length > 30 && !auth.startsWith("Bot ") && !auth.startsWith("Bearer ")) {
                            handler.post { finishWith(auth.trim()) }
                        }
                    }
                    return null
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    msg.visibility = View.GONE
                }

                override fun onReceivedError(
                    view: WebView,
                    req: WebResourceRequest,
                    err: android.webkit.WebResourceError
                ) {
                    if (req.isForMainFrame) {
                        msg.text = "Could not load Discord. Tap to retry."
                        msg.visibility = View.VISIBLE
                    }
                }
            }
        }
        msg = TextView(this).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setOnClickListener {
                visibility = View.GONE
                web.loadUrl("https://discord.com/login")
            }
        }
        body.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        body.addView(msg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        web.loadUrl("https://discord.com/login")
        handler.postDelayed(poll, 3000)
    }

    override fun onDestroy() {
        done = true
        handler.removeCallbacksAndMessages(null)
        web.stopLoading()
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
}

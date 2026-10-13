package com.dpm.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONTokener

class WebLoginActivity : ComponentActivity() {
    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false
    private var closed = false
    private var loadProgress by mutableIntStateOf(0)

    private val grab = """
        (function(){
          var t=null;
          try{var f=document.createElement('iframe');f.style.display='none';document.body.appendChild(f);t=f.contentWindow.localStorage.getItem('token');f.remove();}catch(e){}
          if(!t){
            try{
              var m=[];
              window.webpackChunkdiscord_app.push([[Symbol()],{},function(r){for(var k in r.c)m.push(r.c[k]);}]);
              window.webpackChunkdiscord_app.pop();
              for(var j=0;j<m.length;j++){
                var e=m[j]&&m[j].exports;
                if(e&&e.default&&typeof e.default.getToken==='function'){var x=e.default.getToken();if(x){t=x;break;}}
              }
            }catch(e){}
          }
          return t;
        })()
    """.trimIndent()

    private val poll = object : Runnable {
        override fun run() {
            if (closed) return
            if (!busy) web.evaluateJavascript(grab) { handle(it) }
            handler.postDelayed(this, 1500)
        }
    }

    private fun handle(raw: String?) {
        if (raw == null || raw == "null" || busy || closed) return
        val t = (JSONTokener(raw).nextValue() as? String)?.trim()?.trim('"') ?: return
        if (t.length < 30) return
        busy = true
        lifecycleScope.launch {
            val ok = completeLogin(t)
            if (!ok) busy = false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = Api.UA
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    loadProgress = newProgress
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    handler.removeCallbacks(poll)
                    handler.postDelayed(poll, 1200)
                }
            }
        }

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

        val themeMode = Store.get(this, "theme", "system")
        val dyn = Store.get(this, "dyn", "1") == "1"
        setContent {
            DPMTheme(themeMode, dyn) {
                Screen()
            }
        }

        web.loadUrl("https://discord.com/login")
    }

    @Composable
    private fun Screen() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Log in to Discord") },
                    navigationIcon = {
                        IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }
        ) { pad ->
            Column(Modifier.padding(pad).consumeWindowInsets(pad).imePadding()) {
                AnimatedVisibility(visible = loadProgress in 1..99) {
                    LinearWavyProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    override fun onDestroy() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}

package com.dpm.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import org.json.JSONTokener

class WebLoginActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false
    private var closed = false

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
        super.onCreate(savedInstanceState)

        val bar = MaterialToolbar(this).apply {
            title = "Log in to Discord"
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        val progress = LinearProgressIndicator(this).apply {
            max = 100
            visibility = View.GONE
        }

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
                    progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                    progress.progress = newProgress
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    handler.removeCallbacks(poll)
                    handler.postDelayed(poll, 1200)
                }
            }
        }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(progress, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(web, LinearLayout.LayoutParams(MATCH, 0, 1f))
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
    }

    override fun onDestroy() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}

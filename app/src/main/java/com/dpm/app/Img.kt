package com.dpm.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object Img {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val http = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun sample(w: Int, h: Int, maxPx: Int): Int {
        var s = 1
        while (w / (s * 2) >= maxPx && h / (s * 2) >= maxPx) s *= 2
        return s
    }

    private fun decode(bytes: ByteArray, maxPx: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        val d = BitmapFactory.Options().apply { inSampleSize = sample(o.outWidth, o.outHeight, maxPx) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, d)
    }

    private fun fetch(url: String, maxPx: Int): Bitmap? =
        try {
            val bytes = http.newCall(Request.Builder().url(url).header("User-Agent", Api.UA).build())
                .execute().use { it.body?.bytes() }
            if (bytes == null) null else decode(bytes, maxPx)
        } catch (e: Exception) {
            null
        }

    fun load(v: ImageView, url: String, maxPx: Int) {
        val key = "$url@$maxPx"
        v.tag = key
        val hit = cache.get(key)
        if (hit != null) {
            v.setImageBitmap(hit)
            return
        }
        v.setImageDrawable(null)
        scope.launch {
            val b = fetch(url, maxPx) ?: return@launch
            cache.put(key, b)
            withContext(Dispatchers.Main) {
                if (v.tag == key) v.setImageBitmap(b)
            }
        }
    }
}

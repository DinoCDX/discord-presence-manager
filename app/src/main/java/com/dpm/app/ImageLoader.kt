package com.dpm.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object RemoteImages {
    private const val MAX_PX = 512

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        try {
            val bytes = Api.http.newCall(Request.Builder().url(url).header("User-Agent", Api.UA).build())
                .execute()
                .use { if (it.isSuccessful) it.body?.bytes() else null }
                ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_PX && bounds.outHeight / (sample * 2) >= MAX_PX) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
            )
            bitmap?.also { cache.put(url, it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fromFile(c: Context, name: String): Bitmap? = withContext(Dispatchers.IO) {
        StaticFiles.file(c, name)?.let { BitmapFactory.decodeFile(it.path) }
    }
}

@Composable
fun rememberPicture(name: String): Bitmap? {
    val ctx = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, name) {
        value = when {
            name.isEmpty() -> null
            name.startsWith("https://") -> RemoteImages.load(name)
            else -> RemoteImages.fromFile(ctx, name)
        }
    }
    return bitmap
}

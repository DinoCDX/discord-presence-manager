package com.dpm.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    private val client by lazy {
        Api.http.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
    }

    fun get(url: String): Pair<Int, String>? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            r.code to r.body?.string().orEmpty()
        }
    } catch (e: IOException) {
        null
    }

    fun head(url: String): Boolean? = try {
        client.newCall(Request.Builder().url(url).head().build()).execute().use { it.isSuccessful }
    } catch (e: IOException) {
        null
    }

    fun bitmap(url: String, maxSide: Int = 320): Bitmap? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) {
                null
            } else {
                val bytes = r.body?.bytes()
                if (bytes == null) {
                    null
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) {
                        sample *= 2
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
        }
    } catch (e: IOException) {
        null
    }
}

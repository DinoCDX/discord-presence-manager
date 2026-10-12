package com.dpm.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.util.LruCache
import java.io.ByteArrayOutputStream

object AppIcons {
    private val cache = LruCache<String, Bitmap>(300)

    fun cached(pkg: String, px: Int): Bitmap? = cache.get("$pkg@$px")

    fun get(c: Context, pkg: String, px: Int): Bitmap? {
        val key = "$pkg@$px"
        cache.get(key)?.let { return it }
        val b = render(c, pkg, px) ?: return null
        cache.put(key, b)
        return b
    }

    fun render(c: Context, pkg: String, px: Int): Bitmap? =
        try {
            val d = c.packageManager.getApplicationIcon(pkg)
            val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            if (d is AdaptiveIconDrawable) {
                val o = (px * 0.25f).toInt()
                d.setBounds(-o, -o, px + o, px + o)
            } else {
                d.bounds = Rect(0, 0, px, px)
            }
            d.draw(cv)
            b
        } catch (e: Exception) {
            null
        }

    fun square(src: Bitmap, px: Int): Bitmap {
        val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) ?: src else src
        val side = minOf(soft.width, soft.height)
        val x = (soft.width - side) / 2
        val y = (soft.height - side) / 2
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(soft, Rect(x, y, x + side, y + side), Rect(0, 0, px, px), null)
        return out
    }

    fun png(b: Bitmap): ByteArray =
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    fun jpeg(b: Bitmap, quality: Int = 88): ByteArray =
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}

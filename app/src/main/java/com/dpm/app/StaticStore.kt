package com.dpm.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Preset(
    val id: String,
    val label: String,
    val type: Int,
    val name: String,
    val details: String,
    val state: String,
    val timerMode: String,
    val durMin: Int,
    val large: String,
    val small: String,
    val b1l: String,
    val b1u: String,
    val b2l: String,
    val b2u: String
)

object Presets {
    fun all(c: Context): List<Preset> = try {
        val a = JSONArray(Store.get(c, "s_presets", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Preset(
                o.getString("id"), o.optString("label"), o.optInt("type"), o.optString("name"),
                o.optString("details"), o.optString("state"), o.optString("tm", "off"), o.optInt("dur", 60),
                o.optString("large"), o.optString("small"),
                o.optString("b1l"), o.optString("b1u"), o.optString("b2l"), o.optString("b2u")
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(c: Context, list: List<Preset>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("label", it.label).put("type", it.type).put("name", it.name)
                    .put("details", it.details).put("state", it.state).put("tm", it.timerMode).put("dur", it.durMin)
                    .put("large", it.large).put("small", it.small)
                    .put("b1l", it.b1l).put("b1u", it.b1u).put("b2l", it.b2l).put("b2u", it.b2u)
            )
        }
        Store.put(c, "s_presets", a.toString())
    }
}

object StaticFiles {
    private const val SIZE = 512

    fun dir(c: Context): File = File(c.filesDir, "static").also { it.mkdirs() }

    fun file(c: Context, name: String): File? =
        if (name.isBlank()) null else File(dir(c), name).takeIf { it.exists() }

    fun save(c: Context, uri: Uri): String? = try {
        val bytes = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 1024 && o.outHeight / (s * 2) >= 1024) s *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
            ?: return null
        val name = "img_${System.currentTimeMillis()}.jpg"
        File(dir(c), name).writeBytes(AppIcons.jpeg(AppIcons.square(bmp, SIZE), 90))
        name
    } catch (e: Exception) {
        null
    }

    fun inUse(c: Context, name: String): Boolean =
        name.isNotBlank() && (Store.get(c, "s_large", "") == name || Store.get(c, "s_small", "") == name ||
            Presets.all(c).any { it.large == name || it.small == name })

    fun prune(c: Context, name: String) {
        if (name.isNotBlank() && !name.startsWith("https://") && !inUse(c, name)) File(dir(c), name).delete()
    }
}

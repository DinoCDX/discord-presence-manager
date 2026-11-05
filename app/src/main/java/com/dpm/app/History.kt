package com.dpm.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object History {
    private const val KEY = "history"
    private const val MAX = 100

    class Entry(val at: Long, val type: Int, val name: String, val details: String, val state: String)

    @Synchronized
    fun record(c: Context, activities: JSONArray) {
        try {
            val a = activities.optJSONObject(0) ?: return
            val name = a.optString("name")
            if (name.isEmpty()) return
            val entry = JSONObject()
                .put("t", System.currentTimeMillis())
                .put("type", a.optInt("type"))
                .put("name", name)
                .put("details", a.optString("details"))
                .put("state", a.optString("state"))
            val old = JSONArray(Store.get(c, KEY, "[]"))
            val out = JSONArray().put(entry)
            for (i in 0 until minOf(old.length(), MAX - 1)) out.put(old.get(i))
            Store.put(c, KEY, out.toString())
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun list(c: Context): List<Entry> = try {
        val a = JSONArray(Store.get(c, KEY, "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Entry(o.optLong("t"), o.optInt("type"), o.optString("name"), o.optString("details"), o.optString("state"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun clear(c: Context) {
        Store.put(c, KEY, "[]")
    }
}

package com.dpm.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object History {
    private const val KEY = "history"
    private const val MAX = 100

    class Entry(val at: Long, val type: Int, val name: String, val details: String, val state: String)

    @Volatile
    private var memo: List<Entry>? = null

    private fun current(c: Context): List<Entry> {
        memo?.let { return it }
        val loaded = try {
            val a = JSONArray(Store.get(c, KEY, "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Entry(o.optLong("t"), o.optInt("type"), o.optString("name"), o.optString("details"), o.optString("state"))
            }
        } catch (e: Exception) {
            emptyList()
        }
        memo = loaded
        return loaded
    }

    @Synchronized
    fun record(c: Context, activities: JSONArray) {
        try {
            val a = activities.optJSONObject(0) ?: return
            val name = a.optString("name")
            if (name.isEmpty()) return
            val entry = Entry(System.currentTimeMillis(), a.optInt("type"), name, a.optString("details"), a.optString("state"))
            val next = (listOf(entry) + current(c)).take(MAX)
            memo = next
            Store.put(c, KEY, JSONArray(next.map { toJson(it) }).toString())
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun list(c: Context): List<Entry> = current(c)

    @Synchronized
    fun clear(c: Context) {
        memo = emptyList()
        Store.put(c, KEY, "[]")
    }

    private fun toJson(e: Entry): JSONObject =
        JSONObject()
            .put("t", e.at)
            .put("type", e.type)
            .put("name", e.name)
            .put("details", e.details)
            .put("state", e.state)
}

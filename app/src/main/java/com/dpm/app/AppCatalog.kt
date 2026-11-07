package com.dpm.app

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class AppEntry(val pkg: String, val label: String, val category: Int)

object AppCatalog {
    private const val KEY = "app_catalog"
    private const val STALE_MS = 10 * 60_000L

    private val state = MutableStateFlow<List<AppEntry>>(emptyList())
    val flow: StateFlow<List<AppEntry>> = state.asStateFlow()

    @Volatile
    private var seeded = false
    @Volatile
    private var loadedAt = 0L

    @Synchronized
    fun ensure(c: Context) {
        if (!seeded) {
            seeded = true
            state.value = restore(c)
        }
        if (state.value.isEmpty() || System.currentTimeMillis() - loadedAt > STALE_MS) refresh(c)
    }

    fun refresh(c: Context) {
        val app = c.applicationContext
        Bg.exec.execute {
            val list = load(app)
            state.value = list
            loadedAt = System.currentTimeMillis()
            persist(app, list)
        }
    }

    private fun load(app: Context): List<AppEntry> {
        val pm = app.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(q, 0)
            .map {
                AppEntry(
                    it.activityInfo.packageName,
                    it.loadLabel(pm).toString(),
                    it.activityInfo.applicationInfo.category
                )
            }
            .filter { it.pkg != app.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun persist(c: Context, list: List<AppEntry>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("p", it.pkg).put("l", it.label).put("c", it.category)) }
        Store.put(c, KEY, a.toString())
    }

    private fun restore(c: Context): List<AppEntry> = try {
        val a = JSONArray(Store.get(c, KEY, "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            AppEntry(o.getString("p"), o.getString("l"), o.optInt("c"))
        }
    } catch (e: Exception) {
        emptyList()
    }
}

package com.dpm.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class Account(val id: String, val name: String, val token: String)

class DiscordApp(val id: String, val name: String)

object Store {
    private const val ABSENT = "\u0000"

    private var sp: SharedPreferences? = null
    private val cache = ConcurrentHashMap<String, String>()
    private val pkgMemo = ConcurrentHashMap<String, Pair<String, Set<String>>>()

    @Volatile
    private var accountList: List<Account>? = null

    @Synchronized
    private fun p(c: Context): SharedPreferences {
        sp?.let { return it }
        val app = c.applicationContext
        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val created = EncryptedSharedPreferences.create(
            app,
            "secure_store",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        sp = created
        return created
    }

    fun get(c: Context, k: String, d: String): String {
        val hit = cache[k]
        if (hit != null) return if (hit == ABSENT) d else hit
        val v = p(c).getString(k, null)
        cache[k] = v ?: ABSENT
        return v ?: d
    }

    fun put(c: Context, k: String, v: String) {
        p(c).edit().putString(k, v).apply()
        cache[k] = v
    }

    private fun drop(c: Context, vararg keys: String) {
        val e = p(c).edit()
        keys.forEach {
            e.remove(it)
            cache[it] = ABSENT
        }
        e.apply()
    }

    fun pkgs(c: Context, k: String): Set<String> {
        val raw = get(c, k, "")
        val memo = pkgMemo[k]
        if (memo != null && memo.first == raw) return memo.second
        val set = raw.split("\n").filter { it.isNotBlank() }.toSet()
        pkgMemo[k] = raw to set
        return set
    }

    fun putPkgs(c: Context, k: String, v: Set<String>) = put(c, k, v.joinToString("\n"))

    private fun save(c: Context, list: List<Account>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("token", it.token)) }
        put(c, "accounts", a.toString())
        accountList = list
    }

    fun accounts(c: Context): List<Account> {
        accountList?.let { return it }

        drop(c, "token")
        val raw = get(c, "accounts", "")
        if (raw.isEmpty()) return emptyList()
        val all = try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Account(o.getString("id"), o.optString("name", "?"), o.getString("token"))
            }
        } catch (e: Exception) {
            return emptyList()
        }
        val list = all.filter { get(c, DiscordOAuth.refreshKey(it.id), "").isNotEmpty() }
        if (list.size != all.size) {
            save(c, list)
            if (list.isEmpty()) {
                drop(c, "active_id", "name", "uid")
            } else if (get(c, "active_id", "") !in list.map { it.id }) {
                setActive(c, list[0].id)
            }
        }
        accountList = list
        return list
    }

    fun active(c: Context): Account? {
        val list = accounts(c)
        val id = get(c, "active_id", "")
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    fun token(c: Context): String? = active(c)?.token

    fun setActive(c: Context, id: String) {
        val a = accounts(c).firstOrNull { it.id == id } ?: return
        put(c, "active_id", a.id)
        put(c, "name", a.name)
        put(c, "uid", a.id)
    }

    fun updateToken(c: Context, id: String, token: String) {
        save(c, accounts(c).map { if (it.id == id) Account(it.id, it.name, token) else it })
    }

    fun addAccount(c: Context, token: String, name: String, id: String) {
        val list = accounts(c).filter { it.id != id } + Account(id, name, token)
        save(c, list)
        setActive(c, id)
    }

    fun removeAccount(c: Context, id: String) {
        val wasActive = active(c)?.id == id
        val list = accounts(c).filter { it.id != id }
        save(c, list)
        if (!wasActive) return
        if (list.isNotEmpty()) {
            setActive(c, list[0].id)
        } else {
            drop(c, "active_id", "name", "uid")
        }
    }

    fun apps(c: Context): List<DiscordApp> {
        val raw = get(c, "apps", "")
        if (raw.isEmpty()) {
            val legacy = get(c, "app_id", "").trim()
            if (!Art.validId(legacy)) return emptyList()
            val migrated = listOf(DiscordApp(legacy, ""))
            saveApps(c, migrated)
            put(c, "active_app", legacy)
            return migrated
        }
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                DiscordApp(o.getString("id"), o.optString("name", ""))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveApps(c: Context, list: List<DiscordApp>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
        put(c, "apps", a.toString())
    }

    fun activeApp(c: Context): DiscordApp? {
        val id = get(c, "active_app", "")
        return apps(c).firstOrNull { it.id == id }
    }
}

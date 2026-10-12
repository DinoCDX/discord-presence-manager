package com.dpm.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class Account(val id: String, val name: String, val token: String)

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
        val legacy = get(c, "token", "")
        val raw = get(c, "accounts", "")
        if (legacy.isNotEmpty() && raw.isEmpty()) {
            val id = get(c, "uid", "").ifEmpty { "legacy" }
            val acc = Account(id, get(c, "name", "").ifEmpty { "Account" }, legacy)
            save(c, listOf(acc))
            drop(c, "token")
            put(c, "active_id", id)
            return listOf(acc)
        }
        if (raw.isEmpty()) return emptyList()
        return try {
            val a = JSONArray(raw)
            val list = (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Account(o.getString("id"), o.optString("name", "?"), o.getString("token"))
            }
            accountList = list
            list
        } catch (e: Exception) {
            emptyList()
        }
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

    fun logout(c: Context) {
        active(c)?.let { removeAccount(c, it.id) }
    }
}

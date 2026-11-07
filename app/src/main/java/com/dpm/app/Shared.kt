package com.dpm.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

object Shared {
    private val local = Any()

    private fun data(c: Context) = File(c.filesDir, "shared.json")

    private fun <T> locked(c: Context, block: () -> T): T = synchronized(local) {
        RandomAccessFile(File(c.filesDir, "shared.lock"), "rw").use { raf ->
            val lock = raf.channel.lock()
            try {
                block()
            } finally {
                lock.release()
            }
        }
    }

    private fun read(c: Context): JSONObject = try {
        JSONObject(data(c).readText())
    } catch (e: Exception) {
        JSONObject()
    }

    private fun write(c: Context, o: JSONObject) {
        val tmp = File(c.filesDir, "shared.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(data(c))) data(c).writeText(o.toString())
    }

    fun get(c: Context, k: String, d: String): String = locked(c) { read(c).optString(k, d) }

    fun put(c: Context, k: String, v: String) {
        putAll(c, mapOf(k to v))
    }

    fun putAll(c: Context, values: Map<String, String>) {
        locked(c) {
            val o = read(c)
            values.forEach { (k, v) -> o.put(k, v) }
            write(c, o)
        }
    }
}

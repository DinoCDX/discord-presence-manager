package com.dpm.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logs {
    private const val MAX_BYTES = 80_000L
    private const val KEEP_LINES = 200
    private val ver = MutableStateFlow(0)
    val version: StateFlow<Int> = ver.asStateFlow()
    private val time = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss", Locale.US) }

    private fun file(): File? = Procs.appContext?.let { File(it.filesDir, "logs.txt") }

    fun add(msg: String) {
        Log.i("DPM", msg)
        val f = file() ?: return
        val line = time.get()!!.format(Date()) + "  " + msg + "\n"
        try {
            f.appendText(line)
            if (f.length() > MAX_BYTES) trim(f)
        } catch (e: Exception) {
        }
    }

    private fun trim(f: File) {
        val keep = f.readLines().takeLast(KEEP_LINES)
        f.writeText(keep.joinToString("\n") + "\n")
    }

    fun text(): String {
        val f = file() ?: return "No events yet."
        return try {
            if (!f.exists()) "No events yet." else f.readLines().takeLast(KEEP_LINES).joinToString("\n")
        } catch (e: Exception) {
            "No events yet."
        }
    }

    fun clear() {
        file()?.delete()
        touch()
    }

    fun touch() {
        ver.value = ver.value + 1
    }
}

package com.dpm.app

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logs {
    private const val MAX_LINES = 300
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(msg: String) {
        val line = "${time.format(Date())}  $msg"
        Log.i("DPM", msg)
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    @Synchronized
    fun text(): String = if (lines.isEmpty()) "No events yet." else lines.joinToString("\n")

    @Synchronized
    fun clear() = lines.clear()
}

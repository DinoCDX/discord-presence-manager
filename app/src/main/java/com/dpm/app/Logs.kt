package com.dpm.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logs {
    private const val MAX_LINES = 300
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val ver = MutableStateFlow(0)
    val version: StateFlow<Int> = ver.asStateFlow()

    @Synchronized
    fun add(msg: String) {
        val line = "${time.format(Date())}  $msg"
        Log.i("DPM", msg)
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        ver.value = ver.value + 1
    }

    @Synchronized
    fun text(): String = if (lines.isEmpty()) "No events yet." else lines.takeLast(200).joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
        ver.value = ver.value + 1
    }
}

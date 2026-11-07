package com.dpm.app

import android.content.Context

object TickRate {
    val OPTIONS = listOf(5, 10, 15, 30)

    fun onSec(c: Context): Int = Store.get(c, "tick_on", "5").toIntOrNull()?.takeIf { it in OPTIONS } ?: 5

    fun offSec(c: Context): Int = Store.get(c, "tick_off", "15").toIntOrNull()?.takeIf { it in OPTIONS } ?: 15
}

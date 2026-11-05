package com.dpm.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object Net {
    const val ANY = "any"
    const val WIFI = "wifi"
    const val MOBILE = "mobile"

    fun rule(c: Context): String = Store.get(c, "net_rule", ANY)

    fun setRule(c: Context, r: String) = Store.put(c, "net_rule", r)

    fun allowed(c: Context): Boolean {
        val r = rule(c)
        if (r == ANY) return true
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }
            .filter {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        return when (r) {
            WIFI -> caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
            MOBILE -> caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) }
            else -> true
        }
    }

    fun waitingText(c: Context): String = when (rule(c)) {
        WIFI -> "Waiting for Wi-Fi"
        MOBILE -> "Waiting for mobile data"
        else -> "Connection rule"
    }
}

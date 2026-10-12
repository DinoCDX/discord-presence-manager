package com.dpm.app

import android.app.Application
import android.content.Context
import com.google.android.material.color.DynamicColors

class DPMApp : Application() {
    companion object {
        lateinit var ctx: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        val dyn = try { Store.get(this, "dyn", "1") == "1" } catch (e: Exception) { true }
        if (dyn) DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

package com.dpm.app

import android.app.Application
import android.content.Context

class DPMApp : Application() {
    companion object {
        lateinit var ctx: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
    }
}

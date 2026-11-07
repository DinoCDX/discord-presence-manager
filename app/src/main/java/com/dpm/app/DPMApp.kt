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
        Procs.appContext = applicationContext
        Procs.inService = Application.getProcessName().endsWith(":presence")
        if (!Procs.inService) Live.watch(this)
    }
}

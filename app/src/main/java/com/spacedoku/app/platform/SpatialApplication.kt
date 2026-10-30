package com.spacedoku.app.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.spacedoku.app.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this) // capture any uncaught crash to a file + Logcat
        launch(::mainApp)
    }
}

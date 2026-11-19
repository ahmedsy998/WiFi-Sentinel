package com.wifisentinel

import android.app.Application
import com.wifisentinel.di.AppContainer

class WifiSentinelApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

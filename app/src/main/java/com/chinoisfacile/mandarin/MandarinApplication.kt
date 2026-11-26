package com.chinoisfacile.mandarin

import android.app.Application

class MandarinApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTerminate() {
        container.release()
        super.onTerminate()
    }
}

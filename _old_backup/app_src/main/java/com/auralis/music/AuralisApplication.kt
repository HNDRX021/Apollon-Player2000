package com.auralis.music

import android.app.Application
import com.auralis.music.core.AppContainer

class AuralisApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

package com.blaze.photobooth

import android.app.Application
import android.content.Context

class BoothApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Manual DI root (README §8.1). Repositories, stores and controllers are added here
 * milestone by milestone; for now it only holds the application context.
 */
class AppContainer(val appContext: Context)

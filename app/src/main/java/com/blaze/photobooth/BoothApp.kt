package com.blaze.photobooth

import android.app.Application
import android.content.Context
import com.blaze.photobooth.ui.feature.home.EmptyHomeSource
import com.blaze.photobooth.ui.feature.home.HomeSource

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
 * milestone by milestone.
 */
class AppContainer(val appContext: Context) {
    /** Album count, newest strip and draft for Home. Empty until the album (M5) and draft store (M3) exist. */
    val homeSource: HomeSource = EmptyHomeSource
}

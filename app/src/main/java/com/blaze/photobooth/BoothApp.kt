package com.blaze.photobooth

import android.app.Application
import android.content.Context
import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.data.prefs.BoothPreferences
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

    /** Her frames and the built-in themes (§5.5.3). Imported frames plug in here in M7. */
    val frameRepository: FrameRepository by lazy { FrameRepository(appContext) }

    /** The last Setup choices (§11.6). */
    val boothPreferences: BoothPreferences by lazy { BoothPreferences(appContext) }
}

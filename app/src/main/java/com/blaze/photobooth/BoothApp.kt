package com.blaze.photobooth

import android.app.Application
import android.content.Context
import android.util.Log
import com.blaze.photobooth.data.SessionRepository
import com.blaze.photobooth.data.db.BoothDatabase
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.data.prefs.BoothPreferences
import com.blaze.photobooth.render.StripExporter
import com.blaze.photobooth.render.StripRenderer
import com.blaze.photobooth.ui.feature.home.BoothHomeSource
import com.blaze.photobooth.ui.feature.home.HomeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class BoothApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // A session whose row didn't make it into the DB (process death mid-print) comes back (§11.1).
        container.appScope.launch {
            try {
                container.sessionRepository.recoverOrphans()
            } catch (e: Exception) {
                Log.e("BoothApp", "Orphan check failed", e)
            }
        }
    }
}

/**
 * Manual DI root (README §8.1). Repositories, stores and controllers are added here
 * milestone by milestone.
 */
class AppContainer(val appContext: Context) {
    /** The in-progress session, mirrored to `files/sessions/_draft/` (§11.4). */
    val draftStore: BoothDraftStore by lazy { BoothDraftStore(File(appContext.filesDir, SessionsDir)) }

    /** Album count, newest strip and draft for Home. The draft is real since M3; the album arrives in M5. */
    val homeSource: HomeSource by lazy { BoothHomeSource(draftStore) }

    /** Her frames and the built-in themes (§5.5.3). Imported frames plug in here in M7. */
    val frameRepository: FrameRepository by lazy { FrameRepository(appContext) }

    /** The last Setup choices (§11.6). */
    val boothPreferences: BoothPreferences by lazy { BoothPreferences(appContext) }

    /** App-wide work that outlives any screen (startup checks). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** `booth.db` (§11.2). */
    val database: BoothDatabase by lazy { BoothDatabase.create(appContext) }

    /** Saved sessions: Room rows plus `files/sessions/{id}/` (§11). */
    val sessionRepository: SessionRepository by lazy {
        SessionRepository(database.sessions(), SessionFileStore(File(appContext.filesDir, SessionsDir)), draftStore)
    }

    /** Print: the strip at full size (§10). */
    val stripExporter: StripExporter by lazy { StripExporter(StripRenderer.get(appContext)) }

    companion object {
        /** `files/sessions/` (§11.1): the draft and, from M4, every finished session. */
        const val SessionsDir = "sessions"
    }
}

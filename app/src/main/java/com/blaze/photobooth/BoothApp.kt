package com.blaze.photobooth

import android.app.Application
import android.content.Context
import android.util.Log
import com.blaze.photobooth.data.SessionRepository
import com.blaze.photobooth.data.db.BoothDatabase
import com.blaze.photobooth.data.export.BoothExportService
import com.blaze.photobooth.data.export.ExportService
import com.blaze.photobooth.data.export.PhotoExporter
import com.blaze.photobooth.data.export.ResolverGallery
import com.blaze.photobooth.data.export.ShareHelper
import com.blaze.photobooth.data.export.StripAutoSaver
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.data.frames.CustomFrameStore
import com.blaze.photobooth.data.frames.FrameImporter
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
        // Startup checks (§11.1, §11.2): a session that lost its row comes back, an interrupted edit is finished,
        // old soft deletes are purged, and lost thumbnails are made again.
        container.appScope.launch {
            container.sessionRepository.startup(makeThumb = container.stripExporter::writeThumb)
            // After the purge: deleted frames that no strip uses any more go for good (§11.2).
            try {
                container.customFrames.cleanup()
            } catch (e: Exception) {
                Log.e("BoothApp", "Frame cleanup failed", e)
            }
            // Old shared copies (§11.5) and any half-made export from a crash.
            container.shareHelper.cleanOld()
            container.exportWorkDir.deleteRecursively()
        }
    }

    /** Low memory (M9): caches that are quick to rebuild give memory back. Coil trims its own cache. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::container.isInitialized) container.trimMemory(level)
    }
}

/**
 * Manual DI root (README §8.1). Repositories, stores and controllers are added here
 * milestone by milestone.
 */
class AppContainer(val appContext: Context) {
    /** The in-progress session, mirrored to `files/sessions/_draft/` (§11.4). */
    val draftStore: BoothDraftStore by lazy { BoothDraftStore(File(appContext.filesDir, SessionsDir)) }

    /** Album count, newest strip and draft for Home (§4.1). */
    val homeSource: HomeSource by lazy { BoothHomeSource(draftStore, sessionRepository) }

    private val frameRepositoryLazy = lazy { FrameRepository(appContext, customFrames, boothPreferences.hiddenBundledFrames) }

    /** Her frames (bundled and imported) and the built-in themes (§5.5.3). */
    val frameRepository: FrameRepository by frameRepositoryLazy

    /** `onTrimMemory`: only what already exists is trimmed (nothing is created just to be emptied). */
    fun trimMemory(level: Int) {
        if (frameRepositoryLazy.isInitialized()) frameRepository.trimMemory(level)
    }

    /** Frames imported on the phone: the `frames` table plus `files/frames/` (§4.9, §11.2). */
    val customFrames: CustomFrameStore by lazy {
        val sessions = database.sessions()
        CustomFrameStore(
            dao = database.frames(),
            dir = File(appContext.filesDir, FramesDir),
            // The draft counts too: a frame being shot with right now must not lose its files.
            framesInUse = { sessions.frameIdsInUse().toSet() + listOfNotNull(draftStore.current()?.config?.frame) },
            sessionsUsing = sessions::countUsing,
        )
    }

    /** "+ Add frame" (§4.9). */
    val frameImporter: FrameImporter by lazy { FrameImporter(appContext, customFrames) }

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

    /** Where a file for Photos is made before it's streamed into MediaStore (§11.5). */
    val exportWorkDir: File get() = File(appContext.cacheDir, "export")

    /** Save to Photos (§11.5). */
    val photoExporter: PhotoExporter by lazy {
        PhotoExporter(ResolverGallery(appContext.contentResolver), sessionRepository.files, exportWorkDir)
    }

    /** The system share sheet (§11.5). */
    val shareHelper: ShareHelper by lazy { ShareHelper(appContext, photoExporter) }

    /** Save and Share for Result, Detail and the Album's multi-select. */
    val exports: ExportService by lazy { BoothExportService(photoExporter, shareHelper) }

    /** Settings' *Auto-save strip to Photos after printing* (§4.8). */
    val autoSaver: StripAutoSaver by lazy {
        StripAutoSaver(boothPreferences.settings, sessionRepository::session, { photoExporter.save(it) }, appScope)
    }

    companion object {
        /** `files/sessions/` (§11.1): the draft and, from M4, every finished session. */
        const val SessionsDir = "sessions"

        /** `files/frames/` (§11.1): one folder per imported frame. */
        const val FramesDir = "frames"
    }
}

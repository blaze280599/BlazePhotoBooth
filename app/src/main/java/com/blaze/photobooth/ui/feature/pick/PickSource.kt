package com.blaze.photobooth.ui.feature.pick

import com.blaze.photobooth.data.SessionRepository
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.db.ShotEntity
import com.blaze.photobooth.data.files.BoothDraft
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.StripExporter
import com.blaze.photobooth.ui.feature.album.AlbumGrouping
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Where Pick's shots come from and where its strip goes (README §4.4, §4.7). One screen, two sources:
 * [DraftPickSource] for a booth session that was just shot (every change is written to the draft, Print saves it to
 * the album) and [SavedPickSource] for Edit (changes stay in memory until Save, which re-renders the saved strip;
 * Cancel leaves everything as it was, and the draft is never touched).
 */
interface PickSource {
    /** Edit mode: Save instead of Print, no retakes, ✕ and back simply cancel. */
    val editing: Boolean

    /** The session to pick from; null once it's gone. A draft emits again after a retake. */
    val session: Flow<PickSession?>

    fun shotFile(index: Int): File

    /** Remembers the picks (the draft does; Edit keeps them in memory). */
    suspend fun savePick(selection: List<Int?>, caption: String?, showDate: Boolean)

    /** Remembers the frame and filter (the draft does; Edit keeps them in memory). */
    suspend fun saveConfig(config: BoothConfig)

    /** Print ✿ or Save: renders [spec] with [result]'s photos and stores it. Returns the session id. */
    suspend fun commit(spec: StripSpec, result: PickResult): String

    /** "Retake everything?" (draft only): a fresh draft with the same choices. */
    suspend fun restart(config: BoothConfig, totalShots: Int)

    /** "Discard session?" (draft only). */
    suspend fun discard()
}

/** What Pick starts from. [stampDate] is the date printed on the strip; null means today (a new session). */
data class PickSession(
    val config: BoothConfig,
    val totalShots: Int,
    val shots: List<PickSourceShot>,
    val selection: List<Int?>,
    val caption: String?,
    val showDate: Boolean,
    val stampDate: LocalDate? = null,
)

/** A shot Pick can show. [version] changes when the shot is retaken. */
data class PickSourceShot(val index: Int, val version: Long)

/** What Pick hands over on Print or Save. [selection]: slot → shot index. */
data class PickResult(val config: BoothConfig, val selection: List<Int?>, val caption: String?, val showDate: Boolean)

/** A booth session that was just shot (§4.4): the draft (§11.4). Print finalises it into the album (§11.1). */
class DraftPickSource(
    private val draftStore: BoothDraftStore,
    private val sessions: SessionRepository,
    private val exporter: StripExporter,
    /** Settings' auto-save (§4.8): told about every printed session. */
    private val onPrinted: suspend (sessionId: String) -> Unit = {},
) : PickSource {
    override val editing = false

    override val session: Flow<PickSession?> = draftStore.draft.map { it?.toPickSession() }

    override fun shotFile(index: Int): File = draftStore.shotFile(index)

    override suspend fun savePick(selection: List<Int?>, caption: String?, showDate: Boolean) {
        draftStore.updatePick(selection, caption, showDate)
    }

    override suspend fun saveConfig(config: BoothConfig) {
        draftStore.updateConfig(config)
    }

    override suspend fun commit(spec: StripSpec, result: PickResult): String {
        draftStore.updatePick(result.selection, result.caption, result.showDate)
        draftStore.updateConfig(result.config)
        val id = sessions.finalizeDraft { _, dir ->
            val photos = result.selection.map { i -> i?.let { File(dir, SessionFileStore.shotName(it)) } }
            exporter.export(spec, photos, dir)
        }
        onPrinted(id)
        return id
    }

    override suspend fun restart(config: BoothConfig, totalShots: Int) {
        draftStore.begin(config, totalShots)
    }

    override suspend fun discard() {
        draftStore.discard()
    }

    private fun BoothDraft.toPickSession() = PickSession(
        config = config.toConfig(),
        totalShots = totalShots,
        shots = shots.map { PickSourceShot(it.index, it.takenAt) },
        selection = selection,
        caption = caption,
        showDate = showDate,
    )
}

/**
 * Edit (§4.7): a saved session. Every shot can be re-picked, bonus shots included; frame, filter, caption and date
 * can change. Save re-renders `strip.jpg` and `thumb.jpg` at full size and updates the rows
 * ([SessionRepository.saveEdit]); nothing is written before that.
 */
class SavedPickSource(
    private val sessionId: String,
    private val sessions: SessionRepository,
    private val exporter: StripExporter,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PickSource {
    override val editing = true

    override val session: Flow<PickSession?> = flow {
        val session = sessions.session(sessionId)?.takeIf { it.deletedAt == null }
        val shots = session?.let { sessions.shots(sessionId) }.orEmpty()
        val present = withContext(io) { shots.filter { sessions.files.shot(sessionId, it.shotIndex).isFile } }
        emit(session?.let { toPickSession(it, present, zone()) })
    }

    override fun shotFile(index: Int): File = sessions.files.shot(sessionId, index)

    override suspend fun savePick(selection: List<Int?>, caption: String?, showDate: Boolean) = Unit

    override suspend fun saveConfig(config: BoothConfig) = Unit

    override suspend fun commit(spec: StripSpec, result: PickResult): String {
        // Every shot row gets its new slot (or none), including shots whose file has gone missing.
        val shots = sessions.shots(sessionId).map { it.shotIndex }
        val edit = SessionRepository.editFor(result.config, result.selection, shots, result.caption, result.showDate)
        val photos = result.selection.map { i -> i?.let { sessions.files.shot(sessionId, it) } }
        sessions.saveEdit(sessionId, edit) { dir -> exporter.export(spec, photos, dir) }
        return sessionId
    }

    override suspend fun restart(config: BoothConfig, totalShots: Int) = Unit

    override suspend fun discard() = Unit

    companion object {
        /** A saved session's rows as Pick's starting point (§4.7). Pure. */
        fun toPickSession(session: SessionEntity, shots: List<ShotEntity>, zone: ZoneId): PickSession {
            val defaults = BoothConfig()
            val config = BoothConfig(
                layout = BoothLayout.fromId(session.layoutId) ?: defaults.layout,
                frameId = FrameId(session.frameId).takeIf { it.kind != null } ?: defaults.frameId,
                filter = PhotoFilter.fromId(session.filterId) ?: PhotoFilter.Original,
            )
            val slots = shots.mapNotNull { it.slotIndex }
            val selection = MutableList<Int?>((slots.maxOrNull() ?: -1) + 1) { null }
            shots.forEach { shot -> shot.slotIndex?.let { selection[it] = shot.shotIndex } }
            return PickSession(
                config = config,
                totalShots = shots.size,
                shots = shots.map { PickSourceShot(it.shotIndex, version = 0) },
                selection = selection,
                caption = session.caption,
                showDate = session.showDate,
                stampDate = AlbumGrouping.dateOf(session.createdAt, zone),
            )
        }
    }
}

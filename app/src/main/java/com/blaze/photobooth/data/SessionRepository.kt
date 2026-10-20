package com.blaze.photobooth.data

import android.util.Log
import com.blaze.photobooth.data.db.SessionDao
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.db.ShotEntity
import com.blaze.photobooth.data.files.BoothDraft
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.FrameId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The album (README §8.2, §11): saved sessions in Room plus their folders under `files/sessions/`.
 * [finalizeDraft] turns the draft into a session the moment it's printed, so "Saved to your album ♡" is true
 * before Result even shows. Since M5: favourites, soft delete with one Undo per batch ([pendingUndo]), the
 * startup purge of old soft deletes, and Edit ([saveEdit]).
 */
class SessionRepository(
    private val dao: SessionDao,
    val files: SessionFileStore,
    private val draftStore: BoothDraftStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Finalising, editing and the startup checks never overlap. */
    private val lock = Mutex()

    /** Favourite writes land in the order they were tapped (a fair mutex), however fast she taps. */
    private val favoriteLock = Mutex()

    private val undo = MutableStateFlow<DeletedBatch?>(null)

    val sessions: Flow<List<SessionEntity>> = dao.observeSessions()
    val count: Flow<Int> = dao.observeCount()

    /** The newest strip (Home's peek, §4.1). */
    val latest: Flow<SessionEntity?> = dao.observeLatest()

    /**
     * The last delete that can still be undone (§4.7). Album shows its snackbar, also for a delete made in
     * Detail, which then hands over to Album.
     */
    val pendingUndo: StateFlow<DeletedBatch?> = undo.asStateFlow()

    fun observe(id: String): Flow<SessionEntity?> = dao.observeSession(id)

    suspend fun session(id: String): SessionEntity? = dao.session(id)

    suspend fun shots(id: String): List<ShotEntity> = dao.shots(id)

    suspend fun setFavorite(id: String, favorite: Boolean) = favoriteLock.withLock {
        dao.setFavorite(id, favorite, clock())
    }

    /**
     * Soft delete (§11.2): the sessions leave the album at once, but their rows and files stay until a later app
     * start ([purgeDeleted]), so [restore] can bring them back. The batch becomes [pendingUndo].
     */
    suspend fun softDelete(ids: Collection<String>): DeletedBatch? {
        if (ids.isEmpty()) return null
        val batch = DeletedBatch(ids.distinct(), at = clock())
        dao.softDeleteAll(batch.ids, batch.at)
        undo.value = batch
        Log.i(Tag, "Soft-deleted ${batch.ids.size} session(s)")
        return batch
    }

    /** Undo: every session of [batch] comes back. */
    suspend fun restore(batch: DeletedBatch) {
        dao.restoreAll(batch.ids)
        undo.compareAndSet(batch, null)
        Log.i(Tag, "Restored ${batch.ids.size} session(s)")
    }

    /** The Undo snackbar for [batch] went away without Undo. */
    fun dismissUndo(batch: DeletedBatch) {
        undo.compareAndSet(batch, null)
    }

    /**
     * Print (§11.1): [render] writes `strip.jpg` and `thumb.jpg` into the draft folder, then the folder is
     * atomically renamed to `sessions/{uuid}` and the rows are inserted. If the insert fails, the rename is
     * undone and the draft is exactly as it was. Every shot is kept, including unused bonus shots.
     * Returns the new session's id.
     */
    suspend fun finalizeDraft(render: suspend (draft: BoothDraft, dir: File) -> Unit): String = lock.withLock {
        draftStore.handOff { draft, dir ->
            render(draft, dir)
            check(withContext(io) { File(dir, SessionFileStore.StripName).isFile }) { "No strip was rendered" }
            val id = newId()
            val target = files.dir(id)
            val rows = rowsFor(draft, id, now = clock())
            withContext(io) { files.moveDir(dir, target) }
            try {
                dao.insert(rows.first, rows.second)
            } catch (e: Throwable) {
                // The rows didn't land: put the folder back so the draft (and every photo) is still there.
                withContext(NonCancellable + io) { files.moveDir(target, dir) }
                throw e
            }
            withContext(io) { File(target, BoothDraftStore.DraftFileName).delete() }
            Log.i(Tag, "Saved session $id: ${rows.second.size} shots, ${rows.second.count { it.slotIndex != null }} in the strip")
            id
        }
    }

    /**
     * Edit (§4.7): [render] writes the new `strip.jpg` and `thumb.jpg` into a staging folder inside the session's
     * folder. Then the rows are updated in one transaction (stamped with the staging folder's stamp) and the files
     * replace the old ones. A failed render or update changes nothing. If the app dies after the update, the next
     * start finishes the move ([finishEdits]), so a saved edit is never lost. The draft is never touched.
     */
    suspend fun saveEdit(id: String, edit: SessionEdit, render: suspend (dir: File) -> Unit) = lock.withLock {
        val current = checkNotNull(dao.session(id)) { "No session $id" }
        val stamp = maxOf(clock(), current.updatedAt + 1)
        val staging = files.editDir(id, stamp)
        try {
            withContext(io) {
                staging.deleteRecursively()
                check(staging.mkdirs()) { "Couldn't create $staging" }
            }
            render(staging)
            val rendered = withContext(io) {
                File(staging, SessionFileStore.StripName).isFile && File(staging, SessionFileStore.ThumbName).isFile
            }
            check(rendered) { "No strip was rendered" }
            val updated = current.copy(
                layoutId = edit.layoutId,
                frameId = edit.frameId,
                filterId = edit.filterId,
                caption = edit.caption,
                showDate = edit.showDate,
                updatedAt = stamp,
            )
            dao.applyEdit(updated, edit.slots)
        } catch (e: Throwable) {
            withContext(NonCancellable + io) { staging.deleteRecursively() }
            throw e
        }
        withContext(NonCancellable + io) { promote(id, staging) }
        Log.i(Tag, "Edited session $id")
    }

    /** Moves whatever a staging folder still holds over the session's files, then removes it. */
    private fun promote(id: String, staging: File) {
        File(staging, SessionFileStore.StripName).takeIf { it.isFile }?.let { files.replace(it, files.strip(id)) }
        File(staging, SessionFileStore.ThumbName).takeIf { it.isFile }?.let { files.replace(it, files.thumb(id)) }
        staging.deleteRecursively()
    }

    /**
     * A process death between the rename and the insert leaves a session folder with no row. Its
     * `draft.json` still describes it, so the session is re-inserted (never lose a photo, §1). Folders
     * without one are left alone. Returns how many sessions came back.
     */
    suspend fun recoverOrphans(): Int = lock.withLock {
        val known = dao.allIds().toSet()
        var recovered = 0
        for (id in withContext(io) { files.sessionIds() } - known) {
            val draftFile = File(files.dir(id), BoothDraftStore.DraftFileName)
            val draft = withContext(io) {
                draftFile.takeIf { it.isFile && files.strip(id).isFile }?.readText()?.let(BoothDraftStore::decode)
            } ?: continue
            val present = draft.copy(shots = draft.shots.filter { files.shot(id, it.index).isFile })
            val (session, shots) = rowsFor(present, id, now = draftFile.lastModified())
            dao.insert(session, shots)
            withContext(io) { draftFile.delete() }
            Log.w(Tag, "Recovered session $id from its draft.json")
            recovered++
        }
        recovered
    }

    /**
     * Startup (§4.7): a staging folder whose stamp the row already carries is an edit that was saved but not yet
     * moved into place, so the move is finished. Any other staging folder is an edit that never landed: dropped.
     */
    suspend fun finishEdits(): Int = lock.withLock {
        var finished = 0
        for (id in withContext(io) { files.sessionIds() }) {
            val leftovers = withContext(io) { files.editDirs(id) }
            if (leftovers.isEmpty()) continue
            val updatedAt = dao.session(id)?.updatedAt
            withContext(io) {
                for ((dir, stamp) in leftovers) {
                    if (updatedAt != null && updatedAt >= stamp) {
                        promote(id, dir)
                        finished++
                        Log.w(Tag, "Finished an interrupted edit of $id")
                    } else {
                        dir.deleteRecursively()
                    }
                }
            }
        }
        finished
    }

    /**
     * Startup (§11.2): sessions soft-deleted more than [PurgeAfterMillis] ago are removed for good, folder first
     * and then the row. A crash in between leaves a hidden row that the next start removes, never a lost folder.
     */
    suspend fun purgeDeleted(): Int = lock.withLock {
        var purged = 0
        for (id in dao.deletedBefore(clock() - PurgeAfterMillis)) {
            if (withContext(io) { files.deleteDir(id) }) {
                dao.delete(id)
                purged++
            } else {
                Log.w(Tag, "Couldn't remove the folder of $id; trying again next time")
            }
        }
        if (purged > 0) Log.i(Tag, "Purged $purged deleted session(s)")
        purged
    }

    /** Startup: a missing `thumb.jpg` is made again from `strip.jpg` by [make]. The album shows the strip meanwhile. */
    suspend fun repairThumbs(make: suspend (strip: File, thumb: File) -> Unit): Int = lock.withLock {
        var repaired = 0
        for (id in dao.allIds()) {
            val strip = files.strip(id)
            val thumb = files.thumb(id)
            if (withContext(io) { thumb.isFile || !strip.isFile }) continue
            try {
                make(strip, thumb)
                repaired++
                Log.w(Tag, "Made a new thumbnail for $id")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(Tag, "Couldn't make a thumbnail for $id", e)
            }
        }
        repaired
    }

    /** Everything the app checks once at startup, in this order. One failing check doesn't stop the others. */
    suspend fun startup(makeThumb: suspend (strip: File, thumb: File) -> Unit) {
        runStep("Orphan check") { recoverOrphans() }
        runStep("Edit check") { finishEdits() }
        runStep("Purge") { purgeDeleted() }
        runStep("Thumbnail check") { repairThumbs(makeThumb) }
    }

    private suspend fun runStep(name: String, step: suspend () -> Unit) {
        try {
            step()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(Tag, "$name failed", e)
        }
    }

    companion object {
        private const val Tag = "SessionRepository"

        /** Soft deletes older than this are purged at startup (§11.2): well past the 5 s Undo. */
        const val PurgeAfterMillis = 10_000L

        /** `layout_id` (§11.2): the layout for themes, [SessionEntity.ImageLayoutId] for image frames. */
        fun layoutIdFor(config: BoothConfig): String =
            if (config.frameId.kind == FrameId.Kind.Theme) config.layout.id else SessionEntity.ImageLayoutId

        /** The `sessions` row and every `shots` row for a printed [draft] (§11.2). Pure. */
        fun rowsFor(draft: BoothDraft, id: String, now: Long): Pair<SessionEntity, List<ShotEntity>> {
            val config = draft.config.toConfig()
            val session = SessionEntity(
                id = id,
                createdAt = now,
                updatedAt = now,
                layoutId = layoutIdFor(config),
                frameId = config.frameId.value,
                filterId = config.filter.id,
                caption = draft.caption?.trim()?.takeIf { it.isNotEmpty() },
                showDate = draft.showDate,
            )
            val shots = draft.shots.map { shot ->
                ShotEntity(
                    sessionId = id,
                    shotIndex = shot.index,
                    slotIndex = draft.selection.indexOf(shot.index).takeIf { it >= 0 },
                    width = shot.width,
                    height = shot.height,
                )
            }
            return session to shots
        }

        /** What Edit saves (§4.7) for [selection] (slot → shot index), covering every shot in [shots]. Pure. */
        fun editFor(config: BoothConfig, selection: List<Int?>, shots: List<Int>, caption: String?, showDate: Boolean) = SessionEdit(
            layoutId = layoutIdFor(config),
            frameId = config.frameId.value,
            filterId = config.filter.id,
            caption = caption?.trim()?.takeIf { it.isNotEmpty() },
            showDate = showDate,
            slots = shots.associateWith { shot -> selection.indexOf(shot).takeIf { it >= 0 } },
        )
    }
}

/** Sessions deleted together; one Undo restores them all. [at] is when (epoch millis). */
data class DeletedBatch(val ids: List<String>, val at: Long)

/** An edited session's new look (§4.7). [slots] maps every shot index to its slot, or null when it's unused. */
data class SessionEdit(
    val layoutId: String,
    val frameId: String,
    val filterId: String,
    val caption: String?,
    val showDate: Boolean,
    val slots: Map<Int, Int?>,
)

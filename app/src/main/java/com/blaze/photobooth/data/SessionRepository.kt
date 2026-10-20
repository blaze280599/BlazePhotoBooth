package com.blaze.photobooth.data

import android.util.Log
import com.blaze.photobooth.data.db.SessionDao
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.db.ShotEntity
import com.blaze.photobooth.data.files.BoothDraft
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.model.FrameId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The album (README §8.2, §11): saved sessions in Room plus their folders under `files/sessions/`.
 * [finalizeDraft] turns the draft into a session the moment it's printed, so "Saved to your album ♡" is true
 * before Result even shows.
 */
class SessionRepository(
    private val dao: SessionDao,
    val files: SessionFileStore,
    private val draftStore: BoothDraftStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Finalising and orphan recovery never overlap. */
    private val lock = Mutex()

    val sessions: Flow<List<SessionEntity>> = dao.observeSessions()
    val count: Flow<Int> = dao.observeCount()

    fun observe(id: String): Flow<SessionEntity?> = dao.observeSession(id)

    suspend fun session(id: String): SessionEntity? = dao.session(id)

    suspend fun shots(id: String): List<ShotEntity> = dao.shots(id)

    /**
     * Print (§11.1): [render] writes `strip.jpg` and `thumb.jpg` into the draft folder, then the folder is
     * atomically renamed to `sessions/{uuid}` and the rows are inserted. If the insert fails, the rename is
     * undone and the draft is exactly as it was. Every shot is kept, including unused bonus shots.
     * Returns the new session's id.
     */
    suspend fun finalizeDraft(render: suspend (draft: BoothDraft, dir: File) -> Unit): String = lock.withLock {
        draftStore.handOff { draft, dir ->
            render(draft, dir)
            check(File(dir, SessionFileStore.StripName).isFile) { "No strip was rendered" }
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

    companion object {
        private const val Tag = "SessionRepository"

        /** The `sessions` row and every `shots` row for a printed [draft] (§11.2). Pure. */
        fun rowsFor(draft: BoothDraft, id: String, now: Long): Pair<SessionEntity, List<ShotEntity>> {
            val config = draft.config.toConfig()
            val session = SessionEntity(
                id = id,
                createdAt = now,
                updatedAt = now,
                layoutId = if (config.frameId.kind == FrameId.Kind.Theme) config.layout.id else SessionEntity.ImageLayoutId,
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
    }
}

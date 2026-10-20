package com.blaze.photobooth.data.files

import android.util.Log
import com.blaze.photobooth.model.BoothConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The in-progress session (README §8.2, §11.4), shared by Setup → Capture → Pick → Result and mirrored
 * to `sessions/_draft/` so a process death never loses a shot. `draft.json` is rewritten (atomically)
 * after every change. One instance lives in `AppContainer`.
 */
class BoothDraftStore(
    sessionsDir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val dir = File(sessionsDir, DraftDirName)
    private val file = File(dir, DraftFileName)

    private val lock = Mutex()
    private val state = MutableStateFlow<BoothDraft?>(null)
    private var loaded = false

    /** The current draft (null = none), read from disk on first use. */
    val draft: Flow<BoothDraft?> = flow {
        current()
        emitAll(state)
    }

    suspend fun current(): BoothDraft? = lock.withLock {
        ensureLoaded()
        state.value
    }

    /** Starts a fresh session, wiping any previous draft and its shots. */
    suspend fun begin(config: BoothConfig, totalShots: Int): BoothDraft = checkNotNull(
        edit {
            dir.deleteRecursively()
            BoothDraft(createdAt = clock(), config = DraftConfig.from(config), totalShots = totalShots)
        },
    )

    /** Records a shot whose file is already in place at [shotFile]; a retake of the same index replaces it. */
    suspend fun addShot(shot: DraftShot): BoothDraft = checkNotNull(
        edit { d ->
            val draft = checkNotNull(d) { "No draft to add a shot to" }
            draft.copy(shots = (draft.shots.filterNot { it.index == shot.index } + shot).sortedBy { it.index })
        },
    )

    /** E.g. the camera was flipped mid-session. */
    suspend fun updateConfig(config: BoothConfig): BoothDraft? = edit { d -> d?.copy(config = DraftConfig.from(config)) }

    /** Discard (Home's draft card, or leaving the booth): deletes `_draft/` and everything in it. */
    suspend fun discard() {
        lock.withLock {
            loaded = true
            withContext(io) { dir.deleteRecursively() }
            state.value = null
        }
    }

    fun shotFile(index: Int) = File(dir, "shot_$index.jpg")

    /** Written first, then renamed onto [shotFile], so a half-written JPEG never looks like a shot. */
    fun pendingShotFile(index: Int) = File(dir, "shot_$index.jpg.part")

    /** Moves a finished [pendingShotFile] into place. */
    suspend fun commitShotFile(index: Int) = withContext(io) { move(pendingShotFile(index), shotFile(index)) }

    private suspend fun edit(change: (BoothDraft?) -> BoothDraft?): BoothDraft? = lock.withLock {
        ensureLoaded()
        val next = withContext(io) { change(state.value)?.also(::write) }
        state.value = next
        next
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        state.value = withContext(io) { read() }
        loaded = true
    }

    private fun read(): BoothDraft? {
        if (!file.exists()) return null
        val draft = decode(file.readText()) ?: run {
            Log.w(Tag, "Unreadable draft.json; ignoring it")
            return null
        }
        // A shot whose file didn't make it to disk (process death mid-write) is simply not there.
        return draft.copy(shots = draft.shots.filter { shotFile(it.index).isFile })
    }

    private fun write(draft: BoothDraft) {
        dir.mkdirs()
        val tmp = File(dir, "$DraftFileName.tmp")
        tmp.writeText(encode(draft))
        move(tmp, file)
    }

    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val DraftDirName = "_draft"
        const val DraftFileName = "draft.json"
        private const val Tag = "BoothDraftStore"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(draft: BoothDraft): String = json.encodeToString(BoothDraft.serializer(), draft)

        /** Null for anything that isn't a readable draft. */
        fun decode(text: String): BoothDraft? = try {
            json.decodeFromString(BoothDraft.serializer(), text)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

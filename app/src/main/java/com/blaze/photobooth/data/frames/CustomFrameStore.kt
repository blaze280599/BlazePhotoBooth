package com.blaze.photobooth.data.frames

import android.util.Log
import com.blaze.photobooth.data.db.FrameDao
import com.blaze.photobooth.data.db.FrameEntity
import com.blaze.photobooth.model.ArtworkLocation
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.ImageFrame
import com.blaze.photobooth.model.SlotRect
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** What Manage frames' Delete did (§11.2). */
sealed interface FrameDeletion {
    /** Gone: the row now, the files with it. */
    data object Deleted : FrameDeletion

    /** Saved strips use it ([strips] of them), so it's hidden and kept for Edit until they're gone. */
    data class KeptHidden(val strips: Int) : FrameDeletion

    data object NotFound : FrameDeletion
}

/**
 * Imported image frames (README §4.9, §11.1, §11.2): a row in the `frames` table plus
 * `files/frames/{uuid}/frame.png` (windows transparent) and `thumb.png` (300 px wide). The frame's id is `custom:{uuid}`.
 *
 * **Deletion rules:** a frame that a saved session (or the draft) uses is only hidden ([FrameEntity.HiddenDeleted]),
 * so Edit can still re-frame it; [cleanup] removes it at startup once nothing uses it. An unused one is deleted at once.
 */
class CustomFrameStore(
    private val dao: FrameDao,
    /** `files/frames/`. */
    private val dir: File,
    /** Frame ids (`custom:…`, `asset:…`, `theme:…`) that saved sessions and the draft use. */
    private val framesInUse: suspend () -> Set<String>,
    /** How many saved sessions use a frame id. */
    private val sessionsUsing: suspend (String) -> Int,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CustomFrameSource {

    /** One change at a time, so a delete can't race a reorder. */
    private val lock = Mutex()

    override val frames: Flow<List<CustomFrame>> = dao.observeAll().map { rows -> rows.mapNotNull(::toCustomFrame) }

    fun folder(uuid: String) = File(dir, uuid)

    fun artworkFile(uuid: String) = File(folder(uuid), ArtworkFile)

    fun thumbFile(uuid: String) = File(folder(uuid), ThumbFile)

    /**
     * Saves a new frame: [write] fills a staging folder with `frame.png` and `thumb.png`, which is then renamed into
     * place and the row inserted, last in her order. If anything fails, nothing is left behind.
     */
    suspend fun add(
        name: String,
        width: Int,
        height: Int,
        slots: List<SlotRect>,
        write: suspend (artwork: File, thumb: File) -> Unit,
    ): FrameId = lock.withLock {
        val uuid = UUID.randomUUID().toString()
        val staging = File(dir, "$uuid$StagingSuffix")
        val target = folder(uuid)
        try {
            withContext(io) {
                staging.deleteRecursively()
                check(staging.mkdirs()) { "Couldn't make $staging" }
            }
            write(File(staging, ArtworkFile), File(staging, ThumbFile))
            withContext(io) { check(staging.renameTo(target)) { "Couldn't move $staging into place" } }
            dao.insert(
                FrameEntity(
                    id = uuid,
                    name = cleanName(name),
                    width = width,
                    height = height,
                    slotsJson = encodeSlots(slots),
                    textAreaJson = null,
                    sortOrder = (dao.maxSortOrder() ?: -1) + 1,
                    createdAt = now(),
                ),
            )
            FrameId.custom(uuid)
        } catch (e: Exception) {
            withContext(io) {
                staging.deleteRecursively()
                target.deleteRecursively()
            }
            throw e
        }
    }

    suspend fun rename(id: FrameId, name: String) = lock.withLock {
        val clean = cleanName(name)
        if (clean.isNotEmpty()) dao.rename(id.localId, clean)
    }

    /** Hide or show again (§4.8). A deleted-but-kept frame stays out of sight. */
    suspend fun setHidden(id: FrameId, hidden: Boolean) = lock.withLock {
        val row = dao.frame(id.localId) ?: return@withLock
        if (row.hidden == FrameEntity.HiddenDeleted) return@withLock
        dao.setHidden(row.id, if (hidden) FrameEntity.HiddenByHer else FrameEntity.HiddenShown)
    }

    /** Moves [id] one place earlier ([by] = -1) or later (+1) among the frames she can see in Manage frames. */
    suspend fun move(id: FrameId, by: Int) = lock.withLock {
        val listed = dao.all().filter { it.hidden != FrameEntity.HiddenDeleted }.map { it.id }.toMutableList()
        val from = listed.indexOf(id.localId)
        val to = from + by
        if (from < 0 || to !in listed.indices) return@withLock
        listed.add(to, listed.removeAt(from))
        dao.reorder(listed)
    }

    /** Manage frames' Delete (§11.2): hidden if saved strips use it, otherwise gone with its files. */
    suspend fun delete(id: FrameId): FrameDeletion = lock.withLock {
        val row = dao.frame(id.localId) ?: return@withLock FrameDeletion.NotFound
        val strips = sessionsUsing(id.value)
        if (strips > 0 || id.value in framesInUse()) {
            dao.setHidden(row.id, FrameEntity.HiddenDeleted)
            FrameDeletion.KeptHidden(strips)
        } else {
            dao.delete(row.id)
            withContext(io) { folder(row.id).deleteRecursively() }
            FrameDeletion.Deleted
        }
    }

    /**
     * Startup (§11.2): deleted frames that nothing uses any more go for good, and so does any folder under
     * `files/frames/` without a row (an import that died half-way).
     */
    suspend fun cleanup() = lock.withLock {
        val used = framesInUse()
        var removed = 0
        for (row in dao.all()) {
            if (row.hidden == FrameEntity.HiddenDeleted && FrameId.custom(row.id).value !in used) {
                // Files first, then the row: a crash in between leaves a row that the next start removes.
                withContext(io) { folder(row.id).deleteRecursively() }
                dao.delete(row.id)
                removed++
            }
        }
        val known = dao.all().map { it.id }.toSet()
        val strays = withContext(io) {
            dir.listFiles()?.filter { it.name !in known }?.onEach { it.deleteRecursively() }.orEmpty()
        }
        if (removed > 0 || strays.isNotEmpty()) Log.i(Tag, "Removed $removed deleted frame(s) and ${strays.size} stray folder(s)")
    }

    /** Every imported frame's name, deleted-but-kept ones included (for "Frame N" defaults). */
    suspend fun names(): List<String> = dao.all().map { it.name }

    private fun toCustomFrame(row: FrameEntity): CustomFrame? = try {
        CustomFrame(
            frame = ImageFrame(
                id = FrameId.custom(row.id),
                name = row.name,
                width = row.width,
                height = row.height,
                slots = decodeSlots(row.slotsJson),
                textArea = row.textAreaJson?.let { FrameRepository.json.decodeFromString<ManifestRect>(it).toSlot() },
                artwork = ArtworkLocation.LocalFile(artworkFile(row.id)),
                thumb = ArtworkLocation.LocalFile(thumbFile(row.id)),
            ),
            visibility = when (row.hidden) {
                FrameEntity.HiddenShown -> FrameVisibility.Shown
                FrameEntity.HiddenDeleted -> FrameVisibility.Deleted
                else -> FrameVisibility.Hidden
            },
        )
    } catch (e: Exception) {
        // A broken row must never take Setup down.
        Log.e(Tag, "Skipping frame ${row.id}", e)
        null
    }

    companion object {
        private const val Tag = "CustomFrameStore"
        const val ArtworkFile = "frame.png"
        const val ThumbFile = "thumb.png"
        const val StagingSuffix = ".part"

        /** Frame names are short labels under a carousel card. */
        const val MaxName = 24

        /** `slots_json` (§11.2): `[{x,y,w,h}, …]` in frame pixels. */
        fun encodeSlots(slots: List<SlotRect>): String = FrameRepository.json.encodeToString(slots.map(ManifestRect::of))

        fun decodeSlots(json: String): List<SlotRect> =
            FrameRepository.json.decodeFromString<List<ManifestRect>>(json).map { it.toSlot() }

        /** A name as stored: trimmed, at most [MaxName] characters. */
        fun cleanName(name: String): String = name.trim().take(MaxName).trim()

        /** "Frame 1", "Frame 2", …: the smallest number not already taken ([pattern] holds `%1$d`). */
        fun defaultName(taken: Collection<String>, pattern: (Int) -> String): String =
            generateSequence(1) { it + 1 }.map(pattern).first { it !in taken }
    }
}

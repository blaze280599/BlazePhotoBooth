package com.blaze.photobooth.data.files

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Where a saved session's files live (README §11.1): `files/sessions/{id}/` with every original shot,
 * `strip.jpg` and `thumb.jpg`. Paths are derived from the id, never stored (§11.2).
 */
class SessionFileStore(val sessionsDir: File) {

    fun dir(id: String) = File(sessionsDir, id)

    fun shot(id: String, index: Int) = File(dir(id), shotName(index))

    fun strip(id: String) = File(dir(id), StripName)

    fun thumb(id: String) = File(dir(id), ThumbName)

    /** What the album grid shows (§4.6): the thumbnail, or the full strip if the thumbnail is missing. */
    fun thumbOrStrip(id: String): File = thumb(id).takeIf { it.isFile } ?: strip(id)

    /** What Detail shows (§4.7): the full strip, or the thumbnail if the strip is missing. */
    fun stripOrThumb(id: String): File = strip(id).takeIf { it.isFile } ?: thumb(id)

    /**
     * Where Edit renders the new strip before it replaces the old one (§4.7). [stamp] is the `updated_at` the
     * edit writes, so a staging folder left by a crash tells whether its DB update landed.
     */
    fun editDir(id: String, stamp: Long) = File(dir(id), "$EditPrefix$stamp")

    /** Staging folders left in a session folder, with their stamps. */
    fun editDirs(id: String): List<Pair<File, Long>> =
        dir(id).listFiles { f -> f.isDirectory && f.name.startsWith(EditPrefix) }
            ?.mapNotNull { f -> f.name.removePrefix(EditPrefix).toLongOrNull()?.let { f to it } }
            .orEmpty()

    /** Session folders on disk (everything except the draft). */
    fun sessionIds(): List<String> =
        sessionsDir.listFiles { f -> f.isDirectory && f.name != BoothDraftStore.DraftDirName }?.map { it.name }.orEmpty()

    /** Renames [from] to [to] in one step: the folder is either all here or all there (§11.1). */
    fun moveDir(from: File, to: File) {
        check(!to.exists()) { "$to already exists" }
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    /** Removes a session folder and everything in it (the purge, §11.2). True when it's gone. */
    fun deleteDir(id: String): Boolean {
        val dir = dir(id)
        return !dir.exists() || dir.deleteRecursively()
    }

    /** Moves [from] over [to], replacing it in one step. */
    fun replace(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        private const val EditPrefix = "edit-"
        const val StripName = "strip.jpg"
        const val ThumbName = "thumb.jpg"

        fun shotName(index: Int) = "shot_$index.jpg"
    }
}

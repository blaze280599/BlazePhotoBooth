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

    /** Session folders on disk (everything except the draft). */
    fun sessionIds(): List<String> =
        sessionsDir.listFiles { f -> f.isDirectory && f.name != BoothDraftStore.DraftDirName }?.map { it.name }.orEmpty()

    /** Renames [from] to [to] in one step: the folder is either all here or all there (§11.1). */
    fun moveDir(from: File, to: File) {
        check(!to.exists()) { "$to already exists" }
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val StripName = "strip.jpg"
        const val ThumbName = "thumb.jpg"

        fun shotName(index: Int) = "shot_$index.jpg"
    }
}

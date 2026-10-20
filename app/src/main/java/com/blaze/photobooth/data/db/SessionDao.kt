package com.blaze.photobooth.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Saved sessions and their shots (README §11.2). Soft-deleted sessions are hidden from every "observe" query. */
@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(session: SessionEntity)

    @Insert
    suspend fun insertShots(shots: List<ShotEntity>)

    /** A session and all its shots, or nothing at all. */
    @Transaction
    suspend fun insert(session: SessionEntity, shots: List<ShotEntity>) {
        insertSession(session)
        insertShots(shots)
    }

    /** The album, newest first. */
    @Query("SELECT * FROM sessions WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT COUNT(*) FROM sessions WHERE deleted_at IS NULL")
    fun observeCount(): Flow<Int>

    /** The newest strip, for Home's peek (§4.1). */
    @Query("SELECT * FROM sessions WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 1")
    fun observeLatest(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: String): SessionEntity?

    @Query("SELECT id FROM sessions")
    suspend fun allIds(): List<String>

    /** A session's shots in the order they were taken. */
    @Query("SELECT * FROM shots WHERE session_id = :sessionId ORDER BY shot_index")
    suspend fun shots(sessionId: String): List<ShotEntity>

    @Query("UPDATE sessions SET is_favorite = :favorite, updated_at = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Query("UPDATE sessions SET deleted_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE sessions SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: String)

    /** Album multi-select and Detail's Delete (§4.6, §4.7): one Undo brings the whole batch back. */
    @Query("UPDATE sessions SET deleted_at = :now WHERE id IN (:ids)")
    suspend fun softDeleteAll(ids: List<String>, now: Long)

    @Query("UPDATE sessions SET deleted_at = NULL WHERE id IN (:ids)")
    suspend fun restoreAll(ids: List<String>)

    @Query(
        "UPDATE sessions SET layout_id = :layoutId, frame_id = :frameId, filter_id = :filterId, caption = :caption, " +
            "show_date = :showDate, updated_at = :now WHERE id = :id",
    )
    suspend fun updateLook(id: String, layoutId: String, frameId: String, filterId: String, caption: String?, showDate: Boolean, now: Long)

    @Query("UPDATE shots SET slot_index = :slot WHERE session_id = :sessionId AND shot_index = :shotIndex")
    suspend fun setSlot(sessionId: String, shotIndex: Int, slot: Int?)

    /**
     * Edit (§4.7): the new look and which shot sits in each slot ([slots]: shot index → slot, null = unused),
     * all at once or not at all.
     */
    @Transaction
    suspend fun applyEdit(session: SessionEntity, slots: Map<Int, Int?>) {
        updateLook(session.id, session.layoutId, session.frameId, session.filterId, session.caption, session.showDate, session.updatedAt)
        slots.forEach { (shot, slot) -> setSlot(session.id, shot, slot) }
    }

    /** Soft-deleted before [cutoff]: ready to purge for good (§11.2). */
    @Query("SELECT id FROM sessions WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    suspend fun deletedBefore(cutoff: Long): List<String>

    /**
     * Every frame a saved session uses, soft-deleted ones included (Undo can bring them back). An imported frame in
     * this list is hidden instead of deleted, and its files stay (§11.2).
     */
    @Query("SELECT DISTINCT frame_id FROM sessions")
    suspend fun frameIdsInUse(): List<String>

    @Query("SELECT COUNT(*) FROM sessions WHERE frame_id = :frameId")
    suspend fun countUsing(frameId: String): Int

    /** Removes the row; its shots go with it (`ON DELETE CASCADE`). */
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)
}

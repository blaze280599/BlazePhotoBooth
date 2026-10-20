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

    /** Soft-deleted before [cutoff]: ready to purge for good (§11.2). */
    @Query("SELECT id FROM sessions WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    suspend fun deletedBefore(cutoff: Long): List<String>

    /** Removes the row; its shots go with it (`ON DELETE CASCADE`). */
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)
}

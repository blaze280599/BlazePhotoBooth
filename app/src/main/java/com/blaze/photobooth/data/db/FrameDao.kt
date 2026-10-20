package com.blaze.photobooth.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Image frames imported on the phone (README §4.9, §11.2), in her order. */
@Dao
interface FrameDao {

    /** Every row, hidden and deleted-but-kept ones included, in carousel order. */
    @Query("SELECT * FROM frames ORDER BY sort_order, created_at")
    fun observeAll(): Flow<List<FrameEntity>>

    @Query("SELECT * FROM frames ORDER BY sort_order, created_at")
    suspend fun all(): List<FrameEntity>

    @Query("SELECT * FROM frames WHERE id = :id")
    suspend fun frame(id: String): FrameEntity?

    @Insert
    suspend fun insert(frame: FrameEntity)

    @Query("SELECT MAX(sort_order) FROM frames")
    suspend fun maxSortOrder(): Int?

    @Query("UPDATE frames SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    /** [state] is one of the `FrameEntity.Hidden…` values. */
    @Query("UPDATE frames SET hidden = :state WHERE id = :id")
    suspend fun setHidden(id: String, state: Int)

    @Query("UPDATE frames SET sort_order = :order WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int)

    /** Numbers [ids] 0, 1, 2… in the given order, all at once. */
    @Transaction
    suspend fun reorder(ids: List<String>) {
        ids.forEachIndexed { i, id -> setSortOrder(id, i) }
    }

    @Query("DELETE FROM frames WHERE id = :id")
    suspend fun delete(id: String)
}

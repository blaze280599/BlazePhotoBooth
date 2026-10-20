package com.blaze.photobooth.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A printed strip (README §11.2). [id] is a UUID and also the folder name under `files/sessions/`;
 * file paths are derived from it, never stored.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** A built-in layout id (§5.1), or [ImageLayoutId] for image frames, which bring their own geometry. */
    @ColumnInfo(name = "layout_id") val layoutId: String,
    /** `theme:strawberry` | `asset:lilac-pals` | `custom:{uuid}` ([com.blaze.photobooth.model.FrameId]). */
    @ColumnInfo(name = "frame_id") val frameId: String,
    @ColumnInfo(name = "filter_id") val filterId: String,
    val caption: String?,
    @ColumnInfo(name = "show_date") val showDate: Boolean,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    /** Soft delete for Undo (§4.7); purged on a later app start. */
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
) {
    companion object {
        const val ImageLayoutId = "image"
    }
}

/**
 * Every shot of a session, including unused bonus shots ([slotIndex] = null), so nothing is ever lost
 * and Edit can re-pick later (§11.1, §11.2). The file is `sessions/{sessionId}/shot_{shotIndex}.jpg`.
 */
@Entity(
    tableName = "shots",
    primaryKeys = ["session_id", "shot_index"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ShotEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "shot_index") val shotIndex: Int,
    /** Position in the strip (0-based), or null for a shot that didn't make the cut. */
    @ColumnInfo(name = "slot_index") val slotIndex: Int?,
    val width: Int,
    val height: Int,
)

/**
 * An image frame imported on the phone (§4.9, §11.2). Created in v1, so M7 needed no migration. Bundled frames
 * come from `frames.json`, not this table. [id] is a UUID and also the folder name under `files/frames/`.
 */
@Entity(tableName = "frames")
data class FrameEntity(
    @PrimaryKey val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    /** `[{x,y,w,h}, …]` in frame pixels. */
    @ColumnInfo(name = "slots_json") val slotsJson: String,
    @ColumnInfo(name = "text_area_json") val textAreaJson: String?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /**
     * [HiddenShown] (0), [HiddenByHer] (1, she hid it in Manage frames) or [HiddenDeleted] (2, she deleted it but
     * saved sessions still use it, so it's kept for Edit until they're gone, §11.2). The column is the v1
     * `hidden INTEGER NOT NULL DEFAULT 0`; 0 and 1 read the same as the old boolean.
     */
    @ColumnInfo(defaultValue = "0") val hidden: Int = HiddenShown,
) {
    companion object {
        const val HiddenShown = 0
        const val HiddenByHer = 1
        const val HiddenDeleted = 2
    }
}

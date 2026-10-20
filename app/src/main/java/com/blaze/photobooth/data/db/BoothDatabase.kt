package com.blaze.photobooth.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * `booth.db` (README §11.2). The schema is exported to `app/schemas/`. Any change needs a real migration:
 * never `fallbackToDestructiveMigration` (§17 rule 11), because this is her album.
 */
@Database(
    entities = [SessionEntity::class, ShotEntity::class, FrameEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BoothDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao

    companion object {
        const val FileName = "booth.db"

        fun create(context: Context): BoothDatabase =
            Room.databaseBuilder(context.applicationContext, BoothDatabase::class.java, FileName).build()
    }
}

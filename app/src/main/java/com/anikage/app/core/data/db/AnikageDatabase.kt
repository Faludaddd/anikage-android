package com.anikage.app.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AnimeCacheEntity::class,
        DetailCacheEntity::class,
        RecentlyViewedEntity::class,
        WatchProgressEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AnikageDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun detailDao(): DetailDao
    abstract fun recentlyViewedDao(): RecentlyViewedDao
    abstract fun watchProgressDao(): WatchProgressDao

    companion object {
        @Volatile private var INSTANCE: AnikageDatabase? = null

        fun get(context: Context): AnikageDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnikageDatabase::class.java,
                    "anikage.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

package com.anikage.app.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AnimeCacheEntity::class,
        DetailCacheEntity::class,
        RecentlyViewedEntity::class,
        WatchProgressEntity::class,
        DownloadedEpisodeEntity::class,
        AnimeListEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AnikageDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun detailDao(): DetailDao
    abstract fun recentlyViewedDao(): RecentlyViewedDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun downloadedEpisodeDao(): DownloadedEpisodeDao
    abstract fun animeListDao(): AnimeListDao

    companion object {
        @Volatile private var INSTANCE: AnikageDatabase? = null

        /**
         * v1 -> v2: add the in-app download records and the local anime list.
         * Pure additive CREATE TABLE statements — watch progress, recently
         * viewed and caches from v1 survive the upgrade untouched.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloaded_episodes` (" +
                        "`downloadKey` TEXT NOT NULL, `animeId` INTEGER NOT NULL, " +
                        "`slug` TEXT, `episode` INTEGER NOT NULL, `quality` TEXT NOT NULL, " +
                        "`height` INTEGER NOT NULL, `filePath` TEXT NOT NULL, " +
                        "`subtitlePath` TEXT, `titleRomaji` TEXT, `titleEnglish` TEXT, " +
                        "`episodeTitle` TEXT, `posterUrl` TEXT, `sizeBytes` INTEGER NOT NULL, " +
                        "`provider` TEXT NOT NULL, `lang` TEXT NOT NULL, " +
                        "`downloadedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`downloadKey`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `anime_list` (" +
                        "`animeId` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                        "`titleRomaji` TEXT, `titleEnglish` TEXT, `posterUrl` TEXT, " +
                        "`coverColor` TEXT, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`animeId`))",
                )
            }
        }

        fun get(context: Context): AnikageDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnikageDatabase::class.java,
                    "anikage.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

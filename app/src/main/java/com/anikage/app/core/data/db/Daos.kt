package com.anikage.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AnimeCacheEntity>)

    @Query("SELECT * FROM anime_cache WHERE id = :id")
    suspend fun getById(id: Int): AnimeCacheEntity?

    @Query("SELECT * FROM anime_cache WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<AnimeCacheEntity>

    @Query("SELECT * FROM anime_cache ORDER BY cachedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AnimeCacheEntity>
}

@Dao
interface DetailDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DetailCacheEntity)

    @Query("SELECT * FROM detail_cache WHERE animeId = :id")
    suspend fun get(id: Int): DetailCacheEntity?
}

@Dao
interface RecentlyViewedDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RecentlyViewedEntity)

    @Query("SELECT * FROM recently_viewed ORDER BY lastViewedAt DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<RecentlyViewedEntity>>

    @Query("SELECT * FROM recently_viewed ORDER BY lastViewedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<RecentlyViewedEntity>

    @Query("DELETE FROM recently_viewed WHERE animeId = :id")
    suspend fun delete(id: Int)
}

@Dao
interface WatchProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchProgressEntity)

    @Query("SELECT * FROM watch_progress WHERE animeId = :animeId ORDER BY episode ASC")
    suspend fun forAnime(animeId: Int): List<WatchProgressEntity>

    @Query("SELECT * FROM watch_progress WHERE episodeKey = :key")
    suspend fun get(key: String): WatchProgressEntity?

    @Query("DELETE FROM watch_progress WHERE animeId = :animeId")
    suspend fun clearForAnime(animeId: Int)
}

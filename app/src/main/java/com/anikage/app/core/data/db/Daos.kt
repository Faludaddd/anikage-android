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

@Dao
interface DownloadedEpisodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadedEpisodeEntity)

    @Query("SELECT * FROM downloaded_episodes ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<DownloadedEpisodeEntity>>

    @Query("SELECT * FROM downloaded_episodes ORDER BY downloadedAt DESC")
    suspend fun all(): List<DownloadedEpisodeEntity>

    @Query("SELECT * FROM downloaded_episodes WHERE animeId = :animeId ORDER BY episode ASC")
    suspend fun forAnime(animeId: Int): List<DownloadedEpisodeEntity>

    @Query("SELECT * FROM downloaded_episodes WHERE downloadKey = :key")
    suspend fun get(key: String): DownloadedEpisodeEntity?

    @Query("SELECT * FROM downloaded_episodes WHERE animeId = :animeId AND episode = :episode LIMIT 1")
    suspend fun forEpisode(animeId: Int, episode: Int): DownloadedEpisodeEntity?

    @Query("DELETE FROM downloaded_episodes WHERE downloadKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface AnimeListDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AnimeListEntity)

    @Query("SELECT * FROM anime_list ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnimeListEntity>>

    @Query("SELECT * FROM anime_list ORDER BY updatedAt DESC")
    suspend fun all(): List<AnimeListEntity>

    @Query("SELECT * FROM anime_list WHERE animeId = :animeId")
    suspend fun get(animeId: Int): AnimeListEntity?

    @Query("SELECT * FROM anime_list WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun byStatus(status: String): List<AnimeListEntity>

    @Query("DELETE FROM anime_list WHERE animeId = :animeId")
    suspend fun delete(animeId: Int)
}

@Dao
interface SubscriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions ORDER BY subscribedAt DESC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY subscribedAt DESC")
    suspend fun all(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE animeId = :animeId")
    suspend fun get(animeId: Int): SubscriptionEntity?

    @Query("UPDATE subscriptions SET lastKnownEpisodes = :episodeCount, lastNotifiedEpisode = :notified, nextAiringEpisode = :nextAiring, lastCheckedAt = :checkedAt, releaseStatus = :status WHERE animeId = :animeId")
    suspend fun updateCheck(animeId: Int, episodeCount: Int, notified: Int, nextAiring: Int?, status: String?, checkedAt: Long)

    @Query("UPDATE subscriptions SET lastNotifiedEpisode = :episode WHERE animeId = :animeId")
    suspend fun markNotified(animeId: Int, episode: Int)

    @Query("DELETE FROM subscriptions WHERE animeId = :animeId")
    suspend fun delete(animeId: Int)

    @Query("DELETE FROM subscriptions")
    suspend fun clear()
}

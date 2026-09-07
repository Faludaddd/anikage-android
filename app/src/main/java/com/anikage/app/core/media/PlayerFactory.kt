package com.anikage.app.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.anikage.app.Config

/**
 * Central player construction — the ONE place ExoPlayer instances are built.
 *
 * Root-cause notes (v2.0.0):
 *  - og.bakayaro.live (the Anikage HLS proxy) rejects requests without an
 *    anikage.cc Origin/Referer with 403 "forbidden origin" (live-verified).
 *    DefaultHttpDataSource rides on HttpURLConnection, which is NOT
 *    guaranteed to forward those headers on every Android version — a
 *    transport that silently drops them yields exactly the "player stays
 *    black forever" symptom. OkHttp always forwards them.
 *  - A bounded LRU segment cache (256 MB in cacheDir) is layered under the
 *    network source so seeks/backward re-reads don't re-hit Cloudflare and
 *    burn the proxy's rate budget; cache errors never fail playback.
 *  - LoadControl is tuned for on-demand HLS: quick start, deep buffer.
 */
@OptIn(UnstableApi::class)
object PlayerFactory {

    private const val CACHE_MAX_BYTES = 256L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache? =
        cache ?: synchronized(this) {
            cache ?: runCatching {
                SimpleCache(
                    java.io.File(context.cacheDir, "media"),
                    LeastRecentlyUsedCacheEvictor(CACHE_MAX_BYTES),
                    StandaloneDatabaseProvider(context),
                )
            }.getOrNull()?.also { cache = it }
        }

    /**
     * DataSource factory: [SimpleCache] (LRU, 256 MB) -> OkHttp upstream with
     * the Origin/Referer/UA the stream proxy demands.
     */
    fun dataSourceFactory(context: Context): DataSource.Factory {
        val httpFactory = OkHttpDataSource.Factory(
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(Config.Network.CONNECT_TIMEOUT.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(Config.Network.READ_TIMEOUT.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build(),
        )
            .setUserAgent(Config.Network.USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "${Config.ANIKAGE_SITE_ORIGIN}/",
                    "Origin" to Config.ANIKAGE_SITE_ORIGIN,
                ),
            )

        val c = cache(context) ?: return httpFactory
        return CacheDataSource.Factory()
            .setCache(c)
            .setUpstreamDataSourceFactory(httpFactory)
            .setCacheWriteDataSinkFactory(null) // stream live data; cache reads only
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** ExoPlayer wired to the Anikage transport + streaming-tuned buffers. */
    fun build(context: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .build()
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory(context)))
            .build()
    }
}

package com.anikage.app.core.data.api

import com.anikage.app.Config
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Low-level REST client for Anikage's own API (anikage.cc + auth.anikage.cc).
 *
 * All calls go through [get] which:
 *  - logs every request/response through the in-app [AppLogger] (category
 *    NETWORK) with method, URL, status, latency and payload size — the same
 *    log format users already know from log exports;
 *  - uses a browser-like User-Agent (the API is Cloudflare-fronted);
 *  - parses JSON leniently (unknown keys ignored).
 *
 * Endpoint map (see Config.kt for the captured response shapes):
 *   browse:   {base}/api/media/anime/browse
 *   episodes: {base}/api/media/anime/{slug}/episodes
 *   servers:  {base}/api/media/anime/{slug}/episodes/{ep}/servers
 *   sources:  {base}/api/media/anime/{slug}/episodes/{ep}/sources
 *   views:    {auth}/api/views/anime/{slug}/episode/{ep}/views
 *   comments: {auth}/api/comments
 */
class AnikageApi(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson,
) {

    private val base: String = Config.ANIKAGE_API_BASE_URL ?: throw IllegalStateException(
        "ANIKAGE_API_BASE_URL is null — AnikageApi must not be constructed in AniList-only mode.",
    )
    private val authBase: String? = Config.ANIKAGE_AUTH_API_BASE_URL

    // ---------------------------------------------------------------------
    //  Core execution + logging
    // ---------------------------------------------------------------------

    /** Logged GET returning the raw body (throws on non-2xx). */
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        AppLogger.d(LogCategory.NETWORK, "Anikage -> GET $url")
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", Config.Network.USER_AGENT)
                .addHeader("Accept", "application/json")
                .addHeader("Referer", "${Config.ANIKAGE_SITE_ORIGIN}/")
                .addHeader("Origin", Config.ANIKAGE_SITE_ORIGIN)
                .build()
            val raw = client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                    ?: throw IOException("Empty response body")
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${body.take(120)}")
                }
                body
            }
            AppLogger.d(
                LogCategory.NETWORK,
                "Anikage <- 200 (${System.currentTimeMillis() - started}ms, ${raw.length} bytes)",
            )
            raw
        } catch (e: Exception) {
            AppLogger.e(
                LogCategory.NETWORK,
                "Anikage FAILED GET $url (${System.currentTimeMillis() - started}ms)",
                e,
            )
            throw e
        }
    }

    /** Resolve a stream token to the HLS playlist URL. */
    fun resolveStreamUrl(token: String): String {
        if (token.startsWith("http://") || token.startsWith("https://")) return token
        val proxy = Config.ANIKAGE_STREAM_PROXY_BASE_URL ?: "https://og.bakayaro.live"
        return "$proxy/m3u8/$token"
    }

    /** Resolve a subtitle token to its file URL. */
    fun resolveSubtitleUrl(token: String): String {
        val proxy = Config.ANIKAGE_STREAM_PROXY_BASE_URL ?: "https://og.bakayaro.live"
        return "$proxy/stream/$token"
    }

    // ---------------------------------------------------------------------
    //  Home — the exact payload the website homepage renders from.
    //  spotlight[6] = hero slides (TVDB fanart + clearLogo), featured =
    //  Editor's Pick, plus every rail the site shows.
    // ---------------------------------------------------------------------

    suspend fun home(): AnikageHomeResponse {
        val url = url(base, "/api/media/anime/home")
        return json.decodeFromString(get(url))
    }

    // ---------------------------------------------------------------------
    //  Browse / search
    // ---------------------------------------------------------------------

    /**
     * Browse or search the Anikage catalogue — the exact endpoint + params
     * the site's Browse page uses. `sort` is one of
     * popularity|trending|score|favourites|newest (site default: popularity).
     * Filter values mirror the site's filter panel:
     *   season: WINTER|SPRING|SUMMER|FALL     year: e.g. 2025
     *   format: TV|TV_SHORT|MOVIE|SPECIAL|OVA|ONA (comma-joined when multiple)
     *   status: FINISHED|RELEASING|NOT_YET_RELEASED|CANCELLED (multi)
     *   country: JP|KR|CN|TW                  genres: comma-joined
     */
    suspend fun browse(
        query: String? = null,
        sort: String? = null,
        page: Int = 1,
        limit: Int = 24,
        genres: String? = null,
        season: String? = null,
        year: Int? = null,
        format: String? = null,
        status: String? = null,
        country: String? = null,
        type: String? = "anime",
        adult: Boolean = true,
    ): AnikageBrowseResponse {
        val url = url(base, "/api/media/anime/browse") {
            param("q", query)
            param("sort", sort)
            param("page", page.toString())
            param("limit", limit.toString())
            param("genres", genres)
            param("season", season)
            param("year", year?.toString())
            param("format", format)
            param("status", status)
            param("country", country)
            param("type", type)
            param("adult", adult.toString())
        }
        return json.decodeFromString(get(url))
    }

    // ---------------------------------------------------------------------
    //  Episodes / servers / sources
    // ---------------------------------------------------------------------

    /**
     * Anime info — the payload behind the site's /anime/info/{slug} page
     * (full metadata: description, characters, relations, recommendations,
     * studios, genres, airing info). The site's own data source; does not
     * depend on the public AniList GraphQL API.
     */
    suspend fun animeInfo(slug: String): AnikageInfoResponse {
        val url = url(base, "/api/media/anime/$slug")
        return json.decodeFromString(get(url))
    }

    /**
     * Week airing schedule — the payload behind the site's /schedule page
     * (server-side schedule data; no public-AniList dependency).
     */
    suspend fun schedule(): List<AnikageScheduleElement> {
        val url = url(base, "/api/media/anime/schedule")
        return json.decodeFromString(get(url))
    }

    suspend fun episodes(slug: String): List<AnikageEpisode> {
        val url = url(base, "/api/media/anime/$slug/episodes")
        return json.decodeFromString(get(url))
    }

    suspend fun servers(slug: String, episode: Int): AnikageServersResponse {
        val url = url(base, "/api/media/anime/$slug/episodes/$episode/servers")
        return json.decodeFromString(get(url))
    }

    suspend fun sources(
        slug: String,
        episode: Int,
        provider: String = Config.DEFAULT_STREAM_PROVIDER,
        lang: String = Config.DEFAULT_STREAM_LANG,
        server: String? = null,
    ): AnikageSourcesResponse {
        val url = url(base, "/api/media/anime/$slug/episodes/$episode/sources") {
            param("provider", provider)
            param("lang", lang)
            param("server", server)
        }
        return json.decodeFromString(get(url))
    }

    // ---------------------------------------------------------------------
    //  Music — /api/animethemes (the site's music tab proxy).
    // ---------------------------------------------------------------------

    /**
     * Search anime themes — the exact call the site's Music page makes:
     * `path=/search`, anime included with `animethemes.song,images`.
     */
    suspend fun musicSearch(query: String): AnikageMusicSearchResponse {
        val url = url(base, "/api/animethemes") {
            param("path", "/search")
            param("include[anime]", "animethemes.song,images")
            param("q", query)
            param("fields[search]", "anime")
        }
        return json.decodeFromString(get(url))
    }

    /**
     * Full theme set for one anime — the site's music/info page call:
     * entries with video + audio links, song titles, images, AniList id.
     */
    suspend fun musicAnime(slug: String): AnikageMusicAnime {
        val url = url(base, "/api/animethemes") {
            param("path", "/anime/$slug")
            param(
                "include",
                "animethemes.animethemeentries.videos," +
                    "animethemes.animethemeentries.videos.audio," +
                    "animethemes.song,images,resources",
            )
            param("filter[site]", "Anilist")
            param("fields[resource]", "external_id")
        }
        val response = json.decodeFromString<AnikageMusicAnimeResponse>(get(url))
        return response.anime
    }

    // ---------------------------------------------------------------------
    //  Auth-side endpoints (comments / views)
    // ---------------------------------------------------------------------

    suspend fun comments(
        animeId: Int,
        episode: Int,
        limit: Int = 20,
        sort: String = "newest",
    ): AnikageCommentsResponse {
        val auth = authBase ?: return AnikageCommentsResponse()
        val url = url(auth, "/api/comments") {
            param("animeId", animeId.toString())
            param("episode", episode.toString())
            param("limit", limit.toString())
            param("sort", sort)
        }
        return json.decodeFromString(get(url))
    }

    suspend fun views(slug: String, episode: Int): AnikageViewsResponse {
        val auth = authBase ?: return AnikageViewsResponse()
        val url = url(auth, "/api/views/anime/$slug/episode/$episode/views")
        return json.decodeFromString(get(url))
    }

    // ---------------------------------------------------------------------
    //  URL builder helper
    // ---------------------------------------------------------------------

    private class UrlBuilder(private val sb: StringBuilder) {
        private val params = mutableListOf<Pair<String, String>>()

        fun param(name: String, value: String?) {
            if (value != null) params += name to value
        }

        fun build(): String {
            if (params.isEmpty()) return sb.toString()
            return sb.append(params.joinToString(separator = "&") { (n, v) ->
                "${enc(n)}=${enc(v)}"
            }.let { "?$it" }).toString()
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }

    private inline fun url(root: String, path: String, block: UrlBuilder.() -> Unit = {}): String =
        UrlBuilder(StringBuilder(root).append(path)).apply(block).build()

    companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            explicitNulls = false
        }

        /** Shared client with the configured timeouts (one instance per app). */
        @Volatile
        private var sharedClient: OkHttpClient? = null

        fun defaultClient(): OkHttpClient =
            sharedClient ?: synchronized(this) {
                sharedClient ?: OkHttpClient.Builder()
                    .connectTimeout(Config.Network.CONNECT_TIMEOUT.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(Config.Network.READ_TIMEOUT.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(Config.Network.WRITE_TIMEOUT.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()
                    .also { sharedClient = it }
            }
    }
}

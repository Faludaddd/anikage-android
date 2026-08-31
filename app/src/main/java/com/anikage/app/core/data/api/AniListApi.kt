package com.anikage.app.core.data.api

import com.anikage.app.core.data.model.AnimeDetails
import com.anikage.app.core.data.model.AiringSchedule
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.data.model.PageInfo
import com.anikage.app.core.data.model.PageMediaResponse
import com.anikage.app.core.data.model.PageScheduleResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.anikage.app.Config
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory

/**
 * Low-level AniList GraphQL client.
 *
 * Uses OkHttp + kotlinx.serialization to POST GraphQL queries to AniList.
 * We use OkHttp directly (not Retrofit) because AniList is a single-endpoint
 * GraphQL API — Retrofit's multi-endpoint abstraction adds no value.
 *
 * Responses are typed: each `xxx()` method returns a parsed data class.
 *
 * On failure, throws [IOException] for network errors and
 * [GraphQLException] for GraphQL errors returned in the response body.
 */
class AniListApi(
    private val client: OkHttpClient,
    private val json: Json = defaultJson,
) {

    private val endpoint = Config.ANILIST_API_URL

    /** Generic GraphQL request. Returns the raw response JSON. */
    private fun execute(query: String, variables: JsonObject): String {
        val op = operationName(query)
        val started = System.currentTimeMillis()
        AppLogger.d(LogCategory.NETWORK, "AniList -> $op")
        try {
            val raw = executeInternal(query, variables)
            AppLogger.d(
                LogCategory.NETWORK,
                "AniList <- $op (${System.currentTimeMillis() - started}ms, ${raw.length} bytes)",
            )
            return raw
        } catch (e: Exception) {
            AppLogger.e(
                LogCategory.NETWORK,
                "AniList FAILED $op (${System.currentTimeMillis() - started}ms)",
                e,
            )
            throw e
        }
    }

    private fun executeInternal(query: String, variables: JsonObject): String {
        // Build the request body as a JSON object: {"query": "...", "variables": { ... }}
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.let { json.encodeToString(JsonObject.serializer(), it) }

        val request = Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", Config.Network.USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string()
                ?: throw IOException("Empty response body")
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            // GraphQL always returns 200 with potential errors in the body
            val obj = json.parseToJsonElement(raw) as? JsonObject
                ?: throw IOException("Malformed GraphQL response")
            if (obj["errors"] != null) {
                val msg = obj["errors"].toString()
                throw GraphQLException("GraphQL errors: $msg")
            }
            return raw
        }
    }

    /** Best-effort GraphQL operation name for log messages. */
    private fun operationName(query: String): String =
        Regex("(?:query|mutation)\\s+(\\w+)").find(query)?.groupValues?.get(1) ?: "request"

    /** Paged list of trending anime. */
    suspend fun trending(page: Int = 1, perPage: Int = 20): Pair<List<Anime>, PageInfo> {
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
        }
        val raw = execute(AniListQueries.TRENDING, vars)
        val parsed = json.decodeFromString(PageMediaResponse.serializer(), raw)
        val page = parsed.data?.Page
        return (page?.media ?: emptyList()) to (page?.pageInfo ?: PageInfo())
    }

    /** Paged list of popular anime for a given season + year. */
    suspend fun popularThisSeason(
        season: String,
        year: Int,
        page: Int = 1,
        perPage: Int = 20,
    ): Pair<List<Anime>, PageInfo> {
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
            put("season", season)
            put("year", year)
        }
        val raw = execute(AniListQueries.POPULAR_THIS_SEASON, vars)
        val parsed = json.decodeFromString(PageMediaResponse.serializer(), raw)
        val pageData = parsed.data?.Page
        return (pageData?.media ?: emptyList()) to (pageData?.pageInfo ?: PageInfo())
    }

    /** Paged list of top-rated anime. */
    suspend fun topRated(page: Int = 1, perPage: Int = 20): Pair<List<Anime>, PageInfo> {
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
        }
        val raw = execute(AniListQueries.TOP_RATED, vars)
        val parsed = json.decodeFromString(PageMediaResponse.serializer(), raw)
        val pageData = parsed.data?.Page
        return (pageData?.media ?: emptyList()) to (pageData?.pageInfo ?: PageInfo())
    }

    /** Paged list of upcoming (NOT_YET_RELEASED) anime. */
    suspend fun upcoming(page: Int = 1, perPage: Int = 20): Pair<List<Anime>, PageInfo> {
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
        }
        val raw = execute(AniListQueries.UPCOMING, vars)
        val parsed = json.decodeFromString(PageMediaResponse.serializer(), raw)
        val pageData = parsed.data?.Page
        return (pageData?.media ?: emptyList()) to (pageData?.pageInfo ?: PageInfo())
    }

    /** Browse with filters. */
    suspend fun browse(
        page: Int = 1,
        perPage: Int = 24,
        season: String? = null,
        year: Int? = null,
        genre: String? = null,
        format: String? = null,
        status: String? = null,
        sort: String = "POPULARITY_DESC",
    ): Pair<List<Anime>, PageInfo> {
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
            if (season != null) put("season", season)
            if (year != null) put("year", year)
            if (genre != null) put("genre", genre)
            if (format != null) put("format", format)
            if (status != null) put("status", status)
            put("sort", sort)
        }
        val raw = execute(AniListQueries.BROWSE, vars)
        val parsed = json.decodeFromString(PageMediaResponse.serializer(), raw)
        val pageData = parsed.data?.Page
        return (pageData?.media ?: emptyList()) to (pageData?.pageInfo ?: PageInfo())
    }

    /** Search. */
    suspend fun search(
        query: String,
        page: Int = 1,
        perPage: Int = 24,
    ): Pair<List<Anime>, PageInfo> {
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
            put("query", query)
        }
        val raw = execute(AniListQueries.SEARCH, vars)
        val parsed = json.decodeFromString(PageMediaResponse.serializer(), raw)
        val pageData = parsed.data?.Page
        return (pageData?.media ?: emptyList()) to (pageData?.pageInfo ?: PageInfo())
    }

    /** Schedule (airingSchedules) for a time range. */
    suspend fun schedule(
        from: Long,
        to: Long,
        page: Int = 1,
        perPage: Int = 50,
    ): Pair<List<AiringSchedule>, PageInfo> {
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
            put("from", from)
            put("to", to)
        }
        val raw = execute(AniListQueries.SCHEDULE, vars)
        val parsed = json.decodeFromString(PageScheduleResponse.serializer(), raw)
        val pageData = parsed.data?.Page
        return (pageData?.airingSchedules ?: emptyList()) to (pageData?.pageInfo ?: PageInfo())
    }

    /** Anime details (full nested data). */
    suspend fun animeDetails(id: Int): AnimeDetails? {
        val vars = buildJsonObject {
            put("id", id)
        }
        val raw = execute(AniListQueries.ANIME_DETAILS, vars)
        // Response shape: { data: { Media: AnimeDetails } }
        val parsed = json.parseToJsonElement(raw) as JsonObject
        val data = parsed["data"] as? JsonObject ?: return null
        val media = data["Media"] ?: return null
        return json.decodeFromString(AnimeDetails.serializer(), media.toString())
    }

    companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            explicitNulls = false
        }

        /** Create a default OkHttpClient with the configured timeouts. */
        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(Config.Network.CONNECT_TIMEOUT.toLong(), TimeUnit.SECONDS)
                .readTimeout(Config.Network.READ_TIMEOUT.toLong(), TimeUnit.SECONDS)
                .writeTimeout(Config.Network.WRITE_TIMEOUT.toLong(), TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}

class GraphQLException(message: String) : IOException(message)

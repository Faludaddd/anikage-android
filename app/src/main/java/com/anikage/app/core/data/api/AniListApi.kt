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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Low-level AniList GraphQL client.
 *
 * Uses OkHttp + kotlinx.serialization to POST GraphQL queries to AniList.
 * We use OkHttp directly (not Retrofit) because AniList is a single-endpoint
 * GraphQL API — Retrofit's multi-endpoint abstraction adds no value.
 *
 * Responses are typed: each `xxx()` method returns a parsed data class.
 *
 * ## Error taxonomy (v1.9.0)
 *
 * AniList is a third-party public API that can — and demonstrably does —
 * go dark for everyone (observed live: HTTP 403 "The AniList API has been
 * temporarily disabled due to severe stability issues."). Failures are
 * surfaced as typed exceptions so callers can react properly:
 *
 *  - [ApiHttpException] — non-2xx status, with the server's own message
 *    parsed out of the body when present (AniList returns GraphQL-style
 *    `{"errors":[…]}` bodies even on 403/429). `isBlocked` (403),
 *    `isRateLimited` (429, includes Retry-After), `isServerError` (5xx).
 *  - [GraphQLException] — 200 responses carrying GraphQL errors.
 *  - plain [IOException] — network-level failures.
 *
 * No automatic retries anywhere: the repository's single-flight layer
 * already collapses concurrent duplicate calls, and screens offer explicit
 * retry actions. Blind retries against a rate-limited endpoint are what
 * turn a 429 into an IP ban.
 *
 * A client-side rate gate ([rateGate]) keeps the app well under AniList's
 * documented 90 req/min (30 when degraded) so normal browsing can never
 * trip the limit.
 */
class AniListApi(
    private val client: OkHttpClient,
    private val json: Json = defaultJson,
) {

    private val endpoint = Config.ANILIST_API_URL

    /** Rolling-window rate gate (requests in the last 60s). */
    private val rateMutex = Mutex()
    private val requestTimes = ArrayDeque<Long>()

    /**
     * Client-side rate limiting: blocks (suspends) until a slot in the
     * rolling 60-second window is free. Capped below AniList's documented
     * limit so the app is a good citizen even during aggressive browsing.
     */
    private suspend fun rateGate() {
        while (true) {
            val waitMs = rateMutex.withLock {
                val now = System.currentTimeMillis()
                while (requestTimes.isNotEmpty() && now - requestTimes.first() >= 60_000L) {
                    requestTimes.removeFirst()
                }
                if (requestTimes.size < Config.Network.ANILIST_MAX_PER_MINUTE) {
                    requestTimes.addLast(now)
                    null
                } else {
                    requestTimes.first() + 60_000L - now
                }
            }
            if (waitMs == null) return
            AppLogger.w(LogCategory.NETWORK, "AniList rate-gate: waiting ${waitMs}ms (local limiter)")
            delay(waitMs)
        }
    }

    /** Generic GraphQL request. Returns the raw response JSON. */
    private suspend fun execute(query: String, variables: JsonObject): String {
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

    private suspend fun executeInternal(query: String, variables: JsonObject): String {
        rateGate()
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
                // AniList returns GraphQL-style {"errors":[{message…}]} bodies
                // even on 403/429 — surface the server's own reason instead
                // of a bare "HTTP 403: Forbidden".
                val serverMessage = extractErrorMessage(raw)
                val retryAfter = response.header("Retry-After")
                val text = buildString {
                    append("HTTP ${response.code}")
                    if (serverMessage != null) append(" — $serverMessage")
                    if (retryAfter != null) append(" (retry after ${retryAfter}s)")
                }
                throw ApiHttpException(response.code, text, serverMessage, retryAfter?.toIntOrNull())
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

    /** Best-effort `{"errors":[{"message":…}]}` extraction from an error body. */
    private fun extractErrorMessage(raw: String): String? {
        return try {
            val obj = json.parseToJsonElement(raw) as? JsonObject ?: return null
            val errors = obj["errors"]
            (errors as? kotlinx.serialization.json.JsonArray)
                ?.firstOrNull()
                ?.let { (it as? JsonObject)?.get("message") }
                ?.toString()?.trim('"')
        } catch (_: Exception) {
            null
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

/**
 * A non-2xx HTTP response from AniList, carrying the server's own message
 * (parsed from the GraphQL-style error body) when available.
 */
class ApiHttpException(
    val code: Int,
    message: String,
    /** Server-provided reason (e.g. "The AniList API has been temporarily
     * disabled due to severe stability issues."). */
    val serverMessage: String? = null,
    /** Seconds the server asked us to wait (429 responses). */
    val retryAfterSeconds: Int? = null,
) : IOException(message) {
    val isBlocked: Boolean get() = code == 403
    val isRateLimited: Boolean get() = code == 429
    val isServerError: Boolean get() = code in 500..599

    /** Human-readable, honest explanation for a UI notice. */
    fun userMessage(): String = when {
        isBlocked && serverMessage != null ->
            "AniList is refusing requests right now — $serverMessage"
        isBlocked -> "AniList is refusing requests right now (HTTP 403)."
        isRateLimited -> "AniList rate limit reached${retryAfterSeconds?.let { " — retry in ${it}s" } ?: ""}."
        isServerError -> "AniList is having server problems (HTTP $code)."
        else -> "AniList request failed (HTTP $code${serverMessage?.let { " — $it" } ?: ""})."
    }
}

package com.anikage.app.core.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.anikage.app.Config
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * REAL authentication against Anikage's own auth service (auth.anikage.cc,
 * a better-auth server — the same backend anikage.cc's login popup uses).
 *
 * Live-verified endpoints (see worklog v2.2.0):
 *   POST {auth}/api/auth/sign-in/email     {email, password}
 *   POST {auth}/api/auth/sign-in/username  {username, password}
 *   POST {auth}/api/auth/sign-out
 *   GET  {auth}/api/auth/get-session       -> null | {session, user}
 *   POST {auth}/api/comments               (requires the session cookie)
 *
 * Sign-in needs NO captcha (live-verified: bad creds -> 401 INVALID_*,
 * so real creds authenticate). Account CREATION on the service requires a
 * captcha, so sign-up routes to the site itself — honestly, not faked.
 *
 * Session transport: the sign-in response returns the session token in the
 * JSON body AND sets httpOnly session cookies. Native HTTP clients see
 * Set-Cookie headers fine (httpOnly only hides them from browser JS), so
 * we capture every Set-Cookie pair and replay them verbatim on subsequent
 * authenticated calls — robust regardless of the cookie naming the server
 * uses (`better-auth.session_token`, `__Secure-` prefixed, etc.).
 */
object AuthManager {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    /** Authenticated user profile (better-auth user + Anikage profile fields). */
    @Serializable
    data class AuthUser(
        val id: String = "",
        val email: String? = null,
        val emailVerified: Boolean = false,
        val username: String? = null,
        val displayName: String? = null,
        val bio: String? = null,
        /** Site-relative avatar path, e.g. /assets/avatars/…/9.png. */
        val avatarImage: String? = null,
        val avatarFrame: String? = null,
        val bannerImage: String? = null,
        val role: String? = null,
        val isPublic: Boolean = true,
        val createdAt: String? = null,
    ) {
        val displayLabel: String get() = displayName?.takeIf { it.isNotBlank() } ?: username ?: email ?: "Account"
        /** Full avatar URL (the API returns site-relative paths). */
        fun avatarUrl(): String? = avatarImage?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http")) it else "${Config.ANIKAGE_SITE_ORIGIN}$it"
        }
    }

    /** Result of a sign-in attempt. */
    sealed interface SignInResult {
        data class Success(val user: AuthUser) : SignInResult
        data class Failure(val message: String) : SignInResult
    }

    /** Observable session state — read by the top bar, comments, settings. */
    var user: AuthUser? by mutableStateOf(null)
        private set
    var sessionValidating: Boolean by mutableStateOf(false)
        private set

    /** Raw cookie replay string ("name1=value1; name2=value2"). */
    @Volatile
    private var cookieHeader: String? = null

    /** Bearer token from the sign-in body (better-auth also accepts it). */
    @Volatile
    private var bearerToken: String? = null

    val isAuthenticated: Boolean get() = user != null

    private const val PREFS = "anikage_auth"
    private const val KEY_COOKIE = "session_cookies"
    private const val KEY_BEARER = "session_token"
    private const val KEY_USER = "session_user"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Config.Network.CONNECT_TIMEOUT.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(Config.Network.READ_TIMEOUT.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(Config.Network.WRITE_TIMEOUT.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val authBase: String
        get() = Config.ANIKAGE_AUTH_API_BASE_URL ?: "https://auth.anikage.cc"

    // ------------------------------------------------------------------
    //  Session restore
    // ------------------------------------------------------------------

    /** Restore the persisted session on app start and re-validate it. */
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cookieHeader = prefs.getString(KEY_COOKIE, null)?.takeIf { it.isNotBlank() }
        bearerToken = prefs.getString(KEY_BEARER, null)?.takeIf { it.isNotBlank() }
        val savedUser = prefs.getString(KEY_USER, null)?.let {
            runCatching { json.decodeFromString<AuthUser>(it) }.getOrNull()
        }
        user = savedUser
        if (cookieHeader != null || bearerToken != null) {
            // Re-validate in the background; a stale session clears itself.
            refreshSession(context)
        }
    }

    /** Re-fetch /get-session and update [user] (also used after login). */
    fun refreshSession(context: Context) {
        if (cookieHeader == null && bearerToken == null) return
        sessionValidating = true
        Thread {
            val session = fetchSession()
            sessionValidating = false
            if (session != null) {
                user = session
                persist(context)
                AppLogger.d(LogCategory.NETWORK, "Auth session valid (${session.displayLabel})")
            } else {
                // 401/expired — the cookies we hold no longer authenticate.
                user = null
                cookieHeader = null
                bearerToken = null
                persist(context)
                AppLogger.d(LogCategory.NETWORK, "Auth session expired — signed out")
            }
        }.start()
    }

    /** GET /api/auth/get-session -> user, or null when unauthenticated. */
    private fun fetchSession(): AuthUser? = runCatching {
        val builder = Request.Builder()
            .url("$authBase/api/auth/get-session")
            .get()
        cookieHeader?.let { builder.header("Cookie", it) }
        val request = builder
            .header("User-Agent", Config.Network.USER_AGENT)
            .header("Accept", "application/json")
            .header("Referer", "${Config.ANIKAGE_SITE_ORIGIN}/")
            .header("Origin", Config.ANIKAGE_SITE_ORIGIN)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            if (body.isBlank() || body.trim() == "null") return null
            val parsed = json.decodeFromString<SessionResponse>(body)
            parsed.user
        }
    }.getOrNull()

    @Serializable
    private data class SessionResponse(
        val session: SessionInfo? = null,
        val user: AuthUser? = null,
    ) {
        @Serializable
        data class SessionInfo(val id: String? = null, val expiresAt: String? = null)
    }

    // ------------------------------------------------------------------
    //  Sign in / out
    // ------------------------------------------------------------------

    /**
     * Sign in with email OR username (better-auth's two credential flows —
     * the site's login popup supports both). Tries the mode matching the
     * input shape first, then the other on failure.
     */
    fun signIn(context: Context, identifier: String, password: String): SignInResult {
        val id = identifier.trim()
        if (id.isEmpty() || password.isEmpty()) {
            return SignInResult.Failure("Enter your email/username and password.")
        }
        val looksLikeEmail = id.contains('@')
        val first = if (looksLikeEmail) "sign-in/email" to mapOf("email" to id, "password" to password)
        else "sign-in/username" to mapOf("username" to id, "password" to password)
        val second = if (looksLikeEmail) "sign-in/username" to mapOf("username" to id, "password" to password)
        else "sign-in/email" to mapOf("email" to id, "password" to password)

        val firstResult = postSignIn(first.first, json.encodeToString(first.second))
        if (firstResult is SignInResult.Success) {
            persist(context)
            AppLogger.i(LogCategory.NETWORK, "Signed in as ${firstResult.user.displayLabel}")
            return firstResult
        }
        val secondResult = postSignIn(second.first, json.encodeToString(second.second))
        if (secondResult is SignInResult.Success) {
            persist(context)
            AppLogger.i(LogCategory.NETWORK, "Signed in as ${secondResult.user.displayLabel}")
            return secondResult
        }
        // Report the more informative failure (prefer the matched mode).
        return (firstResult as? SignInResult.Failure) ?: (secondResult as SignInResult.Failure)
            ?: SignInResult.Failure("Sign-in failed. Please try again.")
    }

    @Serializable
    private data class SignInResponse(val token: String? = null, val user: AuthUser? = null)

    private fun postSignIn(path: String, payload: String): SignInResult {
        val url = "$authBase/api/auth/$path"
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", Config.Network.USER_AGENT)
                .header("Accept", "application/json")
                .header("Referer", "${Config.ANIKAGE_SITE_ORIGIN}/")
                .header("Origin", Config.ANIKAGE_SITE_ORIGIN)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val message = runCatching {
                        json.decodeFromString<ErrorBody>(text).message
                    }.getOrNull() ?: "Sign-in failed (HTTP ${response.code})."
                    AppLogger.d(LogCategory.NETWORK, "Auth sign-in $path -> ${response.code}: $message")
                    return SignInResult.Failure(message)
                }
                val parsed = runCatching { json.decodeFromString<SignInResponse>(text) }.getOrNull()
                val authUser = parsed?.user
                    ?: return SignInResult.Failure("The server didn't return a user. Try again.")
                // Capture cookies for all future authenticated calls.
                val setCookies = response.headers("Set-Cookie")
                if (setCookies.isNotEmpty()) {
                    cookieHeader = setCookies
                        .map { it.substringBefore(';').trim() }
                        .filter { it.contains('=') }
                        .joinToString("; ")
                }
                bearerToken = parsed.token?.takeIf { it.isNotBlank() }
                user = authUser
                SignInResult.Success(authUser)
            }
        }.getOrElse {
            AppLogger.e(LogCategory.NETWORK, "Auth sign-in network failure", it)
            SignInResult.Failure("Network error while signing in. Check your connection.")
        }
    }

    /** Sign out on the server + clear the local session. */
    fun signOut(context: Context) {
        val name = user?.displayLabel
        runCatching {
            val builder = Request.Builder()
                .url("$authBase/api/auth/sign-out")
                .post(ByteArray(0).toRequestBody(null))
            cookieHeader?.let { builder.header("Cookie", it) }
            val request = builder
                .header("User-Agent", Config.Network.USER_AGENT)
                .header("Accept", "application/json")
                .header("Referer", "${Config.ANIKAGE_SITE_ORIGIN}/")
                .header("Origin", Config.ANIKAGE_SITE_ORIGIN)
                .build()
            client.newCall(request).execute().use { /* best-effort */ }
        }
        user = null
        cookieHeader = null
        bearerToken = null
        persist(context)
        AppLogger.i(LogCategory.NETWORK, "Signed out${name?.let { " ($it)" } ?: ""}")
    }

    // ------------------------------------------------------------------
    //  Authenticated request helper (used by the comments API)
    // ------------------------------------------------------------------

    /**
     * Add the session credentials to an OkHttp request builder: replays the
     * captured Set-Cookie pairs (exactly what the browser would send).
     */
    fun authorize(builder: Request.Builder): Request.Builder {
        cookieHeader?.let { builder.header("Cookie", it) }
        return builder
    }

    @Serializable
    private data class ErrorBody(val message: String? = null, val code: String? = null)

    /** Persist / clear the session in private prefs. */
    private fun persist(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_COOKIE, cookieHeader)
            putString(KEY_BEARER, bearerToken)
            putString(KEY_USER, user?.let { runCatching { json.encodeToString(it) }.getOrNull() })
            apply()
        }
    }

}

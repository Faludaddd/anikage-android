package com.anikage.app.core.log

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Severity of a log entry, ordered from most to least verbose. */
enum class LogLevel(val short: String, val priority: Int) {
    VERBOSE("V", Log.VERBOSE),
    DEBUG("D", Log.DEBUG),
    INFO("I", Log.INFO),
    WARN("W", Log.WARN),
    ERROR("E", Log.ERROR),
}

/** Functional area a log entry belongs to — used for filtering in the Logger UI. */
enum class LogCategory(val label: String) {
    APP("App"),
    UI("UI"),
    DATA("Data"),
    NETWORK("Network"),
    PLAYER("Player"),
    SYSTEM("System"),
}

/** A single in-app log record. */
data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val category: LogCategory,
    val message: String,
    val error: String? = null,
)

/**
 * In-app logger with a bounded ring buffer, exposed to the UI as a
 * [StateFlow] so the Logger screen updates live. Every entry is also
 * mirrored to logcat (tag "Anikage") AND persisted to the current
 * session file by [SessionLogger] — so logs survive crashes and each
 * app start opens a separate, never-overwritten session.
 *
 * All app subsystems log through this object; the Diagnostics screen in
 * Settings is the surface for browsing sessions, searching, filtering,
 * exporting, and reading crash traces.
 */
object AppLogger {

    private const val MAX_ENTRIES = 2000
    private const val PREFS_NAME = "anikage_logger"
    private const val KEY_VERBOSE = "verbose_enabled"

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>()
    private var nextId = 0L

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    /** When false (default), VERBOSE entries are dropped. Persisted. */
    @Volatile
    var verboseEnabled: Boolean = false
        private set

    /** Call once from [com.anikage.app.AnikageApp.onCreate]. */
    fun init(context: Context) {
        val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        verboseEnabled = prefs.getBoolean(KEY_VERBOSE, false)
        // Open a new persistent session (and finalise the previous one).
        SessionLogger.startSession(
            context,
            versionName(context),
            versionCode(context),
        )
        installCrashHandler()
        log(
            LogLevel.INFO, LogCategory.APP,
            "Logger initialised — verbose=${verboseEnabled}",
        )
        log(
            LogLevel.INFO, LogCategory.APP,
            "Session #${SessionLogger.allSessions().firstOrNull()?.seq ?: "?"} started " +
                "(previous session: ${SessionLogger.previousCrash?.let { "CRASHED — ${it.errorCount} errors" } ?: "clean"})",
        )
    }

    private fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" }.getOrDefault("?")

    private fun versionCode(context: Context): Long =
        runCatching {
            val pm = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) pm.longVersionCode else pm.versionCode.toLong()
        }.getOrDefault(0L)

    fun setVerbose(enabled: Boolean, context: Context) {
        verboseEnabled = enabled
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_VERBOSE, enabled)
                .apply()
        }
        log(LogLevel.INFO, LogCategory.APP, "Verbose logging ${if (enabled) "enabled" else "disabled"}")
    }

    /** Core entry point. Thread-safe. */
    fun log(level: LogLevel, category: LogCategory, message: String, error: Throwable? = null) {
        if (level == LogLevel.VERBOSE && !verboseEnabled) return
        val entry = LogEntry(
            id = synchronized(lock) { nextId++ },
            timestamp = System.currentTimeMillis(),
            level = level,
            category = category,
            message = message,
            error = error?.let { "${it.javaClass.simpleName}: ${it.message}" },
        )
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            _entries.value = buffer.toList()
        }
        // Persist to the session file (crash-safe, background flush).
        SessionLogger.append(entry)
        runCatching {
            Log.println(
                level.priority,
                "Anikage/${category.label}",
                message + (entry.error?.let { "\n$it" } ?: ""),
            )
        }
    }

    fun v(category: LogCategory, message: String) = log(LogLevel.VERBOSE, category, message)
    fun d(category: LogCategory, message: String) = log(LogLevel.DEBUG, category, message)
    fun i(category: LogCategory, message: String) = log(LogLevel.INFO, category, message)
    fun w(category: LogCategory, message: String, error: Throwable? = null) =
        log(LogLevel.WARN, category, message, error)

    fun e(category: LogCategory, message: String, error: Throwable? = null) =
        log(LogLevel.ERROR, category, message, error)

    /** Remove all buffered entries. */
    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
        }
        log(LogLevel.INFO, LogCategory.APP, "Log buffer cleared")
    }

    /** Full log as plain text, with a header of useful device/app facts. */
    fun exportText(context: Context): String {
        val sb = StringBuilder()
        sb.append("Anikage log export\n")
        repeat(50) { sb.append('=') }
        sb.append('\n')
        deviceInfo(context).forEach { (k, v) -> sb.append(k).append(": ").append(v).append('\n') }
        repeat(50) { sb.append('=') }
        sb.append('\n')
        val snapshot = synchronized(lock) { buffer.toList() }
        sb.append("Entries: ").append(snapshot.size).append("\n\n")
        snapshot.forEach { e ->
            sb.append(formatTimestamp(e.timestamp, fullDate = true))
                .append(' ').append(e.level.short).append('/').append(e.category.label)
                .append(": ").append(e.message)
            e.error?.let { sb.append('\n').append(it) }
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Useful technical facts shown in the Logger screen and exports. */
    @Suppress("DEPRECATION")
    fun deviceInfo(context: Context): List<Pair<String, String>> = buildList {
        add("Device" to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        add("Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        runCatching {
            val pm = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) pm.longVersionCode else pm.versionCode.toLong()
            add("App version" to "${pm.versionName} ($code)")
        }
        add("Package" to context.packageName)
        add("Cores" to Runtime.getRuntime().availableProcessors().toString())
        add("Java VM" to (System.getProperty("java.vm.version") ?: "unknown"))
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            add("Memory" to "${mi.availMem / (1024 * 1024)} MB free of ${mi.totalMem / (1024 * 1024)} MB")
            add("Low memory" to if (mi.lowMemory) "yes" else "no")
        }
        val rt = Runtime.getRuntime()
        add("App heap" to "${rt.freeMemory() / (1024 * 1024)} MB free of ${rt.maxMemory() / (1024 * 1024)} MB max")
        runCatching {
            val dm = context.resources.displayMetrics
            add("Screen" to "${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi}dpi (${dm.density}x)")
        }
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            add("Network" to when {
                caps == null -> "offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            })
        }
    }

    private fun formatTimestamp(timestamp: Long, fullDate: Boolean): String {
        val fmt = if (fullDate) "yyyy-MM-dd HH:mm:ss.SSS" else "HH:mm:ss.SSS"
        return java.text.SimpleDateFormat(fmt, java.util.Locale.US).format(java.util.Date(timestamp))
    }

    /**
     * Crash capture: the stack trace is persisted SYNCHRONOUSLY by
     * [SessionLogger.recordCrash] (so it survives the process death), an
     * ERROR entry is best-effort logged, then the default handler runs.
     */
    private fun installCrashHandler() {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { SessionLogger.recordCrash(throwable) }
            runCatching {
                Log.e(
                    "Anikage/SYSTEM",
                    "CRASH: ${throwable.javaClass.simpleName}: ${throwable.message}\n" +
                        Log.getStackTraceString(throwable),
                )
            }
            current?.uncaughtException(thread, throwable)
        }
    }

    /** Clean-shutdown marker — call from MainActivity.onDestroy(isFinishing). */
    fun markSessionCompleted() {
        runCatching { SessionLogger.markCompleted() }
    }

    /** Periodic heartbeat — call from MainActivity every ~30s. */
    fun heartbeat() {
        runCatching { SessionLogger.heartbeat() }
    }

    /** The previous session's crash info (null when it shut down cleanly). */
    val previousCrash: SessionLogger.SessionMeta?
        get() = SessionLogger.previousCrash
}

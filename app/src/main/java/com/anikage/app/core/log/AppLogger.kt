package com.anikage.app.core.log

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import java.io.File
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
 * mirrored to logcat (tag "Anikage") so `adb logcat` shows the same
 * stream.
 *
 * All app subsystems log through this object; the Logger screen in
 * Settings is the diagnostics surface (search, filter, export, etc.).
 * This replaces the need for a separate debug build: the production
 * build carries the same diagnostics.
 */
object AppLogger {

    private const val MAX_ENTRIES = 2000
    private const val PREFS_NAME = "anikage_logger"
    private const val KEY_VERBOSE = "verbose_enabled"
    private const val CRASH_FILE = "last_crash.txt"
    private const val CRASH_FILE_MAX_CHARS = 8000

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>()
    private var nextId = 0L
    private var crashFile: File? = null

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
        crashFile = File(context.filesDir, CRASH_FILE)
        installCrashHandler()
        log(
            LogLevel.INFO, LogCategory.APP,
            "Logger initialised — verbose=${verboseEnabled}",
        )
        // Surface the previous session's crash (if any) so it is visible
        // in the Logger screen after the app restarts.
        crashFile?.let { file ->
            if (file.exists()) {
                runCatching {
                    val content = file.readText().take(CRASH_FILE_MAX_CHARS)
                    log(LogLevel.ERROR, LogCategory.SYSTEM, "Previous session crashed:\n$content")
                    file.delete()
                }
            }
        }
    }

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
     * Best-effort crash capture: writes the stack trace to disk, then the
     * default handler takes over (process still dies normally). The next
     * [init] re-logs it so crashes are visible in the Logger screen.
     */
    private fun installCrashHandler() {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                crashFile?.writeText(
                    buildString {
                        append("Time: ").append(java.util.Date()).append('\n')
                        append("Thread: ").append(thread.name).append('\n')
                        append(Log.getStackTraceString(throwable))
                    },
                )
            }
            current?.uncaughtException(thread, throwable)
        }
    }
}

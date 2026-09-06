package com.anikage.app.core.log

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Crash-safe, persistent, per-session log storage.
 *
 * Requirements this implements (user spec):
 *  8.  Real persistent logging — every entry is appended to a per-session
 *      file on disk, not just RAM.
 *  9.  Logs survive crashes — writes are flushed continuously; the crash
 *      handler additionally writes the crash synchronously (no coroutine,
 *      no executor) before the process dies.
 * 10.  Every app start = a NEW session. Sessions are never overwritten:
 *      session-N.log.jsonl + session-N.meta.json. Older sessions stay
 *      readable until the retention cap (30) trims the oldest.
 * 12.  Crash recovery — the previous session's status is finalised at the
 *      next launch: anything that did not end with an explicit
 *      COMPLETED marker is marked CRASHED, together with the captured
 *      stack trace, ready to show in the recovery banner.
 *
 * Threading model:
 *  - Normal entries: appended on a single background thread (ordered,
 *    non-blocking for callers) with an immediate flush.
 *  - Crash finalisation: pure synchronous File I/O from the uncaught
 *    exception handler. This must complete before the process dies.
 */
object SessionLogger {

    private const val DIR = "sessions"
    private const val RETENTION = 30

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Session lifecycle, mirroring the spec's session list statuses. */
    enum class Status { ACTIVE, COMPLETED, CRASHED }

    @Serializable
    data class SessionMeta(
        val seq: Int,
        val startTime: Long,
        var endTime: Long? = null,
        var status: String = Status.ACTIVE.name,
        val appVersion: String = "",
        val appVersionCode: Long = 0,
        val androidVersion: String = "",
        val device: String = "",
        var entryCount: Int = 0,
        var errorCount: Int = 0,
        var warnCount: Int = 0,
        var crashTrace: String? = null,
        var lastHeartbeat: Long = 0,
    ) {
        val statusEnum: Status get() = runCatching { Status.valueOf(status) }.getOrDefault(Status.CRASHED)
        val durationMs: Long get() = (endTime ?: lastHeartbeat) - startTime
    }

    /** One serialised log line inside a session's .log.jsonl file. */
    @Serializable
    data class StoredEntry(
        val id: Long,
        val ts: Long,
        val level: String,
        val category: String,
        val message: String,
        val error: String? = null,
    )

    // ── state ────────────────────────────────────────────────────────────
    private val disk = Executors.newSingleThreadExecutor { r -> Thread(r, "anikage-session-log").apply { isDaemon = true } }
    private val openSeq = AtomicInteger(-1)
    @Volatile private var ctx: Context? = null
    @Volatile private var currentMeta: SessionMeta? = null
    @Volatile private var currentLog: File? = null

    /** Info about the PREVIOUS session when it crashed — for the recovery banner. */
    @Volatile var previousCrash: SessionMeta? = null
        private set

    // ── lifecycle ────────────────────────────────────────────────────────

    /**
     * Called from Application.onCreate BEFORE anything else logs:
     * finalises the previous session (crash detection), opens a new one.
     */
    fun startSession(context: Context, appVersion: String, appVersionCode: Long) {
        ctx = context.applicationContext
        val dir = File(context.filesDir, DIR).apply { mkdirs() }

        // 1. Finalise every non-closed session from previous runs.
        var newestCrashed: SessionMeta? = null
        listMetaFiles(dir).forEach { file ->
            runCatching {
                val meta = json.decodeFromString(SessionMeta.serializer(), file.readText())
                if (meta.statusEnum == Status.ACTIVE) {
                    // A session that never got an explicit COMPLETED marker did
                    // not shut down normally -> CRASHED (or hard-killed).
                    meta.status = Status.CRASHED.name
                    meta.endTime = meta.lastHeartbeat
                    if (meta.crashTrace == null) {
                        meta.crashTrace = "Session ended without a clean shutdown " +
                            "(process died or was killed). Last activity: ${formatTime(meta.lastHeartbeat)}."
                    }
                    writeMetaSync(file, meta)
                    if (newestCrashed == null || meta.seq > newestCrashed!!.seq) newestCrashed = meta
                }
            }
        }
        previousCrash = newestCrashed

        // 2. Rotate: keep only the newest RETENTION sessions.
        listMetaFiles(dir).mapNotNull { f ->
            runCatching { json.decodeFromString(SessionMeta.serializer(), f.readText()) }.getOrNull()
        }.sortedByDescending { it.seq }
            .drop(RETENTION)
            .forEach { meta ->
                File(dir, "session-${meta.seq}.meta.json").delete()
                File(dir, "session-${meta.seq}.log.jsonl").delete()
            }

        // 3. Open the new session.
        val nextSeq = (listMetaFiles(dir).mapNotNull { f ->
            runCatching { json.decodeFromString(SessionMeta.serializer(), f.readText()).seq }.getOrNull()
        }.maxOrNull() ?: 0) + 1
        openSeq.set(nextSeq)

        val meta = SessionMeta(
            seq = nextSeq,
            startTime = System.currentTimeMillis(),
            lastHeartbeat = System.currentTimeMillis(),
            appVersion = appVersion,
            appVersionCode = appVersionCode,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
        currentMeta = meta
        currentLog = File(dir, "session-$nextSeq.log.jsonl")
        writeMetaSync(metaFile(dir, nextSeq), meta)
    }

    /** Append one entry to the current session file (background, flushed). */
    fun append(entry: com.anikage.app.core.log.LogEntry) {
        val seq = openSeq.get()
        val logFile = currentLog ?: return
        if (seq < 0) return
        disk.execute {
            runCatching {
                val stored = StoredEntry(
                    id = entry.id, ts = entry.timestamp, level = entry.level.name,
                    category = entry.category.name, message = entry.message, error = entry.error,
                )
                FileOutputStream(logFile, true).use { out ->
                    out.write((json.encodeToString(StoredEntry.serializer(), stored) + "\n").toByteArray())
                    out.flush()
                }
                val meta = currentMeta ?: return@execute
                meta.entryCount++
                if (entry.level == com.anikage.app.core.log.LogLevel.ERROR) meta.errorCount++
                if (entry.level == com.anikage.app.core.log.LogLevel.WARN) meta.warnCount++
                meta.lastHeartbeat = System.currentTimeMillis()
                writeMetaSync(metaFile(logFile.parentFile, seq), meta)
            }
        }
    }

    /** Periodic heartbeat so duration/status stay fresh (every ~30s is fine). */
    fun heartbeat() {
        val meta = currentMeta ?: return
        meta.lastHeartbeat = System.currentTimeMillis()
        val dir = currentLog?.parentFile ?: return
        disk.execute { writeMetaSync(metaFile(dir, meta.seq), meta) }
    }

    /**
     * SYNCHRONOUS crash finalisation — called from the uncaught exception
     * handler with the stack trace. Must not touch the executor.
     */
    fun recordCrash(throwable: Throwable) {
        val meta = currentMeta ?: return
        val dir = currentLog?.parentFile ?: return
        runCatching {
            meta.status = Status.CRASHED.name
            meta.endTime = System.currentTimeMillis()
            meta.crashTrace = "Thread: ${Thread.currentThread().name}\n" + Log.getStackTraceString(throwable)
            if (meta.errorCount == 0) meta.errorCount = 1
            writeMetaSync(metaFile(dir, meta.seq), meta)
        }
    }

    /** Best-effort clean shutdown marker (MainActivity finishing). */
    fun markCompleted() {
        val meta = currentMeta ?: return
        val dir = currentLog?.parentFile ?: return
        runCatching {
            meta.status = Status.COMPLETED.name
            meta.endTime = System.currentTimeMillis()
            writeMetaSync(metaFile(dir, meta.seq), meta)
        }
    }

    // ── reads for the Diagnostics UI ─────────────────────────────────────

    /** All stored sessions, newest first. The in-flight session is included. */
    fun allSessions(): List<SessionMeta> {
        val dir = File(ctx?.filesDir, DIR) ?: return emptyList()
        if (!dir.exists()) return emptyList()
        return listMetaFiles(dir).mapNotNull { f ->
            runCatching { json.decodeFromString(SessionMeta.serializer(), f.readText()) }.getOrNull()
        }.sortedByDescending { it.seq }
    }

    /** Entries of a stored session, oldest first. */
    fun readEntries(seq: Int): List<StoredEntry> {
        val dir = File(ctx?.filesDir, DIR) ?: return emptyList()
        val file = File(dir, "session-$seq.log.jsonl")
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
                runCatching { json.decodeFromString(StoredEntry.serializer(), line) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    /** Plain-text export of one session (for the copy/share buttons). */
    fun exportSession(context: Context, seq: Int): String {
        val meta = allSessions().firstOrNull { it.seq == seq } ?: return "Session $seq not found"
        val sb = StringBuilder()
        sb.append("Anikage Diagnostics — Session #").append(meta.seq).append('\n')
        repeat(60) { sb.append('=') }
        sb.append('\n')
        sb.append("Status:      ").append(meta.status).append('\n')
        sb.append("Started:     ").append(formatTimeFull(meta.startTime)).append('\n')
        sb.append("Ended:       ").append(meta.endTime?.let { formatTimeFull(it) } ?: "—").append('\n')
        sb.append("Duration:    ").append(formatDuration(meta.durationMs)).append('\n')
        sb.append("App version: ").append(meta.appVersion).append(" (").append(meta.appVersionCode).append(")\n")
        sb.append("Device:      ").append(meta.device).append(" — ").append(meta.androidVersion).append('\n')
        sb.append("Entries:     ").append(meta.entryCount)
            .append(" (").append(meta.errorCount).append(" errors, ").append(meta.warnCount).append(" warnings)\n")
        meta.crashTrace?.let { sb.append("Crash:\n").append(it).append('\n') }
        repeat(60) { sb.append('=') }
        sb.append('\n')
        readEntries(seq).forEach { e ->
            sb.append(formatTimeFull(e.ts)).append(' ').append(e.level.first()).append('/')
                .append(e.category.lowercase(Locale.US)).append(": ").append(e.message)
            e.error?.let { sb.append('\n').append(it) }
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Delete every stored session (Settings "Clear diagnostics"). */
    fun clearAll() {
        val dir = File(ctx?.filesDir, DIR) ?: return
        disk.execute {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun metaFile(dir: File, seq: Int) = File(dir, "session-$seq.meta.json")

    private fun listMetaFiles(dir: File): List<File> =
        dir.listFiles { f -> f.name.endsWith(".meta.json") }?.sortedBy { it.name } ?: emptyList()

    private fun writeMetaSync(file: File, meta: SessionMeta) {
        runCatching {
            FileOutputStream(file, false).use { it.write(json.encodeToString(SessionMeta.serializer(), meta).toByteArray()) }
        }
    }

    fun formatTime(ts: Long): String =
        SimpleDateFormat("h:mm a", Locale.US).format(Date(ts))

    fun formatTimeFull(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(ts))

    fun formatDay(ts: Long): String = SimpleDateFormat("EEEE", Locale.US).format(Date(ts))

    fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /** Session label for the UI: "Current Session" or "Session #N". */
    fun label(meta: SessionMeta): String =
        if (meta.seq == openSeq.get() && meta.statusEnum == Status.ACTIVE) "Current Session" else "Session #${meta.seq}"
}

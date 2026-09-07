package com.anikage.app.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogLevel
import com.anikage.app.core.log.SessionLogger
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles

/**
 * DIAGNOSTICS — the app's session-based crash/error log browser.
 *
 * Structure (user spec):
 *   Diagnostics
 *   System health and application logs
 *   ┌──────────────────────────────────────────┐
 *   │ ● Current Session      12 errors         │  ← session list
 *   │   Started 7:42 PM                        │
 *   ├──────────────────────────────────────────┤
 *   │ ✕ Session #103          37 errors        │
 *   │   Crashed — 6:18 PM                      │
 *   ├──────────────────────────────────────────┤
 *   │ ✓ Session #102          2 warnings        │
 *   │   Completed — Yesterday                  │
 *   └──────────────────────────────────────────┘
 *
 * Tap a session → detail viewer: search, level filters, expandable entries
 * with stack traces, session facts, crash info, copy / share / clear.
 */
@Composable
fun DiagnosticsScreen(
    onBackClick: () -> Unit,
    initialSessionSeq: Int? = null,
) {
    val theme = LocalAnikageTheme.current
    var openSession by remember { mutableStateOf<Int?>(initialSessionSeq) }

    if (openSession != null) {
        SessionLogViewer(seq = openSession!!, onBack = { openSession = null })
        return
    }

    val sessions = remember { mutableStateOf(SessionLogger.allSessions()) }
    // Refresh the list when returning from the viewer or periodically.
    LaunchedEffect(Unit) {
        while (true) {
            sessions.value = SessionLogger.allSessions()
            kotlinx.coroutines.delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .statusBarsPadding(),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.fg)
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = "Diagnostics",
                    style = WebTextStyles.titleHero,
                    color = theme.fg,
                )
                Text(
                    text = "System health and application logs",
                    style = WebTextStyles.sm,
                    color = theme.fgMuted,
                )
            }
        }

        val list = sessions.value

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (list.isEmpty()) {
                item {
                    DiagnosticsEmpty()
                }
            }
            items(list, key = { it.seq }) { meta ->
                SessionCard(
                    meta = meta,
                    onClick = { openSession = meta.seq },
                )
            }
        }
    }
}

/** Status dot / icon colours. */
private fun statusColor(status: SessionLogger.Status): Color = when (status) {
    SessionLogger.Status.ACTIVE -> Color(0xFF4ADE80)      // green-400
    SessionLogger.Status.COMPLETED -> Color(0xFF38BDF8)   // sky-400
    SessionLogger.Status.CRASHED -> Color(0xFFF87171)     // red-400
}

/** One session row — status icon, label, time line, error/warn counts. */
@Composable
private fun SessionCard(meta: SessionLogger.SessionMeta, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    val status = meta.statusEnum
    val tint = statusColor(status)
    val timeLabel = when {
        status == SessionLogger.Status.CRASHED -> "Crashed — ${SessionLogger.formatTime(meta.endTime ?: meta.lastHeartbeat)}"
        status == SessionLogger.Status.ACTIVE -> "Started ${SessionLogger.formatTime(meta.startTime)}"
        else -> "Completed — ${SessionLogger.formatDay(meta.endTime ?: meta.startTime)}, ${SessionLogger.formatTime(meta.endTime ?: meta.startTime)}"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // Status mark — ● active / ✕ crashed / ✓ completed.
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f))
                .border(1.dp, tint.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (status) {
                SessionLogger.Status.ACTIVE -> Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(tint),
                )
                SessionLogger.Status.CRASHED -> Icon(
                    Icons.Default.Close, contentDescription = "Crashed",
                    tint = tint, modifier = Modifier.size(18.dp),
                )
                SessionLogger.Status.COMPLETED -> Icon(
                    Icons.Default.Check, contentDescription = "Completed",
                    tint = tint, modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = SessionLogger.label(meta),
                style = WebTextStyles.base,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = timeLabel,
                style = WebTextStyles.xs,
                color = theme.fgMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Error / warning counts.
        Column(horizontalAlignment = Alignment.End) {
            if (meta.errorCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(13.dp))
                    Text(
                        "${meta.errorCount} error${if (meta.errorCount == 1) "" else "s"}",
                        style = WebTextStyles.xs, color = Color(0xFFF87171),
                    )
                }
            } else if (meta.warnCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(13.dp))
                    Text(
                        "${meta.warnCount} warning${if (meta.warnCount == 1) "" else "s"}",
                        style = WebTextStyles.xs, color = Color(0xFFFBBF24),
                    )
                }
            } else {
                Text("No errors", style = WebTextStyles.xs, color = theme.fgMuted)
            }
        }
    }
}

@Composable
private fun DiagnosticsEmpty() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
    ) {
        Icon(
            Icons.Default.Check, contentDescription = null,
            tint = Color(0xFF71717A), modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("No sessions yet", style = WebTextStyles.base, color = LocalAnikageTheme.current.fg)
        Text(
            "Session logs are created each time the app opens.",
            style = WebTextStyles.sm, color = LocalAnikageTheme.current.fgMuted,
        )
    }
}

// ---------------------------------------------------------------------------
//  Session detail — the log viewer
// ---------------------------------------------------------------------------

@Composable
private fun SessionLogViewer(seq: Int, onBack: () -> Unit) {
    val theme = LocalAnikageTheme.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val meta = remember(seq) { SessionLogger.allSessions().firstOrNull { it.seq == seq } }
    // Current session streams live; stored sessions are read once.
    val liveEntries by AppLogger.entries.collectAsStateWithLifecycle()
    val storedEntries = remember(seq) { SessionLogger.readEntries(seq) }
    val isCurrent = meta?.statusEnum == SessionLogger.Status.ACTIVE

    var query by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf<LogLevel?>(null) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    val filtered = remember(liveEntries, storedEntries, query, levelFilter, isCurrent) {
        val source: List<LogTuple> =
            if (isCurrent) {
                liveEntries.map { LogTuple(it.id, it.level, it.category.label, it.message, it.error, it.timestamp, it.repeat) }
            } else {
                storedEntries.map { LogTuple(it.id, runCatching { LogLevel.valueOf(it.level) }.getOrDefault(LogLevel.INFO), it.category, it.message, it.error, it.ts, 0) }
            }
        source.filter { entry ->
            (levelFilter == null || entry.level == levelFilter) &&
                (query.isBlank() || entry.message.contains(query, ignoreCase = true) || entry.category.contains(query, ignoreCase = true))
        }
    }

    // Follow the tail while live.
    LaunchedEffect(isCurrent, filtered.size) {
        if (isCurrent && filtered.isNotEmpty() && expandedId == null) {
            listState.scrollToItem(filtered.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .statusBarsPadding(),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.fg)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = SessionLogger.label(meta ?: SessionLogger.SessionMeta(seq = seq, startTime = 0L)),
                    style = WebTextStyles.lg,
                    color = theme.fg,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${filtered.size} entries" + if (isCurrent) " — live" else "",
                    style = WebTextStyles.xs,
                    color = if (isCurrent) Color(0xFF4ADE80) else theme.fgMuted,
                )
            }
            IconButton(onClick = {
                val text = SessionLogger.exportSession(context, seq)
                clipboard.setText(AnnotatedString(text))
                Toast.makeText(context, "Session copied to clipboard", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy session", tint = theme.fgMuted)
            }
            IconButton(onClick = {
                val text = SessionLogger.exportSession(context, seq)
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, text),
                        "Share session logs",
                    ),
                )
            }) {
                Icon(Icons.Default.Share, contentDescription = "Share session", tint = theme.fgMuted)
            }
        }

        // Session facts strip.
        meta?.let { m ->
            SessionFacts(m)
        }

        // Search + level filters.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            LevelChip(null, levelFilter == null) { levelFilter = null }
            LevelChip(LogLevel.ERROR, levelFilter == LogLevel.ERROR) { levelFilter = if (levelFilter == LogLevel.ERROR) null else LogLevel.ERROR }
            LevelChip(LogLevel.WARN, levelFilter == LogLevel.WARN) { levelFilter = if (levelFilter == LogLevel.WARN) null else LogLevel.WARN }
            LevelChip(LogLevel.INFO, levelFilter == LogLevel.INFO) { levelFilter = if (levelFilter == LogLevel.INFO) null else LogLevel.INFO }
            LevelChip(LogLevel.DEBUG, levelFilter == LogLevel.DEBUG) { levelFilter = if (levelFilter == LogLevel.DEBUG) null else LogLevel.DEBUG }
        }

        // Crash banner.
        meta?.crashTrace?.let { trace ->
            CrashBanner(meta, trace)
        }

        // Log list.
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No entries", style = WebTextStyles.base, color = theme.fg)
                    Text(
                        if (query.isBlank() && levelFilter == null) "This session has no logs."
                        else "No entries match the current filters.",
                        style = WebTextStyles.sm, color = theme.fgMuted,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp,
                ),
            ) {
                items(filtered, key = { it.id }) { entry ->
                    LogRow(
                        id = entry.id,
                        ts = entry.timestamp,
                        level = entry.level,
                        category = entry.category,
                        message = if (entry.repeat > 0) "${entry.message} (×${entry.repeat + 1})" else entry.message,
                        error = entry.error,
                        expanded = expandedId == entry.id,
                        onToggle = { expandedId = if (expandedId == entry.id) null else entry.id },
                    )
                }
            }
        }
    }
}

/** Session facts — status/duration/version/device, site-card styling. */
@Composable
private fun SessionFacts(m: SessionLogger.SessionMeta) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact("Status", m.statusEnum.name.lowercase().replaceFirstChar { it.uppercase() })
            Fact("Duration", SessionLogger.formatDuration(m.durationMs))
            Fact("Version", m.appVersion)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact("Device", m.device)
            Fact("Started", SessionLogger.formatTimeFull(m.startTime))
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", style = WebTextStyles.xs, color = LocalAnikageTheme.current.fgMuted)
        Text(value, style = WebTextStyles.xs, color = LocalAnikageTheme.current.fg, fontWeight = FontWeight.Medium)
    }
}

/** Red crash banner with the top of the stack trace. */
@Composable
private fun CrashBanner(m: SessionLogger.SessionMeta, trace: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1AEC4899))
            .border(1.dp, Color(0x33EC4899), RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(12.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
            Text(
                text = "This session ended in a crash",
                style = WebTextStyles.sm,
                color = Color(0xFFFECACA),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.ExpandMore, contentDescription = null,
                tint = Color(0xFFF87171), modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = trace.take(4000),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp),
                color = Color(0xFFFCA5A5),
            )
        }
    }
}

/** Search field — site input styling (rounded-xl bg-white/5). */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x0DFFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = theme.fgMuted, modifier = Modifier.size(16.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Search logs", style = WebTextStyles.sm, color = Color(0xFF71717A))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = WebTextStyles.sm.copy(color = theme.fg),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Default.Close, contentDescription = "Clear",
                tint = theme.fgMuted, modifier = Modifier.size(14.dp).clickable { onValueChange("") },
            )
        }
    }
}

@Composable
private fun LevelChip(level: LogLevel?, selected: Boolean, onClick: () -> Unit) {
    val tint = when (level) {
        LogLevel.ERROR -> Color(0xFFF87171)
        LogLevel.WARN -> Color(0xFFFBBF24)
        LogLevel.INFO -> Color(0xFF7DD3FC)
        LogLevel.DEBUG -> Color(0xFFA3E635)
        null -> LocalAnikageTheme.current.fgMuted
        else -> LocalAnikageTheme.current.fgMuted
    }
    Text(
        text = level?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "All",
        style = WebTextStyles.xs,
        color = if (selected) tint else LocalAnikageTheme.current.fgMuted,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) tint.copy(alpha = 0.15f) else Color(0x0DFFFFFF))
            .border(1.dp, if (selected) tint.copy(alpha = 0.4f) else Color(0x0FFFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** One expandable log row: time, level chip, category, message, stack. */
@Composable
private fun LogRow(
    id: Long,
    ts: Long,
    level: LogLevel,
    category: String,
    message: String,
    error: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val levelColor = when (level) {
        LogLevel.ERROR -> Color(0xFFF87171)
        LogLevel.WARN -> Color(0xFFFBBF24)
        LogLevel.INFO -> Color(0xFF7DD3FC)
        LogLevel.DEBUG -> Color(0xFFA3E635)
        LogLevel.VERBOSE -> Color(0xFFA1A1AA)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (level == LogLevel.ERROR) Color(0x14EF4444)
                else if (level == LogLevel.WARN) Color(0x10F59E0B)
                else Color(0x05FFFFFF),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = SessionLogger.formatTimeFull(ts).let { it.substring(11) },  // HH:mm:ss.SSS
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp),
                color = Color(0xFF71717A),
                modifier = Modifier.width(86.dp),
            )
            Text(
                text = level.short,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = levelColor,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                    color = if (level == LogLevel.ERROR) Color(0xFFFCA5A5) else Color(0xFFD4D4D8),
                    maxLines = if (expanded) 20 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (error != null && expanded) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = error,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp),
                        color = Color(0xFFFCA5A5),
                    )
                }
            }
        }
    }
}

// ── small helpers ──────────────────────────────────────────────────────

/** Helper tuple for the filter pipeline. */
private data class LogTuple(
    val id: Long, val level: LogLevel, val category: String,
    val message: String, val error: String?, val timestamp: Long, val repeat: Int,
)

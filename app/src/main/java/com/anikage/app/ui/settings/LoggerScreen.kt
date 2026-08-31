package com.anikage.app.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import com.anikage.app.core.log.LogLevel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged

// Anikage palette accents for log levels.
private val ErrorColor = Color(0xFFE64158)
private val WarnColor = Color(0xFFFACC15)
private val InfoColor = Color(0xFFA855F7)
private val DebugColor = Color(0xFF94A3B8)
private val VerboseColor = Color(0xFF64748B)

private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.ERROR -> ErrorColor
    LogLevel.WARN -> WarnColor
    LogLevel.INFO -> InfoColor
    LogLevel.DEBUG -> DebugColor
    LogLevel.VERBOSE -> VerboseColor
}

/**
 * In-app diagnostics logger: live log output with levels, categories,
 * timestamps, search/filtering, clear, copy/export, error/warning
 * highlighting, device info, auto-scroll with pause, and a verbose
 * logging toggle. Reachable from Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggerScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val entries by AppLogger.entries.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val selectedLevels = remember { mutableStateOf(setOf<LogLevel>()) }
    var selectedCategory by remember { mutableStateOf<LogCategory?>(null) }
    var autoScroll by remember { mutableStateOf(true) }
    var showDeviceInfo by remember { mutableStateOf(false) }
    var programmaticScroll by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    val filtered = entries.filter { e ->
        (selectedLevels.value.isEmpty() || e.level in selectedLevels.value) &&
            (selectedCategory == null || e.category == selectedCategory) &&
            (query.isBlank() ||
                e.message.contains(query, ignoreCase = true) ||
                (e.error?.contains(query, ignoreCase = true) == true))
    }
    val latestId = entries.lastOrNull()?.id

    // Pause live scroll when the user scrolls manually.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling && !programmaticScroll) autoScroll = false
            }
    }

    // Follow the tail when live scroll is active.
    LaunchedEffect(latestId) {
        if (autoScroll && filtered.isNotEmpty()) {
            programmaticScroll = true
            try {
                listState.animateScrollToItem(filtered.size - 1)
            } finally {
                programmaticScroll = false
            }
        }
    }

    fun copyLogs() {
        clipboard.setText(AnnotatedString(AppLogger.exportText(context)))
        Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun exportLogs() {
        runCatching {
            val dir = File(context.cacheDir, "logs").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "anikage-log-$stamp.txt")
            file.writeText(AppLogger.exportText(context))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Anikage log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export logs"))
        }.onFailure {
            Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text("Logger") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { copyLogs() }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy logs")
                }
                IconButton(onClick = { exportLogs() }) {
                    Icon(Icons.Filled.Share, contentDescription = "Export logs")
                }
                IconButton(onClick = { AppLogger.clear() }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear logs")
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        // Search
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search logs") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )

        // Level filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selectedLevels.value.isEmpty(),
                onClick = { selectedLevels.value = emptySet() },
                label = { Text("All") },
            )
            LogLevel.entries.forEach { level ->
                FilterChip(
                    selected = level in selectedLevels.value,
                    onClick = {
                        selectedLevels.value =
                            if (level in selectedLevels.value) selectedLevels.value - level
                            else selectedLevels.value + level
                    },
                    label = { Text(level.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = levelColor(level).copy(alpha = 0.25f),
                        selectedLabelColor = levelColor(level),
                    ),
                )
            }
        }

        // Category filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("All areas") },
            )
            LogCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                    label = { Text(cat.label) },
                )
            }
        }

        // Status row: counts, verbose toggle, live-scroll state
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${filtered.size} / ${entries.size} entries",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (autoScroll) "Live" else "Paused",
                style = MaterialTheme.typography.bodySmall,
                color = if (autoScroll) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Verbose",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = AppLogger.verboseEnabled,
                onCheckedChange = { AppLogger.setVerbose(it, context) },
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // Device info (collapsible)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeviceInfo = !showDeviceInfo }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Device & app info",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (showDeviceInfo) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showDeviceInfo) {
                    AppLogger.deviceInfo(context).forEach { (k, v) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        ) {
                            Text(
                                text = k,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(110.dp),
                            )
                            Text(
                                text = v,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            }
        }

        // Log stream
        Box(modifier = Modifier.weight(1f)) {
            if (filtered.isEmpty()) {
                Text(
                    text = if (entries.isEmpty()) "No log entries yet."
                    else "No entries match the current filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 8.dp,
                    ),
                ) {
                    items(
                        count = filtered.size,
                        key = { i -> filtered[i].id },
                    ) { i ->
                        LogRow(entry = filtered[i], timeFormat = timeFormat)
                    }
                }
            }

            // Resume live scroll
            if (!autoScroll && filtered.isNotEmpty()) {
                Surface(
                    onClick = {
                        autoScroll = true
                    },
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Resume live scroll", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: com.anikage.app.core.log.LogEntry, timeFormat: SimpleDateFormat) {
    val highlight = when (entry.level) {
        LogLevel.ERROR -> ErrorColor.copy(alpha = 0.08f)
        LogLevel.WARN -> WarnColor.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    val color = levelColor(entry.level)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(highlight, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = entry.level.short,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = entry.category.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = if (entry.level == LogLevel.ERROR || entry.level == LogLevel.WARN) {
                color
            } else {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            },
            modifier = Modifier.padding(top = 1.dp),
        )
        entry.error?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = ErrorColor.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

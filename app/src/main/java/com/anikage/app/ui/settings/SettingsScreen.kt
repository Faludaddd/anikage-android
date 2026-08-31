package com.anikage.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anikage.app.Config

/**
 * Settings screen — mirrors Anikage's actual `localStorage.settings` schema
 * (extracted from the live site's snapshot). All values here are the
 * Anikage-default values, exposed as toggle UI. State is local (in-memory)
 * for now — wire up DataStore if you want persistence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit, onOpenLogger: () -> Unit = {}) {
    var autoplay by remember { mutableStateOf(Config.Player.AUTO_PLAY) }
    var autoskip by remember { mutableStateOf(Config.Player.AUTO_SKIP) }
    var autonext by remember { mutableStateOf(Config.Player.AUTO_NEXT) }
    var skipFillers by remember { mutableStateOf(Config.Player.SKIP_FILLERS) }
    var ambientMode by remember { mutableStateOf(Config.Player.AMBIENT_MODE) }
    var miniProgressBar by remember { mutableStateOf(Config.Player.MINI_PROGRESS_BAR) }
    var autoplayHeroTrailer by remember { mutableStateOf(Config.Player.AUTOPLAY_HERO_TRAILER) }
    var showAdultContent by remember { mutableStateOf(Config.Player.SHOW_ADULT_CONTENT) }
    var incognito by remember { mutableStateOf(Config.Player.INCOGNITO) }
    var commentsEnabled by remember { mutableStateOf(Config.Player.COMMENTS_ENABLED) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        SectionHeader("Playback")
        SettingRow("Auto-play", "Start playback automatically when opening an episode", autoplay) { autoplay = it }
        SettingRow("Auto-skip", "Auto-skip intro and outro", autoskip) { autoskip = it }
        SettingRow("Auto-next", "Play the next episode automatically", autonext) { autonext = it }
        SettingRow("Skip fillers", "Skip filler episodes", skipFillers) { skipFillers = it }
        SettingRow("Ambient mode", "Tint the player background to match the video", ambientMode) { ambientMode = it }
        SettingRow("Mini progress bar", "Show a thin progress bar at the top of the player", miniProgressBar) { miniProgressBar = it }
        SettingRow("Auto-play hero trailer", "Auto-play the trailer in the home hero banner", autoplayHeroTrailer) { autoplayHeroTrailer = it }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        SectionHeader("Content")
        SettingRow("Show adult content", "Show 18+ anime in browse / search results", showAdultContent) { showAdultContent = it }
        SettingRow("Incognito", "Don't save to local watch history", incognito) { incognito = it }
        SettingRow("Comments overlay", "Show comments over the player (Danmaku-style)", commentsEnabled) { commentsEnabled = it }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        SectionHeader("Stream")
        InfoRow("Stream type", Config.Player.STREAM_TYPE.uppercase())
        InfoRow("Stream quality", "${Config.Player.STREAM_QUALITY_ID}p (${Config.Player.STREAM_QUALITY_WIDTH}x${Config.Player.STREAM_QUALITY_HEIGHT})")
        InfoRow("Intro skip duration", "${Config.Player.INTRO_SKIP_DURATION} seconds")
        InfoRow("Anime title language", Config.Player.ANIME_TITLE_LANGUAGE)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        SectionHeader("Caption styles")
        InfoRow("Font family", Config.CaptionStyles.fontFamily)
        InfoRow("Font weight", "${Config.CaptionStyles.fontWeight}")
        InfoRow("Font size", "${Config.CaptionStyles.fontSize}%")
        InfoRow("Text color", Config.CaptionStyles.textColor)
        InfoRow("Text opacity", "${Config.CaptionStyles.textOpacity}%")

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        SectionHeader("Episode list preferences")
        InfoRow("View type", if (Config.EpisodePreferences.VIEW_TYPE == 1) "List" else "Grid")
        InfoRow("Sort order", Config.EpisodePreferences.SORT_ORDER.uppercase())

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        SectionHeader("Sync integrations")
        InfoRow("Local database", if (Config.Sync.SYNC_TO_LOCAL_DB) "On" else "Off")
        InfoRow("AniList sync", if (Config.Sync.SYNC_TO_ANILIST) "On" else "Off")
        InfoRow("MyAnimeList sync", if (Config.Sync.SYNC_TO_MAL) "On" else "Off")

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        SectionHeader("Diagnostics")
        NavigationRow(
            title = "Logger",
            subtitle = "Live in-app logs — search, filter, export. For diagnosing issues.",
            onClick = onOpenLogger,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        SectionHeader("About")
        InfoRow("App name", Config.APP_NAME)
        InfoRow("Version", "${Config.APP_VERSION} (${Config.APP_VERSION_CODE})")
        InfoRow("Data source", Config.ANILIST_API_URL)
        InfoRow("Anikage API", Config.ANIKAGE_API_BASE_URL ?: "(not configured - using AniList only)")
        InfoRow("Anikage auth", Config.ANIKAGE_AUTH_API_BASE_URL ?: "(not configured)")
        InfoRow("Stream proxy", Config.ANIKAGE_STREAM_PROXY_BASE_URL ?: "(not configured)")
        InfoRow("Default provider", Config.DEFAULT_STREAM_PROVIDER)
        InfoRow("Default lang", Config.DEFAULT_STREAM_LANG)

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun NavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
        )
    }
}

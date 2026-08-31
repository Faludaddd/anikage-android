package com.anikage.app.ui.torrents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anikage.app.ui.components.ErrorOrEmptyState

/**
 * Torrents screen — placeholder. Anikage's torrents tab lists anime torrent
 * links (to Nyaa.si etc.). This feature is disabled by default for legal
 * reasons.
 *
 * Toggle via Config.Features.ENABLE_TORRENTS_SCREEN.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentsScreen() {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Torrents") },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
        ErrorOrEmptyState(
            title = "Torrents disabled",
            subtitle = "Anikage's torrents tab links to external torrent sites. " +
                "Disabled in this app for legal reasons. Enable in Config.kt " +
                "and provide your own torrent-source endpoint if you have a " +
                "legitimate use case.",
            icon = Icons.Default.Block,
            actionText = null,
            onAction = null,
        )
    }
}

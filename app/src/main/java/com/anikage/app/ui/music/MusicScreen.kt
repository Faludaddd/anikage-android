package com.anikage.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anikage.app.Config
import com.anikage.app.ui.components.ErrorOrEmptyState

/**
 * Music screen — placeholder. Anikage's music tab lists anime OST info
 * (opening/ending themes). AniList exposes theme data via a private field
 * not in the public GraphQL schema, so this screen is a placeholder until
 * you wire up a music-metadata source in Config.kt.
 *
 * Toggle via Config.Features.ENABLE_MUSIC_SCREEN.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen() {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Music") },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
        ErrorOrEmptyState(
            title = "Music — coming soon",
            subtitle = "Anikage's music tab shows anime OST / opening theme info. " +
                "AniList's public API doesn't expose theme songs, so this screen " +
                "needs an additional metadata source. See Config.kt.",
            icon = Icons.Default.MusicNote,
            actionText = null,
            onAction = null,
        )
    }
}

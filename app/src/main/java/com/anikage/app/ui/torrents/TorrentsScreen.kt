package com.anikage.app.ui.torrents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.ErrorOrEmptyState

/**
 * Torrents screen — site header (centred "Torrents" + subtitle), feature
 * disabled: Anikage's torrents tab links to external torrent sites, so the
 * app shows the site-styled disabled state instead. Toggle via
 * Config.Features.ENABLE_TORRENTS_SCREEN.
 */
@Composable
fun TorrentsScreen() {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .padding(top = 96.dp)
    ) {
        // Site: header.mb-6.text-center — h1 "Torrents" + zinc-500 subtitle.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Torrents",
                style = WebTextStyles.titleHero,
                color = theme.fg,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "The ultimate gateway to anime torrents.",
                style = WebTextStyles.base,
                color = theme.fgMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
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

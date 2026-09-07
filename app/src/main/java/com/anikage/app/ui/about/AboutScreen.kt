package com.anikage.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anikage.app.Config
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles

/**
 * About — app identity + the site's data sources + links (DMCA / project).
 */
@Composable
fun AboutScreen(
    onOpenDmca: () -> Unit = {},
) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "About",
            style = WebTextStyles.lg,
            color = theme.fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 96.dp, bottom = 12.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = Config.APP_NAME,
                style = WebTextStyles.base,
                color = theme.fg,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Version ${Config.APP_VERSION} (${Config.APP_VERSION_CODE})",
                style = WebTextStyles.sm,
                color = theme.fgMuted,
            )

            Spacer(Modifier.height(4.dp))
            AboutSectionCard(
                icon = Icons.Filled.Info,
                title = "What is Anikage?",
                body = "Anikage is a search engine and content aggregator that indexes publicly available media from across the internet. " +
                    "This app mirrors anikage.cc — the same catalogue, the same servers, the same schedule — rebuilt natively for Android.",
            )
            AboutSectionCard(
                icon = Icons.Filled.PlayCircle,
                title = "Streaming",
                body = "Streams are resolved from Anikage's own episode servers (Koto, Kiwi, Neko, Zen and more) with sub/dub support, " +
                    "soft subtitles and multiple E-servers. Playback is handled by a native ExoPlayer pipeline.",
            )
            AboutSectionCard(
                icon = Icons.Filled.Code,
                title = "Project",
                body = Config.PROJECT_URL ?: "Source and releases are published on GitHub.",
            )

            // DMCA + Legal link (site: footer "Legal / DMCA").
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x08FFFFFF))
                    .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenDmca)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Balance, contentDescription = null, tint = theme.action, modifier = Modifier.size(16.dp))
                    Text(
                        text = "DMCA + Legal Information",
                        style = WebTextStyles.sm,
                        color = theme.fg,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = theme.fgMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AboutSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x05FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = theme.action, modifier = Modifier.size(16.dp))
            Text(
                text = title,
                style = WebTextStyles.base,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = body,
            style = WebTextStyles.sm,
            color = Color(0xFFA1A1AA),
        )
    }
}

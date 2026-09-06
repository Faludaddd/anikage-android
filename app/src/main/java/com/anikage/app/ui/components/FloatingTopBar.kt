package com.anikage.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anikage.app.Config
import com.anikage.app.R

/**
 * Anikage-style floating top bar.
 *
 * Real Anikage layout (extracted from live site):
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  [Logo Home Browse Schedule Music Torrents]   [Discord][Search][Bell][Avatar]
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * The whole bar is:
 *   - floating, fixed top-4 (16dp from top), centered horizontally
 *   - LEFT: rounded-full pill (border-white/10, bg-surface/70, backdrop-blur)
 *     containing logo + horizontal nav tabs (hidden on mobile, shown on lg+)
 *   - RIGHT: round buttons (h-11 w-11 / 44dp, rounded-full, border-white/10,
 *     bg-surface/70, backdrop-blur)
 *
 * On mobile (< 600dp width), the nav tabs are hidden inside the pill (the
 * user gets the bottom mobile nav instead — see FloatingBottomNav).
 */
@Composable
fun FloatingTopBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp.dp >= 600.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // LEFT: logo + nav tabs in a pill
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                shadowElevation = 4.dp,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Anikage logo image (the actual logo with alpha)
                    // Real Anikage: <img alt="Anikage logo" width="90" height="20" class="w-auto min-w-[60px] h-5">
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.anikage_logo),
                        contentDescription = "Anikage logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(20.dp)
                            .clickable { onNavigate("home") },
                    )

                    if (isWideScreen) {
                        Spacer(Modifier.width(6.dp))
                        // Nav tabs inside the pill
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            navItems().forEach { item ->
                                NavTabPill(
                                    label = item.label,
                                    isSelected = currentRoute == item.route,
                                    onClick = { onNavigate(item.route) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // RIGHT: round icon buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(icon = Icons.Default.Search, contentDescription = "Search",
                    onClick = { onNavigate("search") })
                RoundIconButton(icon = Icons.Default.Notifications, contentDescription = "Notifications",
                    onClick = { /* future: notifications drawer */ })
                RoundIconButton(icon = Icons.Default.Person, contentDescription = "Profile",
                    onClick = { onNavigate("settings") })
            }
        }
    }
}

@Composable
private fun NavTabPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (isSelected) Color.White.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentColor = Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private data class NavTab(val route: String, val label: String)
private fun navItems(): List<NavTab> {
    val items = mutableListOf(
        NavTab("home", "Home"),
        NavTab("browse", "Browse"),
        NavTab("schedule", "Schedule"),
    )
    if (Config.Features.ENABLE_MUSIC_SCREEN) items.add(NavTab("music", "Music"))
    if (Config.Features.ENABLE_TORRENTS_SCREEN) items.add(NavTab("torrents", "Torrents"))
    return items
}

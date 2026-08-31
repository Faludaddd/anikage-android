package com.anikage.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.anikage.app.Config

/**
 * Anikage-style floating bottom-right mobile nav.
 *
 * Real Anikage layout (mobile only — hidden on lg+ where the top pill
 * has the nav tabs):
 *
 *   ┌──────────────────────────────────────────┐
 *   │ [house][compass][calendar][music][search] │   <- floating bottom-right pill
 *   └──────────────────────────────────────────┘
 *
 * Pill:
 *   - rounded-full, border-white/10, bg-surface/70, backdrop-blur-xl
 *   - padding 1 (4dp)
 *   - Contains icon-only buttons (40dp square, rounded-full)
 *   - Active button gets a white/15 pill background
 *   - No text labels — icons only
 */
@Composable
fun FloatingBottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp.dp >= 600.dp

    // Hide on wide screens (the top pill has nav tabs there).
    if (isWideScreen) return

    Box(
        modifier = modifier
            .padding(16.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bottomNavItems().forEach { item ->
                    BottomNavIcon(
                        icon = item.icon,
                        contentDescription = item.label,
                        isSelected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavIcon(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) Color.White.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (isSelected) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private fun bottomNavItems(): List<BottomItem> {
    val items = mutableListOf(
        BottomItem("home", "Home", Icons.Default.Home),
        BottomItem("browse", "Browse", Icons.Default.Explore),
        BottomItem("schedule", "Schedule", Icons.Default.CalendarMonth),
    )
    if (Config.Features.ENABLE_MUSIC_SCREEN) {
        items.add(BottomItem("music", "Music", Icons.Default.MusicNote))
    }
    items.add(BottomItem("search", "Search", Icons.Default.Search))
    return items
}

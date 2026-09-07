package com.anikage.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.anikage.app.core.theme.LocalAnikageTheme

/**
 * MOBILE BOTTOM NAV — 1:1 port of the site's floating glass pill.
 *
 * Site DOM (mobile only, hidden ≥lg):
 *   div.fixed.bottom-0.pb-3.centered > nav.pointer-events-auto
 *     .rounded-full.border-white/10.bg-surface/70.p-1.shadow-sm.backdrop-blur-xl
 *     > 5 × a.size-10 (home/browse/music/schedule/torrents) +
 *     .pill-indicator (sliding white/15 pill with white/20 blur-2xl glow)
 *
 * Active icon text-white; inactive text-fg-muted. The indicator springs
 * horizontally to the active tab (site's pill-indicator translateX).
 */
@Composable
fun FloatingBottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    // Hidden on wide screens — the top pill carries the nav links there.
    if (configuration.screenWidthDp.dp >= 840.dp) return

    val theme = LocalAnikageTheme.current
    val items = bottomNavItems()
    val activeIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val itemSize = 40.dp        // site: size-10
    val gap = 4.dp              // site: gap-1

    // Sliding indicator (site: pill-indicator transform translateX).
    val indicatorOffset by animateDpAsState(
        targetValue = (itemSize + gap) * activeIndex,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navIndicator",
    )

    Box(
        modifier = modifier.padding(bottom = 12.dp),   // site: pb-3
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(50), spotColor = Color(0x40000000))
                .clip(RoundedCornerShape(50))
                .background(theme.surface.copy(alpha = 0.70f))
                .border(BorderStroke(1.dp, Color(0x1AFFFFFF)), RoundedCornerShape(50))
                .padding(4.dp),                        // site: p-1
        ) {
            Box {
                // Sliding pill indicator (site: bg-white/15 + glow blur-2xl).
                if (activeIndex < items.size) {
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .size(itemSize)
                            .background(Color(0x26FFFFFF), CircleShape)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEach { item ->
                        val selected = item.route == currentRoute
                        Box(
                            modifier = Modifier
                                .size(itemSize)
                                .clip(CircleShape)
                                .clickable { onNavigate(item.route) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = if (selected) Color.White else theme.fgMuted,
                                modifier = Modifier.size(18.dp),  // site: size-4.5
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

/**
 * v2.2.0 (directive #10/#13): Torrents removed; Subscriptions is the fifth
 * tab — a bell with a check badge (NotificationsActive) reads clearly as
 * "subscribe / track releases" without looking like a generic alarm.
 */
private fun bottomNavItems(): List<BottomItem> = listOf(
    BottomItem("home", "Home", Icons.Default.Home),
    BottomItem("browse", "Browse", Icons.Default.GridView),
    BottomItem("music", "Music", Icons.Default.MusicNote),
    BottomItem("schedule", "Schedule", Icons.Default.CalendarMonth),
    BottomItem("subscriptions", "Subscriptions", Icons.Default.NotificationsActive),
)

package com.anikage.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anikage.app.R
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles

/**
 * TOP BAR — 1:1 port of the site's floating nav.
 *
 * Site DOM (nav.container-custom.fixed.top-4.z-30):
 *   LEFT  pill: rounded-full border-white/10 bg-surface/90 p-1 shadow-sm,
 *          logo img (h-5, mx-3 my-1.5) + nav links (hidden < lg:
 *          Home/Browse/Schedule/Music/Torrents, px-5 py-2 text-base medium)
 *   RIGHT icons: h-11 w-11 rounded-full border-white/10 bg-surface/90
 *          (Search, Notifications) — Discord button is md+ only.
 */
@Composable
fun FloatingTopBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onSearchClick: () -> Unit = { onNavigate("search") },
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp.dp >= 840.dp   // site lg
    val theme = LocalAnikageTheme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),   // site: top-4 + container-custom
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // LEFT: logo (+ nav tabs on wide screens) inside a pill.
            Box(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(50), spotColor = Color(0x33000000))
                    .clip(RoundedCornerShape(50))
                    .background(theme.surface.copy(alpha = 0.90f))
                    .border(BorderStroke(1.dp, Color(0x1AFFFFFF)), RoundedCornerShape(50))
                    .padding(4.dp),                                   // site: p-1
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Logo — site: h-5 (20px) w-auto min-w-[60px], mx-3 my-1.5.
                    Image(
                        painter = painterResource(id = R.drawable.anikage_logo),
                        contentDescription = "Anikage logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .height(20.dp)
                            .clickable { onNavigate("home") },
                    )
                    if (isWideScreen) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp),
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

            // RIGHT: round icon buttons (site: h-11 w-11, border-white/10).
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Search",
                    onClick = onSearchClick,
                )
                RoundIconButton(
                    icon = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    onClick = { onNavigate("settings") },
                )
            }
        }
    }
}

/** site nav link: rounded-full px-5 py-2 text-base font-medium. */
@Composable
private fun NavTabPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) Color(0x26FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = WebTextStyles.base,
            color = if (isSelected) Color.White else LocalAnikageTheme.current.fgMuted,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** site: h-11 w-11 rounded-full border-white/10 bg-surface/90 shadow-sm. */
@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(4.dp, CircleShape, spotColor = Color(0x33000000))
            .clip(CircleShape)
            .background(theme.surface.copy(alpha = 0.90f))
            .border(BorderStroke(1.dp, Color(0x1AFFFFFF)), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp),   // site: h-5.5 stroke-2.5
        )
    }
}

private data class NavTab(val route: String, val label: String)

/** Site link order: Home, Browse, Schedule, Music, Torrents. */
private fun navItems(): List<NavTab> = listOf(
    NavTab("home", "Home"),
    NavTab("browse", "Browse"),
    NavTab("schedule", "Schedule"),
    NavTab("music", "Music"),
    NavTab("torrents", "Torrents"),
)

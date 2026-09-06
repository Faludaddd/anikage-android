package com.anikage.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.anikage.app.R
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles

/**
 * TOP BAR — 1:1 port of the site's floating nav (present on every content
 * page: home, browse, schedule, music, torrents, info, watch — exactly like
 * the site's fixed root nav; account pages use their own back headers).
 *
 * Site DOM (nav.container-custom.fixed.top-4.z-30):
 *   LEFT  pill: rounded-full border-white/10 bg-surface/90 p-1 shadow-sm
 *          (md: bg-surface/70 + backdrop-blur-xl),
 *          logo img (h-5, mx-3 my-1.5, lg:mx-5) + nav links (lg only:
 *          Home/Browse/Schedule/Music/Torrents) — compact, professional
 *          sizing like the site's text-base links.
 *   RIGHT icons (gap-1.5 md:gap-2.5):
 *          Search  (h-11 w-11, md: h-12 w-12, lucide-search),
 *          Bell    (h-11 w-11, md: h-12 w-12),
 *          Profile (avatar h-11 w-11 md:h-12 w-12; opens a dropdown menu —
 *                   the site's button is aria-haspopup — never a direct
 *                   auth page).
 */
@Composable
fun FloatingTopBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onSearchClick: () -> Unit = { onNavigate("search") },
    onNotificationsClick: () -> Unit = { onNavigate("notifications") },
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
                    .padding(3.dp),                                  // compact: p-0.75
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Logo — site: h-5 (20px) w-auto, mx-3 my-1.5.
                    Image(
                        painter = painterResource(id = R.drawable.anikage_logo),
                        contentDescription = "Anikage logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .height(20.dp)
                            .clickable { onNavigate("home") },
                    )
                    if (isWideScreen) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 6.dp),
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

            // RIGHT: round icon buttons (site order: Search, Bell, Avatar).
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
                    onClick = onNotificationsClick,
                )
                // Profile avatar — opens a dropdown menu (site: aria-haspopup
                // button), NOT a navigation to an auth page.
                ProfileAvatarMenu(
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

/** site nav link: rounded-full px-4 py-1.5 text-sm font-medium — compact. */
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
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            // Fixed compact size (NOT the tablet-scaled type) so the nav pill
            // stays tight like the site's text-base links on large screens.
            fontSize = 14.sp,
            color = if (isSelected) Color.White else LocalAnikageTheme.current.fgMuted,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** site: h-11 w-11 (md: h-12 w-12) rounded-full border-white/10 bg-surface/90 shadow-sm. */
@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    val configuration = LocalConfiguration.current
    val size = if (configuration.screenWidthDp.dp >= 600.dp) 48.dp else 44.dp
    Box(
        modifier = Modifier
            .size(size)
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

// ---------------------------------------------------------------------------
//  Profile dropdown — the site's avatar is an aria-haspopup button. While
//  logged out it opens account options; the app mirrors that popover with
//  its available profile/account destinations.
// ---------------------------------------------------------------------------

private data class ProfileMenuItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * Profile avatar + dropdown menu (site: default avatar image with a subtle
 * pulse while logged out). Tapping opens the account popover instead of
 * navigating anywhere directly.
 */
@Composable
private fun ProfileAvatarMenu(onNavigate: (String) -> Unit) {
    val configuration = LocalConfiguration.current
    val size = if (configuration.screenWidthDp.dp >= 600.dp) 48.dp else 44.dp
    val theme = LocalAnikageTheme.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(4.dp, CircleShape, spotColor = Color(0x33000000))
                .clip(CircleShape)
                .background(theme.surface.copy(alpha = 0.90f))
                .border(BorderStroke(1.dp, Color(0x1AFFFFFF)), CircleShape)
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            // Site's exact default avatar asset (logged-out).
            Image(
                painter = painterResource(id = R.drawable.nav_default_avatar),
                contentDescription = "Profile menu",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, (size.value + 12.dp.value).toInt()),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                ProfileDropdown(
                    onDismiss = { expanded = false },
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

/** The account popover: guest header + profile/account options. */
@Composable
private fun ProfileDropdown(
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val theme = LocalAnikageTheme.current
    val items = listOf(
        ProfileMenuItem("Settings", Icons.Outlined.Settings, "settings"),
        ProfileMenuItem("Notifications", Icons.Outlined.Notifications, "notifications"),
        ProfileMenuItem("Diagnostics", Icons.Outlined.BugReport, "diagnostics"),
        ProfileMenuItem("About", Icons.Outlined.Info, "about"),
    )

    Column(
        modifier = Modifier
            .width(240.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = Color(0x80000000))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF20A0A0A))                        // surface/95 + blur
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp)),
    ) {
        // Guest header — avatar + status.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x14FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = theme.fgMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column {
                Text(
                    text = "Guest",
                    style = WebTextStyles.sm,
                    color = theme.fg,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Not signed in",
                    style = WebTextStyles.xs,
                    color = theme.fgMuted,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .background(Color(0x0FFFFFFF)),
        )
        // Menu items.
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onNavigate(item.route)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = theme.fgMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = item.label,
                        style = WebTextStyles.sm,
                        color = theme.fg,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
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

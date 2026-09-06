package com.anikage.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
 *          Home/Browse/Schedule/Music/Torrents, px-5 py-2 text-base medium)
 *   RIGHT icons (gap-1.5 md:gap-2.5):
 *          Discord (md+ only, h-12 w-12, external link),
 *          Search  (h-11 w-11, md: h-12 w-12, lucide-search),
 *          Bell    (h-11 w-11, md: h-12 w-12),
 *          Profile (avatar h-11 w-11 md:h-12 w-12; logged-out default avatar
 *                   has animate-pulse opacity-70 — reproduced here).
 */
@Composable
fun FloatingTopBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onSearchClick: () -> Unit = { onNavigate("search") },
    onNotificationsClick: () -> Unit = { onNavigate("notifications") },
    onProfileClick: () -> Unit = { onNavigate("profile") },
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp.dp >= 840.dp   // site lg
    val isMd = configuration.screenWidthDp.dp >= 600.dp           // site md (Discord shows)
    val theme = LocalAnikageTheme.current
    val context = LocalContext.current

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

            // RIGHT: round icon buttons (site order: Discord, Search, Bell, Avatar).
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Discord — site: md+ only, external invite link.
                if (isMd) {
                    RoundIconButton(
                        icon = null,
                        discord = true,
                        contentDescription = "Discord",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/qmrbA5WDdV")),
                                )
                            }
                        },
                    )
                }
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
                // Profile avatar — site: default avatar image, animate-pulse
                // opacity-70 while logged out; opens the profile area.
                ProfileAvatarButton(onClick = onProfileClick)
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

/** site: h-11 w-11 (md: h-12 w-12) rounded-full border-white/10 bg-surface/90 shadow-sm. */
@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    contentDescription: String,
    onClick: () -> Unit,
    discord: Boolean = false,
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
        if (discord) {
            // Site's Discord glyph — drawn as a simple game-controller mark.
            Text(
                text = "D",
                color = Color(0xFF8B5CF6),
                fontWeight = FontWeight.Black,
                style = WebTextStyles.lg,
            )
        } else {
            icon?.let {
                Icon(
                    it,
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),   // site: h-5.5 stroke-2.5
                )
            }
        }
    }
}

/**
 * Profile avatar — site: h-11 w-11 (md: 12) grid rounded-full with the
 * default avatar image; logged-out state pulses at opacity 70.
 */
@Composable
private fun ProfileAvatarButton(onClick: () -> Unit) {
    val configuration = LocalConfiguration.current
    val size = if (configuration.screenWidthDp.dp >= 600.dp) 48.dp else 44.dp
    val transition = rememberInfiniteTransition(label = "avatar-pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "avatar-alpha",
    )
    Box(
        modifier = Modifier
            .padding(start = 2.dp)                       // site: ml-0.5
            .size(size)
            .shadow(4.dp, CircleShape, spotColor = Color(0x33000000))
            .clip(CircleShape)
            .background(LocalAnikageTheme.current.surface.copy(alpha = 0.90f))
            .border(BorderStroke(1.dp, Color(0x1AFFFFFF)), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Site's exact default avatar asset (logged-out) with its pulse.
        Image(
            painter = painterResource(id = R.drawable.nav_default_avatar),
            contentDescription = "Profile",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .graphicsLayer { this.alpha = alpha },
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

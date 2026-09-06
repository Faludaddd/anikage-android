package com.anikage.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anikage.app.Config
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.ThemeState
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.Config.Theme.WebTheme

/**
 * SETTINGS — 1:1 port of anikage.cc/settings (mobile).
 *
 * Site: page title + mobile tab pills (Account / General / Player / Themes /
 * About) + `settings-card` rows (rounded-2xl border-white/6 bg-white/3 p-6:
 * title-subsec + text-sm zinc-500 description + toggle or button).
 * The Themes tab is the site's theme picker: a grid of 11 preview cards
 * (aspect-16/10) that switches the whole app live.
 */
@Composable
fun SettingsScreen(onBackClick: () -> Unit, onOpenLogger: () -> Unit = {}) {
    val theme = LocalAnikageTheme.current
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(1) }   // General default
    val tabs = listOf("Account", "General", "Player", "Themes", "About")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Page header ────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp, start = 8.dp, end = 16.dp),
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.fg)
            }
            Text(
                text = "Settings",
                style = WebTextStyles.titleHero,
                color = theme.fg,
            )
        }

        // ── Tab pills (site: rounded-xl segmented control) ─────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x08FFFFFF))
                .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { idx, label ->
                val selected = idx == selectedTab
                Text(
                    text = label,
                    style = WebTextStyles.xs,
                    color = if (selected) theme.actionFg else theme.fgMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) theme.action else Color.Transparent)
                        .clickable { selectedTab = idx }
                        .padding(vertical = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        when (selectedTab) {
            0 -> AccountTab()
            1 -> GeneralTab(onOpenLogger)
            2 -> PlayerTab()
            3 -> ThemesTab()
            else -> AboutTab()
        }

        Spacer(Modifier.height(96.dp))
    }
}

// ---------------------------------------------------------------------------
//  Account — site: Login Now + Incognito + External Trackers
// ---------------------------------------------------------------------------

@Composable
private fun AccountTab() {
    val theme = LocalAnikageTheme.current
    SettingsSection(title = "Account") {
        SettingsCard(title = "Sync to the cloud", description = "Share your watch progress between devices and keep them synced.") {
            SitePrimaryButton(label = "Login Now") { /* auth flow */ }
        }
        SettingsCard(title = "Incognito Mode", description = "Prevent saving your watch history and adding anime to your lists. Tracker syncing is also disabled.") {
            SiteSwitch(checked = Config.Player.INCOGNITO) { }
        }
        SettingsSectionHeader(title = "External Trackers", subtitle = "Sync your watch progress with AniList or MyAnimeList")
        SettingsCard(title = "AniList", description = "Sync your media list and tracking progress", iconLabel = "AL") {
            SitePrimaryButton(label = "Connect AniList") { }
        }
        SettingsCard(title = "MyAnimeList", description = "Sync your media list and tracking progress", iconLabel = "MAL") {
            SitePrimaryButton(label = "Connect MAL") { }
        }
    }
}

// ---------------------------------------------------------------------------
//  General — site general settings + diagnostics (logger)
// ---------------------------------------------------------------------------

@Composable
private fun GeneralTab(onOpenLogger: () -> Unit) {
    var showAdult by remember { mutableStateOf(Config.Player.SHOW_ADULT_CONTENT) }
    SettingsSection(title = "Content") {
        SettingsCard(title = "Show adult content", description = "Show 18+ anime in browse and search results.") {
            SiteSwitch(checked = showAdult) { showAdult = it }
        }
    }
    SettingsSection(title = "Diagnostics") {
        SettingsCard(
            title = "Logger",
            description = "Live in-app logs — search, filter, export. For diagnosing issues.",
            onClick = onOpenLogger,
        )
    }
}

// ---------------------------------------------------------------------------
//  Player — site player settings
// ---------------------------------------------------------------------------

@Composable
private fun PlayerTab() {
    var autoplay by remember { mutableStateOf(Config.Player.AUTO_PLAY) }
    var autoskip by remember { mutableStateOf(Config.Player.AUTO_SKIP) }
    var autonext by remember { mutableStateOf(Config.Player.AUTO_NEXT) }
    var ambientMode by remember { mutableStateOf(Config.Player.AMBIENT_MODE) }
    var commentsEnabled by remember { mutableStateOf(Config.Player.COMMENTS_ENABLED) }

    SettingsSection(title = "Player") {
        SettingsCard(title = "Auto-play", description = "Start playback automatically when opening an episode.") {
            SiteSwitch(checked = autoplay) { autoplay = it }
        }
        SettingsCard(title = "Auto-skip", description = "Auto-skip intro and outro segments.") {
            SiteSwitch(checked = autoskip) { autoskip = it }
        }
        SettingsCard(title = "Auto-next", description = "Play the next episode automatically.") {
            SiteSwitch(checked = autonext) { autonext = it }
        }
        SettingsCard(title = "Ambient mode", description = "Tint the player background to match the video.") {
            SiteSwitch(checked = ambientMode) { ambientMode = it }
        }
        SettingsCard(title = "Comments", description = "Show the episode comment section on the watch page.") {
            SiteSwitch(checked = commentsEnabled) { commentsEnabled = it }
        }
    }
}

// ---------------------------------------------------------------------------
//  Themes — the site's 11-theme picker, live switching
// ---------------------------------------------------------------------------

@Composable
private fun ThemesTab() {
    val context = LocalContext.current
    val activeKey = ThemeState.key

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SettingsSectionHeader(title = "Appearance", subtitle = "Pick the accent theme — matches the website's theme picker.")
        Spacer(Modifier.height(12.dp))
        // Site: grid of theme-card previews (aspect-16/10).
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Config.Theme.ALL.forEach { webTheme ->
                ThemeCard(
                    webTheme = webTheme,
                    selected = webTheme.key == activeKey,
                    onClick = { ThemeState.set(context, webTheme.key) },
                )
            }
        }
    }
}

/** One site theme preview: mini card + name + check when active. */
@Composable
private fun ThemeCard(
    webTheme: WebTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x08FFFFFF))
            .border(
                1.dp,
                if (selected) webTheme.action else Color(0x0FFFFFFF),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        // Mini preview: surface bg + card block + action dot.
        Box(
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(10.dp))
                .background(webTheme.surface)
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp)),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(8.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(webTheme.action)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(webTheme.surfaceCard)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = webTheme.label,
            style = WebTextStyles.base,
            color = LocalAnikageTheme.current.fg,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(webTheme.action),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = webTheme.actionFg,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  About
// ---------------------------------------------------------------------------

@Composable
private fun AboutTab() {
    val theme = LocalAnikageTheme.current
    SettingsSection(title = "About") {
        SettingsCard(title = "App name", description = Config.APP_NAME) {}
        SettingsCard(title = "Version", description = "${Config.APP_VERSION} (${Config.APP_VERSION_CODE})") {}
        SettingsCard(title = "Data source", description = Config.ANIKAGE_API_BASE_URL ?: "AniList") {}
        SettingsCard(title = "About", description = Config.ABOUT_TEXT) {}
    }
}

// ---------------------------------------------------------------------------
//  Site-style settings scaffolding
// ---------------------------------------------------------------------------

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            style = WebTextStyles.titleSection,
            color = theme().fg,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, subtitle: String) {
    val t = LocalAnikageTheme.current
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = WebTextStyles.titleSection,
            color = t.fg,
        )
        Text(
            text = subtitle,
            style = WebTextStyles.sm,
            color = Color(0xFF71717A),
        )
    }
}

/** site: .settings-card — rounded-2xl border-white/6 bg-white/3 p-6. */
@Composable
private fun SettingsCard(
    title: String,
    description: String,
    iconLabel: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = LocalAnikageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconLabel != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x14FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = iconLabel,
                    style = WebTextStyles.xs,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = WebTextStyles.base,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = WebTextStyles.sm,
                color = Color(0xFF71717A),
                lineHeight = 18.sp,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        } else if (onClick != null) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = theme.fgMuted,
            )
        }
    }
}

/** site: .btn.btn-md.btn-primary — action-coloured pill. */
@Composable
private fun SitePrimaryButton(label: String, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    Text(
        text = label,
        style = WebTextStyles.sm,
        color = theme.actionFg,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(theme.action)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/** site toggle — h-7 w-12 track, h-6 w-6 knob. */
@Composable
private fun SiteSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val theme = LocalAnikageTheme.current
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) theme.action else Color(0x1AFFFFFF))
            .clickable { onChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) theme.actionFg else Color(0xFF737373)),
        )
    }
}

@Composable
private fun theme(): WebTheme = LocalAnikageTheme.current

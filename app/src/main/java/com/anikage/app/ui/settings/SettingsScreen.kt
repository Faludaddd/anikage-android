package com.anikage.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anikage.app.Config
import com.anikage.app.core.auth.AuthManager
import com.anikage.app.core.settings.SettingsState
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.ThemeState
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.SiteSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SETTINGS — v2.2.0 RESTORES THE ORIGINAL SETTINGS SYSTEM (user directive
 * #9): the same section organization the app and the site used before the
 * v2.1.0 hero-card hub — Account / General / Player / Themes / About.
 *
 * Layout (site: /(settings) page):
 *   wide  — fixed left sidebar with the section nav
 *   phone — section dropdown (the site's collapsed select) + page content
 *
 * Every individual page is polished and REAL: each row writes to
 * [SettingsState] and is read by the live player engine/watch screens.
 * ALL v2.1.0 settings are preserved (none removed) and folded into their
 * natural sections; v2.2.0 adds Live Captions, download automation and
 * subscription notification controls.
 */
private data class SectionDef(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

private val Sections = listOf(
    SectionDef("account", "Account", Icons.Default.Person),
    SectionDef("general", "General", Icons.Default.Settings),
    SectionDef("player", "Player", Icons.Default.PlayArrow),
    SectionDef("themes", "Themes", Icons.Default.Palette),
    SectionDef("about", "About", Icons.Default.Terminal),
)

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenDmca: () -> Unit = {},
) {
    val theme = LocalAnikageTheme.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp.dp >= 840.dp

    var section by remember { mutableStateOf("general") }
    var showLogin by remember { mutableStateOf(false) }

    BackHandler(enabled = showLogin) { showLogin = false }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .statusBarsPadding(),
    ) {
        // ── Sidebar (wide) — site: fixed w-72 nav card. ──────────────────
        if (isWide) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .padding(start = 24.dp, top = 72.dp)
            ) {
                Text("Settings", style = WebTextStyles.titleHero, color = theme.fg)
                Text(
                    "Tune your Anikage experience",
                    style = WebTextStyles.sm,
                    color = theme.fgMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
                )
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Sections.forEach { s ->
                        SectionButton(
                            label = s.label,
                            icon = s.icon,
                            active = section == s.id,
                            onClick = { section = s.id },
                        )
                    }
                }
            }
        }

        // ── Content column. ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        ) {
            // Header (phone: section selector like the site's collapsed nav).
            Column(Modifier.fillMaxWidth().padding(top = 64.dp, start = 16.dp, end = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    run {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x08FFFFFF))
                                .border(1.dp, Color(0x0FFFFFFF), CircleShape)
                                .clickable(onClick = onBackClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = theme.fg,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (isWide) {
                        Text(
                            text = Sections.first { it.id == section }.label,
                            style = WebTextStyles.titleHero,
                            color = theme.fg,
                        )
                    } else {
                        // Phone: the site's collapsed section selector.
                        val expanded = remember { mutableStateOf(false) }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x08FFFFFF))
                                    .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(14.dp))
                                    .clickable { expanded.value = !expanded.value }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                            ) {
                                Icon(
                                    Sections.first { it.id == section }.icon,
                                    contentDescription = null,
                                    tint = theme.action,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = Sections.first { it.id == section }.label,
                                    style = WebTextStyles.base,
                                    color = theme.fg,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Change section",
                                    tint = theme.fgMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            if (expanded.value) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xF20A0A0A))
                                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
                                        .padding(8.dp),
                                ) {
                                    Sections.forEach { s ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (section == s.id) Color(0x14FFFFFF) else Color.Transparent)
                                                .clickable {
                                                    section = s.id
                                                    expanded.value = false
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                        ) {
                                            Icon(
                                                s.icon, null,
                                                tint = if (section == s.id) theme.action else theme.fgMuted,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = s.label,
                                                style = WebTextStyles.sm,
                                                color = if (section == s.id) theme.fg else theme.fgMuted,
                                                fontWeight = if (section == s.id) FontWeight.SemiBold else FontWeight.Medium,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── The section pages (site: settings-card list). ─────────────
            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 4 } + fadeOut())
                },
                label = "settings-section",
                modifier = Modifier.fillMaxSize(),
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 120.dp),
                ) {
                    when (current) {
                        "account" -> AccountSection(
                            onNeedLogin = { showLogin = true },
                        )
                        "general" -> GeneralSection()
                        "player" -> PlayerSection()
                        "themes" -> ThemesPage()
                        "about" -> AboutSection(
                            onOpenDiagnostics = onOpenDiagnostics,
                            onOpenDmca = onOpenDmca,
                        )
                    }
                }
            }
        }
    }

    if (showLogin) {
        com.anikage.app.ui.player.LoginSheet(
            onDismiss = { showLogin = false },
            onSignedIn = { /* state updates via AuthManager observers */ },
        )
    }
}

// ---------------------------------------------------------------------------
//  Sidebar button (wide layout — the old app's exact pattern)
// ---------------------------------------------------------------------------

@Composable
private fun SectionButton(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Color(0x14FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) theme.action else theme.fgMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = WebTextStyles.sm,
            color = if (active) theme.fg else theme.fgMuted,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
//  Primitives (kept 1:1 from the previous settings — site-exact cards)
// ---------------------------------------------------------------------------

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        content()
    }
}

/** Row: h3 title-subsec + p text-sm zinc-500, toggle right (md:row). */
@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val theme = LocalAnikageTheme.current
    SettingsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = WebTextStyles.base,
                    color = theme.fg,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    text = description,
                    style = WebTextStyles.sm,
                    color = Color(0xFF71717A),
                    lineHeight = 20.sp,
                )
            }
            SiteSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** Select row: card with title/desc + value pills (site SingleSelect). */
@Composable
private fun SelectRow(
    title: String,
    description: String,
    options: List<Pair<String, String>>,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val theme = LocalAnikageTheme.current
    SettingsCard {
        Text(
            text = title,
            style = WebTextStyles.base,
            color = theme.fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = description,
            style = WebTextStyles.sm,
            color = Color(0xFF71717A),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { (key, label) ->
                val active = value == key
                Text(
                    text = label,
                    style = WebTextStyles.sm,
                    color = if (active) theme.actionFg else Color(0xFFA1A1AA),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) theme.action else Color(0x0DFFFFFF))
                        .border(1.dp, if (active) theme.action else Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                        .clickable { onValueChange(key) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Slider row with live value label. */
@Composable
private fun SliderRow(
    title: String,
    description: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
) {
    val theme = LocalAnikageTheme.current
    SettingsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            Text(
                text = title,
                style = WebTextStyles.base,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = valueLabel,
                style = WebTextStyles.sm,
                color = Color(0xFFA1A1AA),
            )
        }
        Text(
            text = description,
            style = WebTextStyles.sm,
            color = Color(0xFF71717A),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = theme.action,
                activeTrackColor = theme.action,
                inactiveTrackColor = Color(0x14FFFFFF),
            ),
        )
    }
}

/** Site section heading inside content. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = WebTextStyles.titleSection,
        color = LocalAnikageTheme.current.fg,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

// ---------------------------------------------------------------------------
//  ACCOUNT — real anikage.cc session + privacy (directive #8/#18)
// ---------------------------------------------------------------------------

@Composable
private fun AccountSection(onNeedLogin: () -> Unit) {
    val context = LocalContext.current
    val theme = LocalAnikageTheme.current
    val user = AuthManager.user

    SectionTitle("Account")

    // The REAL session card: signed-in profile or sign-in action.
    SettingsCard {
        if (user != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val avatar = user.avatarUrl()
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(theme.action),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatar != null) {
                        coil.compose.AsyncImage(
                            model = avatar,
                            contentDescription = user.displayLabel,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = user.displayLabel.firstOrNull()?.uppercase() ?: "?",
                            style = WebTextStyles.titleSection,
                            color = theme.actionFg,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = user.displayLabel,
                        style = WebTextStyles.base,
                        color = theme.fg,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = user.email ?: user.username ?: "anikage.cc account",
                        style = WebTextStyles.xs,
                        color = Color(0xFF71717A),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = "Signed in — comments post as ${user.username ?: user.displayLabel}",
                        style = WebTextStyles.xs,
                        color = Color(0xFF6EE7B7),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = "Sign out",
                    tint = Color(0xFFFCA5A5),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { AuthManager.signOut(context) }
                        .padding(7.dp),
                )
            }
        } else {
            Column {
                Text(
                    text = "Sign in to Anikage",
                    style = WebTextStyles.base,
                    color = theme.fg,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    text = "Use your anikage.cc account to post comments. Signing in " +
                        "happens right here — no external browser needed. Sign-up (which " +
                        "needs a captcha) happens on anikage.cc.",
                    style = WebTextStyles.sm,
                    color = Color(0xFF71717A),
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Text(
                    text = "Sign in",
                    style = WebTextStyles.sm,
                    color = theme.actionFg,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.action)
                        .clickable(onClick = onNeedLogin)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }

    SectionTitle("Privacy")

    ToggleRow(
        title = "Incognito mode",
        description = "Watch history, progress, list changes and view counting are paused while on.",
        checked = SettingsState.incognito,
        onCheckedChange = { SettingsState.setIncognito(context, it) },
    )
    ToggleRow(
        title = "Show adult content",
        description = "Display 18+ anime in browse and search results.",
        checked = SettingsState.showAdultContent,
        onCheckedChange = { SettingsState.setShowAdultContent(context, it) },
    )
    ToggleRow(
        title = "Comments",
        description = "Show the comment section on watch pages.",
        checked = SettingsState.commentsEnabled,
        onCheckedChange = { SettingsState.setCommentsEnabled(context, it) },
    )

    SectionTitle("Data")

    SettingsCard {
        Text(
            text = "Watch history & progress",
            style = WebTextStyles.base,
            color = theme.fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = "Progress and history are stored on this device only. Clearing them resets resume positions.",
            style = WebTextStyles.sm,
            color = Color(0xFF71717A),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        val scope = rememberCoroutineScope()
        var cleared by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (cleared) "Cleared" else "Clear watch history",
                style = WebTextStyles.sm,
                color = if (cleared) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (cleared) Color(0x1A22C55E) else Color(0x14EF4444))
                    .clickable(enabled = !cleared) {
                        val repo = com.anikage.app.core.data.AnikageRepository.get(context)
                        scope.launch { repo.clearWatchData() }
                        cleared = true
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

/** Storage usage text for the downloads card (computed off the main thread). */
@Composable
internal fun rememberDownloadStorageText(): String {
    val context = LocalContext.current
    var text by remember { mutableStateOf("…") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = com.anikage.app.core.download.EpisodeDownloadEngine.downloadsDir(context)
            val bytes = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            val free = dir.usableSpace
            "Downloads: ${formatStorage(bytes)} used · ${formatStorage(free)} free on this device"
        }
    }
    return text
}

private fun formatStorage(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1e6)
    bytes >= 1_000 -> "%d KB".format(bytes / 1_000)
    else -> "$bytes B"
}

// ---------------------------------------------------------------------------
//  GENERAL — content, subscriptions, downloads, appearance basics
// ---------------------------------------------------------------------------

@Composable
private fun GeneralSection() {
    val context = LocalContext.current
    val theme = LocalAnikageTheme.current

    SectionTitle("Content")

    SelectRow(
        title = "Title language",
        description = "Preferred language for anime titles across the app.",
        options = listOf(
            "english" to "English",
            "romaji" to "Romaji",
            "native" to "Japanese",
        ),
        value = SettingsState.titleLanguage,
        onValueChange = { SettingsState.setTitleLanguage(context, it) },
    )
    ToggleRow(
        title = "Hero trailer",
        description = "Autoplay the spotlight trailer on the home page hero.",
        checked = SettingsState.autoplayHeroTrailer,
        onCheckedChange = { SettingsState.setAutoplayHeroTrailer(context, it) },
    )

    SectionTitle("Subscriptions")

    ToggleRow(
        title = "New-episode notifications",
        description = "Notify me when a subscribed anime releases a new episode. Checks happen in the background.",
        checked = SettingsState.subscriptionNotifications,
        onCheckedChange = { SettingsState.setSubscriptionNotifications(context, it) },
    )

    SectionTitle("Downloads")

    SelectRow(
        title = "Download quality",
        description = "Default rendition for in-app downloads (the dialog can still override per download).",
        options = listOf(
            "0" to "Auto",
            "480" to "480p",
            "720" to "720p",
            "1080" to "1080p",
        ),
        value = SettingsState.downloadQualityHeight.toString(),
        onValueChange = { SettingsState.setDownloadQualityHeight(context, it.toInt()) },
    )
    ToggleRow(
        title = "Wi-Fi only",
        description = "Only download on unmetered connections (mobile data pauses the queue).",
        checked = SettingsState.downloadsWifiOnly,
        onCheckedChange = { SettingsState.setDownloadsWifiOnly(context, it) },
    )
    ToggleRow(
        title = "Auto-download next episode",
        description = "After finishing a downloaded episode, queue the next one automatically.",
        checked = SettingsState.autoDownloadNextEpisode,
        onCheckedChange = { SettingsState.setAutoDownloadNextEpisode(context, it) },
    )

    // Storage information (real, computed from the downloads directory).
    val storage = rememberDownloadStorageText()
    SettingsCard {
        Text(
            text = "Storage",
            style = WebTextStyles.base,
            color = theme.fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = storage,
            style = WebTextStyles.sm,
            color = Color(0xFF71717A),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
            text = "Manage files in the Downloads screen",
            style = WebTextStyles.xs,
            color = theme.action,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x14FFFFFF))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }

    SectionTitle("Diagnostics")

    ToggleRow(
        title = "Verbose logging",
        description = "Record debug-level entries in the diagnostics log. Useful when reporting issues.",
        checked = SettingsState.verboseLogging,
        onCheckedChange = { SettingsState.setVerboseLogging(context, it) },
    )
}

// ---------------------------------------------------------------------------
//  PLAYER — every playback knob, all live (v2.1.0 rows preserved)
// ---------------------------------------------------------------------------

@Composable
private fun PlayerSection() {
    val context = LocalContext.current
    val theme = LocalAnikageTheme.current

    SectionTitle("Seeking")

    var seekAmount by remember { mutableStateOf(SettingsState.seekAmountSec) }
    SliderRow(
        title = "Seek amount",
        description = "Applied to the player's ± buttons, double-tap zones and keyboard shortcuts.",
        valueLabel = "${seekAmount}s",
        value = seekAmount.toFloat(),
        range = 5f..60f,
        onValueChange = {
            seekAmount = it.toInt()
            SettingsState.setSeekAmountSec(context, seekAmount)
        },
    )
    SettingsCard {
        Text(
            text = "Quick picks",
            style = WebTextStyles.sm,
            color = theme.fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30, 45, 60).forEach { s ->
                val active = seekAmount == s
                Text(
                    text = "${s}s",
                    style = WebTextStyles.sm,
                    color = if (active) theme.actionFg else Color(0xFFA1A1AA),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) theme.action else Color(0x0DFFFFFF))
                        .clickable {
                            seekAmount = s
                            SettingsState.setSeekAmountSec(context, s)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }

    SectionTitle("Gestures")

    ToggleRow(
        title = "Gesture controls",
        description = "Master switch for double-tap seeking, press-and-hold speed and edge swipes.",
        checked = SettingsState.gestureControls,
        onCheckedChange = { SettingsState.setGestureControls(context, it) },
    )
    ToggleRow(
        title = "Double-tap seeking",
        description = "Tap twice on the left or right edge to jump ${SettingsState.seekAmountSec}s.",
        checked = SettingsState.doubleTapSeek,
        onCheckedChange = { SettingsState.setDoubleTapSeek(context, it) },
    )
    ToggleRow(
        title = "Hold-to-seek (press & hold speed)",
        description = "Hold anywhere on the video to speed playback up; release to restore the normal speed.",
        checked = SettingsState.holdToSpeedEnabled,
        onCheckedChange = { SettingsState.setHoldToSpeedEnabled(context, it) },
    )
    if (SettingsState.holdToSpeedEnabled) {
        var holdRate by remember { mutableStateOf(SettingsState.holdSpeedRate) }
        SliderRow(
            title = "Hold speed",
            description = "The playback rate used while the video surface is held.",
            valueLabel = "${holdRate}x",
            value = holdRate,
            range = 1.5f..3f,
            onValueChange = {
                holdRate = (it * 100).toInt() / 100f
                SettingsState.setHoldSpeedRate(context, holdRate)
            },
        )
    }

    SectionTitle("Playback")

    var defaultSpeed by remember { mutableStateOf(SettingsState.playbackRate) }
    SelectRow(
        title = "Default playback speed",
        description = "Every episode starts at this speed (adjustable in the player).",
        options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { v ->
            val label = if (v == v.toInt().toFloat()) "${v.toInt()}x" else "${v}x"
            v.toString() to label
        },
        value = defaultSpeed.toString(),
        onValueChange = {
            defaultSpeed = it.toFloat()
            SettingsState.setPlaybackRate(context, defaultSpeed)
        },
    )
    ToggleRow(
        title = "Autoplay",
        description = "Start playing automatically when an episode opens.",
        checked = SettingsState.autoplay,
        onCheckedChange = { SettingsState.setAutoplay(context, it) },
    )
    ToggleRow(
        title = "Auto-play next episode",
        description = "When an episode ends, roll into the next one.",
        checked = SettingsState.autonext,
        onCheckedChange = { SettingsState.setAutonext(context, it) },
    )
    if (SettingsState.autonext) {
        var countdown by remember { mutableStateOf(SettingsState.autonextCountdownSec) }
        SliderRow(
            title = "Next-episode countdown",
            description = "The Up-Next card counts down before the next episode starts. 0 = switch instantly.",
            valueLabel = if (countdown == 0) "Instant" else "${countdown}s",
            value = countdown.toFloat(),
            range = 0f..30f,
            onValueChange = {
                countdown = it.toInt()
                SettingsState.setAutonextCountdownSec(context, countdown)
            },
        )
    }
    ToggleRow(
        title = "Remember playback position",
        description = "Save where you stopped and auto-resume the episode (and the show) next time.",
        checked = SettingsState.rememberPosition,
        onCheckedChange = { SettingsState.setRememberPosition(context, it) },
    )

    SectionTitle("Intros & Fillers")

    ToggleRow(
        title = "Auto-skip intro / outro",
        description = "Automatically jump detected openings and endings using the episode's real skip timestamps.",
        checked = SettingsState.autoskip,
        onCheckedChange = { SettingsState.setAutoskip(context, it) },
    )
    var introSkip by remember { mutableStateOf(SettingsState.introSkipDuration) }
    SliderRow(
        title = "Intro skip fallback",
        description = "Used only when an episode has no intro metadata from the server.",
        valueLabel = "${introSkip}s",
        value = introSkip.toFloat(),
        range = 5f..180f,
        onValueChange = {
            introSkip = it.toInt()
            SettingsState.setIntroSkipDuration(context, introSkip)
        },
    )
    ToggleRow(
        title = "Skip filler episodes",
        description = "Next/auto-next jumps over fillers; a clear FILLER badge marks them in lists and the player.",
        checked = SettingsState.skipFillers,
        onCheckedChange = { SettingsState.setSkipFillers(context, it) },
    )
    ToggleRow(
        title = "Auto-skip filler on arrival",
        description = "When auto-advancing lands on a filler, keep going to the next canon episode automatically.",
        checked = SettingsState.autoSkipFiller,
        onCheckedChange = { SettingsState.setAutoSkipFiller(context, it) },
    )

    SectionTitle("Episodes")

    ToggleRow(
        title = "Episode thumbnails",
        description = "Show preview images in episode lists.",
        checked = SettingsState.episodeThumbnails,
        onCheckedChange = { SettingsState.setEpisodeThumbnails(context, it) },
    )
    SelectRow(
        title = "Episode order",
        description = "Sort episodes ascending (1, 2, 3…) or descending in lists.",
        options = listOf("asc" to "Ascending", "desc" to "Descending"),
        value = SettingsState.episodeSortOrder,
        onValueChange = { SettingsState.setEpisodeSortOrder(context, it) },
    )

    SectionTitle("Player display")

    ToggleRow(
        title = "Ambient mode",
        description = "Immersive ambient lighting effects around the video player.",
        checked = SettingsState.ambientMode,
        onCheckedChange = { SettingsState.setAmbientMode(context, it) },
    )
    ToggleRow(
        title = "Mini progress bar",
        description = "A thin progress bar along the bottom of the player while the controls are hidden.",
        checked = SettingsState.miniProgressBar,
        onCheckedChange = { SettingsState.setMiniProgressBar(context, it) },
    )

    SectionTitle("Stream")

    SelectRow(
        title = "Stream quality",
        description = "Coarse quality cap for streams; the exact rendition you pick in the player persists separately.",
        options = listOf(
            "auto" to "Auto",
            "low" to "480p",
            "standard" to "720p",
            "full" to "1080p",
        ),
        value = SettingsState.streamQuality,
        onValueChange = { SettingsState.setStreamQuality(context, it) },
    )
    SelectRow(
        title = "Preferred audio",
        description = "Default to subbed or dubbed streams when both are available.",
        options = listOf("sub" to "Sub", "dub" to "Dub"),
        value = SettingsState.streamLang,
        onValueChange = { SettingsState.setStreamLang(context, it) },
    )

    SectionTitle("Fullscreen")

    SelectRow(
        title = "Fullscreen orientation",
        description = "How the player behaves when you enter fullscreen (the app's bars hide completely).",
        options = listOf(
            "landscape" to "Landscape",
            "sensor" to "Follow sensor",
            "portrait" to "Portrait",
            "none" to "No lock",
        ),
        value = SettingsState.fullscreenOrientation,
        onValueChange = { SettingsState.setFullscreenOrientation(context, it) },
    )

    SectionTitle("Volume")

    var volume by remember { mutableStateOf(SettingsState.volume) }
    SliderRow(
        title = "Default volume",
        description = "Player volume when a new episode starts.",
        valueLabel = "${(volume * 100).toInt()}%",
        value = volume,
        range = 0f..1f,
        onValueChange = {
            volume = it
            SettingsState.setVolume(context, it)
        },
    )
    ToggleRow(
        title = "Mute Audio",
        description = "Always start muted.",
        checked = SettingsState.muted,
        onCheckedChange = { SettingsState.setMuted(context, it) },
    )

    SectionTitle("Captions")

    CaptionsSettingsPage()

    SectionTitle("Live Captions")

    ToggleRow(
        title = "Live captions",
        description = "Caption the playing audio on-device with speech recognition — works even when a stream " +
            "has no subtitle files. Best with speaker playback; requires microphone permission.",
        checked = SettingsState.liveCaptionsEnabled,
        onCheckedChange = { SettingsState.setLiveCaptionsEnabled(context, it) },
    )
    SelectRow(
        title = "Recognition language",
        description = "The language live captions listen for.",
        options = listOf(
            "en-US" to "English",
            "es-ES" to "Spanish",
            "fr-FR" to "French",
            "de-DE" to "German",
            "pt-BR" to "Portuguese",
            "ar-SA" to "Arabic",
            "it-IT" to "Italian",
            "ja-JP" to "Japanese",
        ),
        value = SettingsState.liveCaptionsLanguage,
        onValueChange = { SettingsState.setLiveCaptionsLanguage(context, it) },
    )
}

// ---------------------------------------------------------------------------
//  CAPTIONS — full styling (v2.1.0 rows preserved inside Player)
// ---------------------------------------------------------------------------

@Composable
private fun CaptionsSettingsPage() {
    val context = LocalContext.current
    val theme = LocalAnikageTheme.current
    var styles by remember { mutableStateOf(SettingsState.captionStyles) }
    val update: (com.anikage.app.core.settings.CaptionStyles) -> Unit = { next ->
        styles = next
        SettingsState.setCaptionStyles(context, next)
    }

    SettingsCard {
        Text(
            text = "Preferred subtitle language",
            style = WebTextStyles.base,
            color = theme.fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = "Auto-selected when the stream offers multiple subtitle tracks.",
            style = WebTextStyles.sm,
            color = Color(0xFF71717A),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("English", "Spanish", "French", "German", "Portuguese", "Arabic", "Italian").take(4).forEach { lang ->
                val active = SettingsState.defaultSubtitleLang.equals(lang, ignoreCase = true)
                Text(
                    text = lang,
                    style = WebTextStyles.sm,
                    color = if (active) theme.actionFg else Color(0xFFA1A1AA),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) theme.action else Color(0x0DFFFFFF))
                        .clickable { SettingsState.setDefaultSubtitleLang(context, lang) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }

    SliderRow(
        title = "Subtitle size",
        description = "Scales the caption text in and out of fullscreen.",
        valueLabel = "${(styles.fontSize * 100).toInt()}%",
        value = styles.fontSize,
        range = 0.5f..2f,
        steps = 5,
        onValueChange = { update(styles.copy(fontSize = it)) },
    )
    SelectRow(
        title = "Weight",
        description = "Normal or bold caption text.",
        options = listOf("400" to "Normal", "700" to "Bold"),
        value = styles.fontWeight.toString(),
        onValueChange = { update(styles.copy(fontWeight = it.toInt())) },
    )
    SliderRow(
        title = "Cue background opacity",
        description = "The box behind each caption line.",
        valueLabel = "${(styles.textBgOpacity * 100).toInt()}%",
        value = styles.textBgOpacity,
        range = 0f..1f,
        steps = 4,
        onValueChange = { update(styles.copy(textBgOpacity = it)) },
    )
    SelectRow(
        title = "Cue background",
        description = "A solid black box behind each caption (uses the opacity above).",
        options = listOf("on" to "On", "off" to "Off"),
        value = if (styles.textBgOpacity > 0f) "on" else "off",
        onValueChange = { update(styles.copy(textBgOpacity = if (it == "on") 0.6f else 0f)) },
    )
    SliderRow(
        title = "Vertical position",
        description = "Lift captions off the bottom edge of the player.",
        valueLabel = "${(styles.position * 100).toInt()}%",
        value = styles.position,
        range = 0f..0.4f,
        onValueChange = { update(styles.copy(position = (it * 100).toInt() / 100f)) },
    )

    SettingsCard {
        Text(
            text = "Reset caption styles",
            style = WebTextStyles.base,
            color = theme.fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = "Restore the default Anikage caption look.",
            style = WebTextStyles.sm,
            color = Color(0xFF71717A),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
            text = "Reset",
            style = WebTextStyles.sm,
            color = theme.action,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x14FFFFFF))
                .clickable {
                    styles = com.anikage.app.core.settings.CaptionStyles()
                    SettingsState.setCaptionStyles(context, styles)
                }
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

// ---------------------------------------------------------------------------
//  THEMES — the site's 11 [data-theme] palettes
// ---------------------------------------------------------------------------

private data class ThemeOption(val key: String, val label: String, val description: String, val swatch: List<Long>)

private val ThemeOptions = listOf(
    ThemeOption("default", "Default", "Clean monochrome with crisp white accents.", listOf(0xFF0A0A0A, 0xFF151515, 0xFFFFFFFF)),
    ThemeOption("midnight", "Midnight", "Deep ocean blues for late-night binges.", listOf(0xFF0A0A14, 0xFF15152A, 0xFF3B82F6)),
    ThemeOption("crimson", "Crimson", "Warm reds and ember tones.", listOf(0xFF140A0A, 0xFF2A1515, 0xFFEF4444)),
    ThemeOption("emerald", "Emerald", "Calm forest greens and jade.", listOf(0xFF0A140D, 0xFF152A1E, 0xFF10B981)),
    ThemeOption("amoled", "Amoled", "Pure black for OLED displays. Battery friendly.", listOf(0xFF000000, 0xFF0A0A0A, 0xFFFFFFFF)),
    ThemeOption("sunset", "Sunset", "Warm amber and coral hues.", listOf(0xFF140D0A, 0xFF2A1E15, 0xFFF59E0B)),
    ThemeOption("rose", "Rose", "Soft pinks and magentas.", listOf(0xFF140A0D, 0xFF2A1520, 0xFFF43F5E)),
    ThemeOption("galaxy", "Galaxy", "Deep purple-black with aurora violet accents.", listOf(0xFF0D0A14, 0xFF1A152A, 0xFF8B5CF6)),
    ThemeOption("ocean", "Ocean", "Deep sea blues with glowing teal highlights.", listOf(0xFF0A0F14, 0xFF15222A, 0xFF14B8A6)),
    ThemeOption("sakura", "Sakura", "Cherry blossom pinks on a dark canvas.", listOf(0xFF140D10, 0xFF2A1A20, 0xFFEC4899)),
    ThemeOption("amber", "Amber", "Warm golden tones, clean and minimal.", listOf(0xFF14110A, 0xFF2A2415, 0xFFD97706)),
)

@Composable
private fun ThemesPage() {
    SectionTitle("Appearance")
    Text(
        text = "Anikage's own theme palettes — the app restarts visually the moment you pick one.",
        style = WebTextStyles.sm,
        color = Color(0xFF71717A),
        modifier = Modifier.padding(bottom = 8.dp),
    )
    ThemeOptions.forEach { option -> ThemeOptionCard(option) }
}

@Composable
private fun ThemeOptionCard(option: ThemeOption) {
    val theme = LocalAnikageTheme.current
    val context = LocalContext.current
    val active = ThemeState.key == option.key
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x08FFFFFF))
            .border(
                1.dp,
                if (active) theme.action else Color(0x0FFFFFFF),
                RoundedCornerShape(16.dp),
            )
            .clickable { ThemeState.set(context, option.key) }
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                option.swatch.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(1.dp, Color(0x1AFFFFFF), CircleShape),
                    )
                }
            }
            Text(
                text = option.label,
                style = WebTextStyles.base,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (active) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(theme.action),
                )
            }
        }
        Text(
            text = option.description,
            style = WebTextStyles.sm,
            color = Color(0xFF71717A),
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
//  ABOUT
// ---------------------------------------------------------------------------

@Composable
private fun AboutSection(onOpenDiagnostics: () -> Unit, onOpenDmca: () -> Unit) {
    val theme = LocalAnikageTheme.current

    SectionTitle("About")

    SettingsCard {
        Text(
            text = "Anikage",
            style = WebTextStyles.lg,
            color = theme.fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = "Version ${Config.APP_VERSION} (${Config.APP_VERSION_CODE}) — the Anikage " +
                "experience, rebuilt natively. All content, artwork and streams come from " +
                "anikage.cc and its official API.",
            style = WebTextStyles.sm,
            color = Color(0xFF71717A),
            lineHeight = 20.sp,
        )
    }

    // Diagnostics entry — the app's developer-log surface.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenDiagnostics)
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                tint = theme.fg,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Diagnostics",
                    style = WebTextStyles.base,
                    color = theme.fg,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "System health and application logs",
                    style = WebTextStyles.sm,
                    color = Color(0xFF71717A),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF71717A),
                modifier = Modifier.size(18.dp),
            )
        }
    }

    // DMCA + Legal entry (site: footer "Legal / DMCA").
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenDmca)
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Balance,
                contentDescription = null,
                tint = theme.fg,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DMCA + Legal Information",
                    style = WebTextStyles.base,
                    color = theme.fg,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Service model, copyright policy and user responsibilities",
                    style = WebTextStyles.sm,
                    color = Color(0xFF71717A),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF71717A),
                modifier = Modifier.size(18.dp),
            )
        }
    }

    SettingsCard {
        Text(
            text = "Credits",
            style = WebTextStyles.base,
            color = theme.fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = "Anime data & artwork: AniList · TheTVDB\nStreaming: Anikage servers\n" +
                "Built for Anikage — this app is a client, not the source.",
            style = WebTextStyles.sm,
            color = Color(0xFF71717A),
            lineHeight = 20.sp,
        )
    }
}

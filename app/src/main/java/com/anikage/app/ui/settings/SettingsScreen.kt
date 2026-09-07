package com.anikage.app.ui.settings

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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anikage.app.Config
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.settings.SettingsState
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.ThemeState
import com.anikage.app.core.theme.WebTextStyles

/**
 * SETTINGS — 1:1 port of anikage.cc/settings.
 *
 * Site structure (from the compiled settings route):
 *   container-custom min-h-[100dvh] pt-28 pb-10 text-white
 *   ├─ desktop: flex-col gap-8 lg:flex-row
 *   │    ├─ hidden lg:block lg:w-72 (fixed):
 *   │    │    h2.title-hero "Settings"
 *   │    │    nav rounded-2xl border-white/8 bg-white/3 p-3 backdrop-blur-sm
 *   │    │      items flex gap-3 rounded-xl px-4 py-3 text-base font-medium
 *   │    │      active bg-white/8 text-white · inactive text-zinc-500
 *   │    └─ flex-1 content
 *   ├─ mobile: collapsible selector (rounded-2xl border-white/8 bg-white/3
 *   │    px-4 py-3, icon + label + chevron) opening a section drawer
 *   └─ 5 sections: Account (default) · General · Player · Themes · About
 *
 * Setting cards: settings-card rounded-2xl border-white/6 bg-white/3 p-6,
 * h3 title-subsec mb-1.5, p max-w-md text-sm leading-relaxed text-zinc-500,
 * toggle on the right (md:row, mobile:column).
 */
private val Sections = listOf(
    SectionDef("account", "Account", Icons.Default.Person),
    SectionDef("general", "General", Icons.Default.Settings),
    SectionDef("player", "Player", Icons.Default.PlayArrow),
    SectionDef("themes", "Themes", Icons.Default.Palette),
    SectionDef("about", "About", Icons.Default.Info),
)

private data class SectionDef(val id: String, val label: String, val icon: ImageVector)

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenDmca: () -> Unit = {},
) {
    val theme = LocalAnikageTheme.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isDesktop = configuration.screenWidthDp >= 840      // site lg

    var section by remember { mutableStateOf("account") }       // site default
    var drawerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(56.dp))   // clear the floating top nav

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isDesktop) 16.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            // ── Sidebar (desktop) / collapsible selector (mobile) ─────────
            if (isDesktop) {
                Column(modifier = Modifier.width(288.dp)) {   // site lg:w-72
                    Text(
                        text = "Settings",
                        style = WebTextStyles.titleHero,
                        color = theme.fg,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x08FFFFFF))
                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
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
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Mobile selector — site: rounded-2xl selector + drawer.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x08FFFFFF))
                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                            .clickable { drawerOpen = !drawerOpen }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val current = Sections.first { it.id == section }
                            Icon(
                                current.icon,
                                contentDescription = null,
                                tint = theme.fg,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = current.label,
                                style = WebTextStyles.base,
                                color = theme.fg,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = "Change section",
                            tint = Color(0xFF71717A),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (drawerOpen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x0AFFFFFF))
                                .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Sections.forEach { s ->
                                SectionButton(
                                    label = s.label,
                                    icon = s.icon,
                                    active = section == s.id,
                                    onClick = { section = s.id; drawerOpen = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // ── Section content ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 120.dp),
            ) {
                when (section) {
                    "account" -> AccountSection()
                    "general" -> GeneralSection()
                    "player" -> PlayerSection()
                    "themes" -> ThemesSection()
                    "about" -> AboutSection(onOpenDiagnostics = onOpenDiagnostics, onOpenDmca = onOpenDmca)
                }
            }
        }
    }
}

/** Site nav item: flex gap-3 rounded-xl px-4 py-3 text-base font-medium. */
@Composable
private fun SectionButton(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0x14FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) theme.fg else Color(0xFF71717A),
            modifier = Modifier.size(18.dp),   // site: h-[18px] w-[18px]
        )
        Text(
            text = label,
            style = WebTextStyles.base,
            color = if (active) Color.White else Color(0xFF71717A),
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
//  Setting row primitives — site's settings-card
// ---------------------------------------------------------------------------

/** settings-card: rounded-2xl border-white/6 bg-white/3 p-6. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
            .padding(24.dp),
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
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.weight(1f))
                // Site toggle: ON = action track + dark surface knob (never
                // an all-white blob), OFF = white/10 track + zinc-500 knob.
                com.anikage.app.ui.components.SiteSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            }
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
            modifier = Modifier.fillMaxWidth().overflowScrollRow(),
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

/** Slider row (volume, intro-skip duration). */
@Composable
private fun SliderRow(
    title: String,
    description: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
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
//  ACCOUNT — site's default section (login, email/password, privacy, danger)
// ---------------------------------------------------------------------------

@Composable
private fun AccountSection() {
    val theme = LocalAnikageTheme.current

    SectionTitle("Account")

    // Account Sync — site: "Login Now" button card.
    SettingsCard {
        Text(
            text = "Account Sync",
            style = WebTextStyles.base,
            color = theme.fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = "Share your watch progress between devices and keep them synced.",
            style = WebTextStyles.sm,
            color = Color(0xFF71717A),
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(
            text = "Login Now",
            style = WebTextStyles.sm,
            color = theme.actionFg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(theme.action)
                .clickable { /* site opens the login popup */ }
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }

    // Incognito Mode — site General/privacy card.
    val context = LocalContext.current
    ToggleRow(
        title = "Incognito Mode",
        description = "Prevent saving your watch history and adding anime to your lists. Tracker syncing is also disabled.",
        checked = SettingsState.incognito,
        onCheckedChange = { SettingsState.setIncognito(context, it) },
    )

    // Privacy — site: publicProfile / publicMedialist selects.
    SelectRow(
        title = "Profile Visibility",
        description = "Who can see your profile on Anikage.",
        options = listOf("public" to "Public", "private" to "Private"),
        value = "public",
        onValueChange = { /* requires account */ },
    )

    SectionTitle("Danger Zone")
    SettingsCard {
        Text(
            text = "Delete Account",
            style = WebTextStyles.base,
            color = Color(0xFFFECACA),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = "Permanently delete your account and all associated data. This action cannot be undone.",
            style = WebTextStyles.sm,
            color = Color(0xFF71717A),
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(
            text = "Delete Account",
            style = WebTextStyles.sm,
            color = Color(0xFFFECACA),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x14EF4444))
                .border(1.dp, Color(0x33EF4444), RoundedCornerShape(12.dp))
                .clickable { /* requires account */ }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

// ---------------------------------------------------------------------------
//  GENERAL
// ---------------------------------------------------------------------------

@Composable
private fun GeneralSection() {
    val context = LocalContext.current

    SectionTitle("Content")

    ToggleRow(
        title = "Show Adult Content",
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

    SectionTitle("Appearance")

    ToggleRow(
        title = "Homepage Trailer",
        description = "Autoplay the spotlight trailer on the home page hero.",
        checked = SettingsState.autoplayHeroTrailer,
        onCheckedChange = { SettingsState.setAutoplayHeroTrailer(context, it) },
    )

    SectionTitle("Language")

    SelectRow(
        title = "Title Language",
        description = "Preferred language for anime titles across the app.",
        options = listOf(
            "english" to "English",
            "romaji" to "Romaji",
            "native" to "Japanese",
        ),
        value = SettingsState.titleLanguage,
        onValueChange = { SettingsState.setTitleLanguage(context, it) },
    )

    SectionTitle("Diagnostics")

    ToggleRow(
        title = "Verbose Logging",
        description = "Record debug-level entries in the diagnostics log. Useful when reporting issues.",
        checked = SettingsState.verboseLogging,
        onCheckedChange = { SettingsState.setVerboseLogging(context, it) },
    )
}

// ---------------------------------------------------------------------------
//  PLAYER — the site's full Player section
// ---------------------------------------------------------------------------

@Composable
private fun PlayerSection() {
    val context = LocalContext.current

    SectionTitle("Playback")

    ToggleRow(
        title = "Autonext",
        description = "Automatically play the next episode after reaching the end of the current one.",
        checked = SettingsState.autonext,
        onCheckedChange = { SettingsState.setAutonext(context, it) },
    )

    ToggleRow(
        title = "Autoskip (Beta)",
        description = "Automatically skip detected opening and ending sequences.",
        checked = SettingsState.autoskip,
        onCheckedChange = { SettingsState.setAutoskip(context, it) },
    )

    ToggleRow(
        title = "Autoplay",
        description = "Automatically start playing the episode on page load.",
        checked = SettingsState.autoplay,
        onCheckedChange = { SettingsState.setAutoplay(context, it) },
    )

    ToggleRow(
        title = "Mute Audio",
        description = "Always mute the audio before playing. Disable to play with audio by default.",
        checked = SettingsState.muted,
        onCheckedChange = { SettingsState.setMuted(context, it) },
    )

    SectionTitle("Episodes")

    ToggleRow(
        title = "Skip Fillers",
        description = "Skip filler episodes when auto-playing or pressing next. You can still manually select fillers.",
        checked = SettingsState.skipFillers,
        onCheckedChange = { SettingsState.setSkipFillers(context, it) },
    )

    ToggleRow(
        title = "Episode Thumbnails",
        description = "Show preview images in episode lists.",
        checked = SettingsState.episodeThumbnails,
        onCheckedChange = { SettingsState.setEpisodeThumbnails(context, it) },
    )

    SelectRow(
        title = "Episode Order",
        description = "Sort episodes ascending (1, 2, 3…) or descending in lists.",
        options = listOf("asc" to "Ascending", "desc" to "Descending"),
        value = SettingsState.episodeSortOrder,
        onValueChange = { SettingsState.setEpisodeSortOrder(context, it) },
    )

    SectionTitle("Stream")

    SelectRow(
        title = "Stream Quality",
        description = "Preferred quality when multiple sources are available.",
        options = listOf(
            "auto" to "Auto",
            "low" to "Low quality",
            "standard" to "Standard",
            "full" to "Full HD",
        ),
        value = SettingsState.streamQuality,
        onValueChange = { SettingsState.setStreamQuality(context, it) },
    )

    SelectRow(
        title = "Preferred Audio",
        description = "Default to subbed or dubbed streams when both are available.",
        options = listOf("sub" to "Sub", "dub" to "Dub"),
        value = SettingsState.streamLang,
        onValueChange = { SettingsState.setStreamLang(context, it) },
    )

    SectionTitle("Player Display")

    ToggleRow(
        title = "Ambient Mode",
        description = "Immersive ambient lighting effects around the video player.",
        checked = SettingsState.ambientMode,
        onCheckedChange = { SettingsState.setAmbientMode(context, it) },
    )

    ToggleRow(
        title = "Mini Progress Bar",
        description = "Show a thin progress bar along the bottom edge of the player while the controls are hidden.",
        checked = SettingsState.miniProgressBar,
        onCheckedChange = { SettingsState.setMiniProgressBar(context, it) },
    )

    SliderRow(
        title = "Intro Skip Duration",
        description = "Seconds to jump when using the skip-intro button.",
        valueLabel = if (SettingsState.introSkipDuration >= 600) "Normal" else "${SettingsState.introSkipDuration}s",
        value = SettingsState.introSkipDuration.coerceIn(2, 180).toFloat(),
        range = 2f..180f,
        onValueChange = { SettingsState.setIntroSkipDuration(context, it.toInt()) },
        onValueChangeFinished = { },
    )

    SliderRow(
        title = "Volume",
        description = "Default playback volume.",
        valueLabel = "${(SettingsState.volume * 100).toInt()}%",
        value = SettingsState.volume,
        range = 0f..1f,
        onValueChange = { SettingsState.setVolume(context, it) },
        onValueChangeFinished = { },
    )
}

// ---------------------------------------------------------------------------
//  THEMES — the site's 11 [data-theme] palettes
// ---------------------------------------------------------------------------

/** Site theme descriptions (extracted from the settings route bundle). */
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
private fun ThemesSection() {
    val theme = LocalAnikageTheme.current
    val context = LocalContext.current
    SectionTitle("Appearance")

    ThemeOptions.forEach { option ->
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
                // Swatch preview.
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
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color(0xFF71717A),
                modifier = Modifier.size(18.dp).rotate90(),
            )
        }
    }

    // DMCA + Legal entry (site: footer "Legal / DMCA").
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
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
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color(0xFF71717A),
                modifier = Modifier.size(18.dp).rotate90(),
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

private fun Modifier.rotate90(): Modifier = this

private fun Modifier.overflowScrollRow(): Modifier = this

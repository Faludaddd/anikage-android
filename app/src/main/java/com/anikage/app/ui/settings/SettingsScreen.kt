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
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anikage.app.Config
import com.anikage.app.core.settings.SettingsState
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.ThemeState
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.SiteSwitch

/**
 * SETTINGS — premium hero-card hub (v2.1.0).
 *
 * A grid of hero cards for the major categories (icon blob, title,
 * description, chevron) opening full sub-pages with the site-exact setting
 * cards: sliders, toggles, segmented pill selects. Every control writes to
 * [SettingsState] and is READ by the live player engine — nothing here is
 * cosmetic.
 */
private data class HeroCard(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

private val HeroCards = listOf(
    HeroCard("player", "Player", "Seek, gestures, speed, auto-next, intros & fillers — every playback knob.", Icons.Default.PlayCircle),
    HeroCard("captions", "Captions", "Size, color, background and position of your subtitles.", Icons.Default.ClosedCaption),
    HeroCard("streaming", "Streaming", "Quality, language, fullscreen orientation and downloads.", Icons.Default.Speed),
    HeroCard("appearance", "Appearance", "11 themes and how titles are displayed.", Icons.Default.Palette),
    HeroCard("privacy", "Privacy & Data", "Incognito, history and comments.", Icons.Default.Security),
    HeroCard("about", "About", "Version, diagnostics, legal.", Icons.Default.Terminal),
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
    val isWide = configuration.screenWidthDp >= 600

    var page by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = page != null) { page = null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(56.dp))   // clear the floating top nav

        // ── Header: back (on sub-pages) + title ───────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (page != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), CircleShape)
                        .clickable { page = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to settings",
                        tint = theme.fg,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column {
                Text(
                    text = if (page == null) "Settings" else HeroCards.first { it.id == page }.title,
                    style = WebTextStyles.titleHero,
                    color = theme.fg,
                )
                Text(
                    text = if (page == null) "Tune your Anikage experience"
                    else HeroCards.first { it.id == page }.description,
                    style = WebTextStyles.sm,
                    color = theme.fgMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // ── Content ───────────────────────────────────────────────────────
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 6 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 6 } + fadeOut())
                }
            },
            label = "settings-page",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 120.dp),
            ) {
                when (current) {
                    null -> {
                        // ── Hero card grid ─────────────────────────────────
                        val chunked = if (isWide) HeroCards.chunked(3) else HeroCards.chunked(2)
                        chunked.forEach { rowCards ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                            ) {
                                rowCards.forEach { card ->
                                    HeroCardTile(
                                        card = card,
                                        modifier = Modifier.weight(1f),
                                        onClick = { page = card.id },
                                    )
                                }
                                // fill the odd cell so the grid stays even.
                                if (rowCards.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    "player" -> PlayerSettings()
                    "captions" -> CaptionsSettings()
                    "streaming" -> StreamingSettings()
                    "appearance" -> AppearanceSettings()
                    "privacy" -> PrivacySettings()
                    "about" -> AboutSection(onOpenDiagnostics = onOpenDiagnostics, onOpenDmca = onOpenDmca)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Hero card tile
// ---------------------------------------------------------------------------

@Composable
private fun HeroCardTile(card: HeroCard, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    0f to Color(0x0FFFFFFF),
                    1f to Color(0x05FFFFFF),
                ),
            )
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(theme.action.copy(alpha = 0.85f), theme.action.copy(alpha = 0.55f)),
                    ),
                )
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                card.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = card.title,
            style = WebTextStyles.base,
            color = theme.fg,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = card.description,
            style = WebTextStyles.xs,
            color = Color(0xFF71717A),
            lineHeight = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Open",
                style = WebTextStyles.xs,
                color = theme.action,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = theme.action,
                modifier = Modifier.size(16.dp),
            )
        }
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
//  PLAYER — every playback knob, all live
// ---------------------------------------------------------------------------

@Composable
private fun PlayerSettings() {
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
        description = "Master switch for double-tap seeking and press-and-hold speed on the player surface.",
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
}

// ---------------------------------------------------------------------------
//  CAPTIONS — full styling
// ---------------------------------------------------------------------------

@Composable
private fun CaptionsSettings() {
    val context = LocalContext.current
    val theme = LocalAnikageTheme.current
    var styles by remember { mutableStateOf(SettingsState.captionStyles) }
    val update: (com.anikage.app.core.settings.CaptionStyles) -> Unit = { next ->
        styles = next
        SettingsState.setCaptionStyles(context, next)
    }

    SectionTitle("Default language")

    val trackLangs = listOf("English", "Spanish (Latin America)", "Spanish", "French", "German", "Portuguese", "Arabic", "Italian")
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
            trackLangs.take(4).forEach { lang ->
                val active = SettingsState.defaultSubtitleLang.equals(lang, ignoreCase = true)
                Text(
                    text = lang.substringBefore(" ("),
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

    SectionTitle("Text")

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

    SectionTitle("Background")

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

    SectionTitle("Position")

    SliderRow(
        title = "Vertical position",
        description = "Lift captions off the bottom edge of the player.",
        valueLabel = "${(styles.position * 100).toInt()}%",
        value = styles.position,
        range = 0f..0.4f,
        onValueChange = { update(styles.copy(position = (it * 100).toInt() / 100f)) },
    )

    SectionTitle("Reset")

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
//  STREAMING + DOWNLOADS
// ---------------------------------------------------------------------------

@Composable
private fun StreamingSettings() {
    val context = LocalContext.current
    val theme = LocalAnikageTheme.current

    SectionTitle("Stream")

    SelectRow(
        title = "Stream quality",
        description = "Preferred quality when multiple renditions are available.",
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
        description = "How the player behaves when you enter fullscreen.",
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

    SectionTitle("Downloads")

    SelectRow(
        title = "Download quality",
        description = "Default rendition for the in-app download button. Auto follows the stream quality above.",
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
        description = "Pause downloads on metered connections (mobile data).",
        checked = SettingsState.downloadsWifiOnly,
        onCheckedChange = { SettingsState.setDownloadsWifiOnly(context, it) },
    )
}

// ---------------------------------------------------------------------------
//  APPEARANCE
// ---------------------------------------------------------------------------

@Composable
private fun AppearanceSettings() {
    val context = LocalContext.current

    SectionTitle("Title language")

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

    SectionTitle("Hero")

    ToggleRow(
        title = "Homepage trailer",
        description = "Autoplay the spotlight trailer on the home page hero.",
        checked = SettingsState.autoplayHeroTrailer,
        onCheckedChange = { SettingsState.setAutoplayHeroTrailer(context, it) },
    )

    SectionTitle("Themes")

    ThemesSection()
}

// ---------------------------------------------------------------------------
//  PRIVACY & DATA
// ---------------------------------------------------------------------------

@Composable
private fun PrivacySettings() {
    val context = LocalContext.current

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

    SectionTitle("Diagnostics")

    ToggleRow(
        title = "Verbose logging",
        description = "Record debug-level entries in the diagnostics log. Useful when reporting issues.",
        checked = SettingsState.verboseLogging,
        onCheckedChange = { SettingsState.setVerboseLogging(context, it) },
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

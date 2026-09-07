package com.anikage.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anikage.app.core.settings.CaptionStyles
import com.anikage.app.core.settings.SettingsState
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import kotlin.math.abs

/**
 * The player settings panel — 1:1 with the site's svelte-qcc9et panel:
 * a translucent slide-over (anchored bottom-right) with a header (back +
 * title), a scrollable body of rows, slider cards with range labels, option
 * lists with check marks, an automation toggle group, and a reset row.
 */
@Composable
internal fun PlayerSettingsPanel(
    state: WatchUiState,
    viewModel: WatchViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf<SettingsPage>(SettingsPage.Root) }
    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(Color(0x88000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (page == SettingsPage.Root) onDismiss() else page = SettingsPage.Root },
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .width(300.dp)
                .fillMaxHeight(0.62f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xD90D0D0D))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            // ── panel header (site: back button + h2 title + hint) ────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (page == SettingsPage.Root) onDismiss() else page = SettingsPage.Root
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (page == SettingsPage.Root) Icons.Default.Subtitles else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (page == SettingsPage.Root) "Close settings" else "Back",
                        tint = Color(0xA6FFFFFF),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = pageTitle(page),
                    style = WebTextStyles.sm.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when (page) {
                        SettingsPage.Root -> "${state.speed}x"
                        else -> ""
                    },
                    style = WebTextStyles.xs,
                    color = Color(0x70FFFFFF),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x12FFFFFF)))

            // ── panel body ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
            ) {
                when (page) {
                    SettingsPage.Root -> RootPage(state, viewModel) { page = it }
                    SettingsPage.Speed -> SpeedPage(state, viewModel)
                    SettingsPage.Boost -> BoostPage(state, viewModel)
                    SettingsPage.Audio -> AudioPage(state, viewModel)
                    SettingsPage.Subtitles -> SubtitlesPage(state, viewModel)
                    SettingsPage.CaptionStyles -> CaptionStylesPage(context)
                    SettingsPage.Quality -> QualityPage(state, viewModel)
                    SettingsPage.More -> MorePage(context)
                }
            }
        }
    }
}

private enum class SettingsPage {
    Root, Speed, Boost, Audio, Subtitles, CaptionStyles, Quality, More
}

private fun pageTitle(page: SettingsPage): String = when (page) {
    SettingsPage.Root -> "Settings"
    SettingsPage.Speed -> "Playback speed"
    SettingsPage.Boost -> "Audio boost"
    SettingsPage.Audio -> "Audio"
    SettingsPage.Subtitles -> "Subtitles / CC"
    SettingsPage.CaptionStyles -> "Caption Styles"
    SettingsPage.Quality -> "Quality"
    SettingsPage.More -> "Player settings"
}

// ---------------------------------------------------------------------------
//  Root page — setting rows that drill into sub-pages (site labels 1:1)
// ---------------------------------------------------------------------------

@Composable
private fun RootPage(state: WatchUiState, viewModel: WatchViewModel, onOpen: (SettingsPage) -> Unit) {
    PanelGroupLabel("Playback")
    SettingRow(
        icon = { Icon(Icons.Default.Speed, null, tint = Color(0xBDFFFFFF), modifier = Modifier.size(16.dp)) },
        label = "Playback speed",
        value = "${state.speed}x",
        onClick = { onOpen(SettingsPage.Speed) },
    )
    // Volume slider (site: media-volume-slider, persisted master volume).
    var volume by remember(state.masterVolume) { mutableFloatStateOf(state.masterVolume) }
    LaunchedEffect(volume) {
        if (abs(volume - state.masterVolume) > 0.01f) viewModel.setVolume(volume)
    }
    SliderCard(
        strongValue = "${(volume * 100).toInt()}%",
        value = volume,
        valueRange = 0f..1f,
        steps = 9,
        onValueChange = { volume = it },
        labelStart = "0%",
        labelEnd = "100%",
    )
    SettingRow(
        icon = { Icon(Icons.Default.VolumeUp, null, tint = Color(0xBDFFFFFF), modifier = Modifier.size(16.dp)) },
        label = "Audio boost",
        value = "${state.volume}x",
        onClick = { onOpen(SettingsPage.Boost) },
    )
    if (state.audioTracks.size > 1) {
        SettingRow(
            icon = { Icon(Icons.Default.VolumeUp, null, tint = Color(0xBDFFFFFF), modifier = Modifier.size(16.dp)) },
            label = "Audio",
            value = state.audioTracks.firstOrNull { it.isSelected }?.label ?: "Auto",
            onClick = { onOpen(SettingsPage.Audio) },
        )
    }
    SettingRow(
        icon = { Icon(Icons.Default.Subtitles, null, tint = Color(0xBDFFFFFF), modifier = Modifier.size(16.dp)) },
        label = "Subtitles / CC",
        value = state.textTracks.firstOrNull { it.isSelected }?.label
            ?: (state.textTracks.firstOrNull()?.label ?: "Off"),
        onClick = { onOpen(SettingsPage.Subtitles) },
    )
    PanelGroupLabel("Appearance")
    SettingRow(
        icon = { Icon(Icons.Default.Palette, null, tint = Color(0xBDFFFFFF), modifier = Modifier.size(16.dp)) },
        label = "Caption Styles",
        value = "Custom",
        onClick = { onOpen(SettingsPage.CaptionStyles) },
    )
    SettingRow(
        icon = { Icon(Icons.Default.Tune, null, tint = Color(0xBDFFFFFF), modifier = Modifier.size(16.dp)) },
        label = "Quality",
        value = state.qualities.firstOrNull { it.isSelected }?.label ?: "Auto",
        onClick = { onOpen(SettingsPage.Quality) },
    )
    SettingRow(
        icon = { Icon(Icons.Default.PrivacyTip, null, tint = Color(0xBDFFFFFF), modifier = Modifier.size(16.dp)) },
        label = "More",
        value = "",
        onClick = { onOpen(SettingsPage.More) },
    )
}

// ---------------------------------------------------------------------------
//  Sub pages
// ---------------------------------------------------------------------------

@Composable
private fun SpeedPage(state: WatchUiState, viewModel: WatchViewModel) {
    var value by remember(state.speed) { mutableFloatStateOf(state.speed) }
    LaunchedEffect(value) {
        if (abs(value - state.speed) > 0.01f) viewModel.setSpeed(value)
    }
    PanelGroupLabel("Playback speed")
    SliderCard(
        strongValue = "${value}x",
        value = value,
        valueRange = 0.25f..2f,
        steps = 6, // 0.25 increments: 0.25,0.5,0.75,1,1.25,1.5,1.75,2
        onValueChange = { value = it },
        labelStart = "0.25x",
        labelEnd = "2x",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 10.dp)) {
        listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
            OptionChip(
                label = "${s}x",
                selected = abs(state.speed - s) < 0.01f,
                onClick = { value = s },
            )
        }
    }
    HelperText("Applies instantly and is remembered for the next episode.")
}

@Composable
private fun BoostPage(state: WatchUiState, viewModel: WatchViewModel) {
    var value by remember(state.volume) { mutableFloatStateOf(state.volume) }
    LaunchedEffect(value) {
        if (abs(value - state.volume) > 0.01f) viewModel.setBoost(value)
    }
    PanelGroupLabel("Audio boost")
    SliderCard(
        strongValue = "${value}x",
        value = value,
        valueRange = 1f..2f,
        steps = 3,
        onValueChange = { value = it },
        labelStart = "1x",
        labelEnd = "2x",
    )
    HelperText("Boosts loudness beyond 100%. High values may distort audio.")
}

@Composable
private fun AudioPage(state: WatchUiState, viewModel: WatchViewModel) {
    PanelGroupLabel("Audio track")
    state.audioTracks.forEach { track ->
        OptionRow(
            label = track.label,
            selected = track.isSelected,
            onClick = { viewModel.selectAudioTrack(track) },
        )
    }
}

@Composable
private fun SubtitlesPage(state: WatchUiState, viewModel: WatchViewModel) {
    PanelGroupLabel("Subtitles")
    val currentSelected = state.textTracks.any { it.isSelected }
    OptionRow(
        label = "Off",
        selected = !currentSelected,
        onClick = { viewModel.selectTextTrack(null) },
    )
    state.textTracks.forEach { track ->
        OptionRow(
            label = track.label,
            selected = track.isSelected,
            onClick = { viewModel.selectTextTrack(track) },
        )
    }
    if (state.textTracks.isEmpty()) {
        EmptyOption("No subtitle tracks for this server.")
    }
}

@Composable
private fun QualityPage(state: WatchUiState, viewModel: WatchViewModel) {
    PanelGroupLabel("Quality")
    state.qualities.forEach { quality ->
        OptionRow(
            label = quality.label,
            selected = quality.isSelected,
            onClick = { viewModel.selectQuality(quality) },
        )
    }
    if (state.qualities.isEmpty()) {
        EmptyOption("Quality options load once the stream starts.")
    }
    HelperText("Auto adapts to your connection speed.")
}

@Composable
private fun MorePage(context: android.content.Context) {
    PanelGroupLabel("Automation")
    ToggleRow(
        label = "Autoplay video",
        description = "Automatically start playing the episode.",
        checked = SettingsState.autoplay,
        onCheckedChange = { SettingsState.setAutoplay(context, it) },
    )
    ToggleRow(
        label = "Autonext episode",
        description = "Play the next episode when this one ends.",
        checked = SettingsState.autonext,
        onCheckedChange = { SettingsState.setAutonext(context, it) },
    )
    ToggleRow(
        label = "Skip intro / outro",
        description = "Automatically skip detected openings and endings.",
        checked = SettingsState.autoskip,
        onCheckedChange = { SettingsState.setAutoskip(context, it) },
    )
    ToggleRow(
        label = "Skip fillers",
        description = "Skip filler episodes when auto-advancing.",
        checked = SettingsState.skipFillers,
        onCheckedChange = { SettingsState.setSkipFillers(context, it) },
    )
    ToggleRow(
        label = "Ambient mode",
        description = "Immersive lighting around the player.",
        checked = SettingsState.ambientMode,
        onCheckedChange = { SettingsState.setAmbientMode(context, it) },
    )
    PanelGroupLabel("Privacy")
    ToggleRow(
        label = "Incognito",
        description = "Watch progress, history, and view counting are paused.",
        checked = SettingsState.incognito,
        onCheckedChange = { SettingsState.setIncognito(context, it) },
    )
}

// ---------------------------------------------------------------------------
//  Caption styles editor (site: caption styles panel)
// ---------------------------------------------------------------------------

@Composable
private fun CaptionStylesPage(context: android.content.Context) {
    var styles by remember { mutableStateOf(SettingsState.captionStyles) }
    val update: (CaptionStyles) -> Unit = { next ->
        styles = next
        SettingsState.setCaptionStyles(context, next)
    }

    PanelGroupLabel("Text")
    SliderCard(
        strongValue = "${(styles.fontSize * 100).toInt()}%",
        value = styles.fontSize,
        valueRange = 0.5f..2f,
        steps = 5,
        onValueChange = { update(styles.copy(fontSize = it)) },
        labelStart = "50%",
        labelEnd = "200%",
    )
    StyleGroupRow(
        label = "Weight",
        value = if (styles.fontWeight >= 700) "Bold" else "Normal",
        onClick = { update(styles.copy(fontWeight = if (styles.fontWeight >= 700) 400 else 700)) },
    )
    StyleGroupRow(
        label = "Shadow",
        value = if (styles.textShadow) "On" else "Off",
        onClick = { update(styles.copy(textShadow = !styles.textShadow)) },
    )
    StyleGroupRow(
        label = "Outline",
        value = if (styles.textBorder) "On" else "Off",
        onClick = { update(styles.copy(textBorder = !styles.textBorder)) },
    )
    PanelGroupLabel("Colors")
    ColorRow("Text color", Color(styles.textColor)) { c ->
        update(styles.copy(textColor = c.toArgb().toLong() and 0xFFFFFFFFL))
    }
    ColorRow("Cue background", Color(styles.textBg)) { c ->
        update(styles.copy(textBg = c.toArgb().toLong() and 0xFFFFFFFFL))
    }
    SliderCard(
        strongValue = "${(styles.textBgOpacity * 100).toInt()}%",
        value = styles.textBgOpacity,
        valueRange = 0f..1f,
        steps = 4,
        onValueChange = { update(styles.copy(textBgOpacity = it)) },
        labelStart = "0%",
        labelEnd = "100%",
    )
    PanelGroupLabel("Reset")
    ResetRow(enabled = styles != CaptionStyles()) {
        styles = CaptionStyles()
        SettingsState.setCaptionStyles(context, CaptionStyles())
    }
}

// ---------------------------------------------------------------------------
//  Panel building blocks (site: setting-row / option-row / toggle-row / slider-card)
// ---------------------------------------------------------------------------

@Composable
private fun PanelGroupLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = WebTextStyles.xs2.copy(
            letterSpacing = 0.4.sp,
            fontWeight = FontWeight.Medium,
        ),
        color = Color(0x6BFFFFFF),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x0FFFFFFF)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Text(
            text = label,
            style = WebTextStyles.sm,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = WebTextStyles.xs,
            color = Color(0x70FFFFFF),
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0x70FFFFFF), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = WebTextStyles.sm,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = LocalAnikageTheme.current.action, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = WebTextStyles.sm, color = Color.White)
            Text(description, style = WebTextStyles.xs, color = Color(0x6BFFFFFF))
        }
        SiteSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Site switch: 2.1rem track, knob slides, action color when on. */
@Composable
private fun SiteSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val trackAlpha by animateFloatAsState(if (checked) 1f else 0f, tween(160))
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (checked) LocalAnikageTheme.current.action else Color(0x2EFFFFFF),
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = if (checked) 16.dp else 2.dp)
                .size(16.dp)
                .background(Color.White, RoundedCornerShape(999.dp)),
        )
    }
}

@Composable
private fun StyleGroupRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = WebTextStyles.sm, color = Color.White, modifier = Modifier.weight(1f))
        Text(value, style = WebTextStyles.xs, color = Color(0x70FFFFFF))
    }
}

@Composable
private fun ColorRow(label: String, color: Color, onPick: (Color) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { picking = !picking }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = WebTextStyles.sm, color = Color.White, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
        if (picking) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Color.White, Color(0xFFFEF08A), Color(0xFF86EFAC), Color(0xFF93C5FD),
                    Color(0xFFF9A8D4), Color(0xFFFCA5A5), Color.Black,
                ).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(c)
                            .clickable {
                                onPick(c)
                                picking = false
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResetRow(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Reset to defaults",
            style = WebTextStyles.sm,
            color = if (enabled) Color(0xD9FFFFFF) else Color(0x52FFFFFF),
        )
    }
}

@Composable
private fun SliderCard(
    strongValue: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    labelStart: String,
    labelEnd: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x0DFFFFFF))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Value",
                style = WebTextStyles.xs,
                color = Color(0x8CFFFFFF),
                modifier = Modifier.weight(1f),
            )
            Text(strongValue, style = WebTextStyles.base.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = Color.White)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = LocalAnikageTheme.current.action,
                inactiveTrackColor = Color(0x24FFFFFF),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(labelStart, style = WebTextStyles.xs, color = Color(0x61FFFFFF))
            Spacer(Modifier.weight(1f))
            Text(labelEnd, style = WebTextStyles.xs, color = Color(0x61FFFFFF))
        }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = WebTextStyles.xs.copy(fontWeight = FontWeight.SemiBold),
        color = if (selected) Color.Black else Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) LocalAnikageTheme.current.action else Color(0x1AFFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun HelperText(text: String) {
    Text(
        text = text,
        style = WebTextStyles.xs,
        color = Color(0x6BFFFFFF),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmptyOption(text: String) {
    Text(
        text = text,
        style = WebTextStyles.xs,
        color = Color(0x73FFFFFF),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

// ---------------------------------------------------------------------------
//  Seek slider — custom drag bar (site: 3.5px rounded track, action color,
//  white 8px thumb, buffered track, tap-to-seek)
// ---------------------------------------------------------------------------

@Composable
internal fun SeekSlider(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAnikageTheme.current
    val duration = if (durationMs > 0) durationMs else 1L
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(-1f) }

    fun fractionToMs(f: Float): Long = (f.coerceIn(0f, 1f) * duration).toLong()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp) // fat hit area
            .pointerInput(durationMs) {
                detectTapGestures(
                    onTap = { offset ->
                        val f = offset.x / size.width.toFloat()
                        onScrubEnd(fractionToMs(f))
                    },
                )
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        scrubbing = true
                        scrubFraction = offset.x / size.width.toFloat()
                        onScrubStart()
                        onScrub(fractionToMs(scrubFraction))
                    },
                    onDragEnd = {
                        scrubbing = false
                        onScrubEnd(fractionToMs(scrubFraction))
                        scrubFraction = -1f
                    },
                    onDragCancel = {
                        scrubbing = false
                        scrubFraction = -1f
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        scrubFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onScrub(fractionToMs(scrubFraction))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            val trackHeight = 3.5.dp.toPx()
            val corner = trackHeight / 2
            val posFraction = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
            val bufFraction = (bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
            // buffered track (site: bg-white/30)
            drawRoundRect(
                color = Color(0x4DFFFFFF),
                topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - trackHeight) / 2),
                size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
            )
            // buffered ahead
            drawRoundRect(
                color = Color(0x8AFFFFFF),
                topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - trackHeight) / 2),
                size = androidx.compose.ui.geometry.Size(size.width * bufFraction, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
            )
            // progress (action color)
            val shown = if (scrubbing && scrubFraction >= 0) scrubFraction else posFraction
            drawRoundRect(
                color = theme.action,
                topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - trackHeight) / 2),
                size = androidx.compose.ui.geometry.Size(size.width * shown, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
            )
            // thumb (site: white, scales on press)
            val thumbScale = if (scrubbing) 1.15f else 1f
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx() * thumbScale,
                center = androidx.compose.ui.geometry.Offset(
                    size.width * shown,
                    size.height / 2,
                ),
            )
        }
    }
}

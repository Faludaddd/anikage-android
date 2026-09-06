package com.anikage.app.core.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.anikage.app.Config
import com.anikage.app.Config.Theme as CTheme
import com.anikage.app.Config.Theme.WebTheme

/**
 * The active Anikage website theme (one of the 11 `[data-theme=...]` sets).
 * Screens read tokens from this — e.g. LocalAnikageTheme.current.action —
 * exactly mirroring the site's CSS variables.
 */
val LocalAnikageTheme = staticCompositionLocalOf<WebTheme> { CTheme.DEFAULT }

/**
 * Global, observable theme key — mirrors the site's localStorage
 * `data-theme` value. [MainActivity] observes it; [SettingsScreen] writes it.
 */
object ThemeState {
    var key: String by mutableStateOf(CTheme.DEFAULT.key)

    /** Call once at startup to restore the persisted selection. */
    fun init(context: Context) {
        key = readThemeKey(context)
    }

    /** Switch theme at runtime + persist (like the site's theme picker). */
    fun set(context: Context, newKey: String) {
        key = newKey
        writeThemeKey(context, newKey)
    }
}

/**
 * App theme. 1:1 port of the Anikage website theme system:
 *  - Dark-only (the site is dark-only).
 *  - The Material scheme is derived from the active WebTheme tokens so every
 *    M3 component (dialogs, switches, sliders…) also matches the site.
 *  - The selected theme persists in SharedPreferences like the site's
 *    localStorage `data-theme` key, and can be switched at runtime.
 */
@Composable
fun AnikageTheme(
    themeKey: String? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val resolvedKey = themeKey ?: ThemeState.key
    val webTheme = remember(resolvedKey) { CTheme.byKey(resolvedKey) }
    // Keep legacy Config.Theme aliases in sync for call sites that still use them.
    remember(webTheme) { CTheme.active = webTheme }

    val colorScheme = darkColorScheme(
        primary = webTheme.action,
        onPrimary = webTheme.actionFg,
        primaryContainer = webTheme.accent.copy(alpha = 0.18f),
        onPrimaryContainer = webTheme.fg,
        secondary = webTheme.accentInfo,
        onSecondary = webTheme.actionFg,
        secondaryContainer = webTheme.accentInfo.copy(alpha = 0.18f),
        onSecondaryContainer = webTheme.fg,
        tertiary = webTheme.aurora2,
        onTertiary = webTheme.fg,
        background = webTheme.surface,
        onBackground = webTheme.fg,
        surface = webTheme.surface,
        onSurface = webTheme.fg,
        surfaceVariant = webTheme.surfaceElevated,
        onSurfaceVariant = webTheme.fgMuted,
        surfaceTint = webTheme.action,
        outline = webTheme.fgMuted.copy(alpha = 0.25f),
        outlineVariant = webTheme.fgMuted.copy(alpha = 0.12f),
        error = webTheme.danger,
        onError = Color.White,
        errorContainer = webTheme.danger.copy(alpha = 0.15f),
        onErrorContainer = Color.White,
        scrim = Color(0xCC000000),
    )

    CompositionLocalProvider(LocalAnikageTheme provides webTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AnikageTypography,
            shapes = AnikageShapes,
            content = content
        )
    }
}

/** Read the persisted theme key ("default" on first launch — like the site). */
fun readThemeKey(context: Context): String {
    return try {
        context.getSharedPreferences("anikage", Context.MODE_PRIVATE)
            .getString(CTheme.PREFS_KEY, null) ?: CTheme.DEFAULT.key
    } catch (e: Exception) {
        CTheme.DEFAULT.key
    }
}

/** Persist a new theme key (mirrors the site's localStorage write). */
fun writeThemeKey(context: Context, key: String) {
    try {
        context.getSharedPreferences("anikage", Context.MODE_PRIVATE)
            .edit().putString(CTheme.PREFS_KEY, key).apply()
    } catch (e: Exception) {
        // Non-fatal — theme falls back next launch.
    }
}

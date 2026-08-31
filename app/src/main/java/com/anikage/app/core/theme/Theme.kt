package com.anikage.app.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.anikage.app.Config
import com.anikage.app.Config.Theme as CTheme

/**
 * App theme. Always dark by default (the design is dark-first).
 */
@Composable
fun AnikageTheme(
    darkTheme: Boolean = CTheme.DEFAULT_DARK_MODE,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme(
        primary = CTheme.primary,
        onPrimary = Color.White,
        primaryContainer = CTheme.primary.copy(alpha = 0.18f),
        onPrimaryContainer = Color.White,
        secondary = CTheme.secondary,
        onSecondary = Color.White,
        secondaryContainer = CTheme.secondary.copy(alpha = 0.18f),
        onSecondaryContainer = Color.White,
        tertiary = CTheme.tertiary,
        onTertiary = Color.White,
        background = CTheme.background,
        onBackground = CTheme.onBackground,
        surface = CTheme.surface,
        onSurface = CTheme.onSurface,
        surfaceVariant = CTheme.surfaceVariant,
        onSurfaceVariant = CTheme.onSurfaceVariant,
        surfaceTint = CTheme.primary,
        outline = CTheme.outline,
        outlineVariant = CTheme.outline.copy(alpha = 0.5f),
        error = CTheme.error,
        onError = CTheme.onError,
        errorContainer = CTheme.error.copy(alpha = 0.15f),
        onErrorContainer = Color.White,
        scrim = CTheme.scrim,
    ) else lightColorScheme(
        primary = CTheme.primary,
        onPrimary = Color.White,
        primaryContainer = CTheme.primary.copy(alpha = 0.15f),
        onPrimaryContainer = Color.Black,
        secondary = CTheme.secondary,
        onSecondary = Color.White,
        secondaryContainer = CTheme.secondary.copy(alpha = 0.15f),
        onSecondaryContainer = Color.Black,
        tertiary = CTheme.tertiary,
        onTertiary = Color.White,
        background = Color(0xFFF8F8F8),
        onBackground = Color(0xFF1A1A1A),
        surface = Color.White,
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFFE8E8E8),
        onSurfaceVariant = Color(0xFF555555),
        outline = Color(0xFFB0B0B0),
        error = CTheme.error,
        onError = Color.White,
        scrim = Color(0x66000000),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AnikageTypography,
        shapes = AnikageShapes,
        content = content
    )
}

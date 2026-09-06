package com.anikage.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * App typography — 1:1 port of the Anikage website type system.
 *
 * The site uses the system font stack:
 *   -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
 *   "Helvetica Neue", "Noto Sans", Arial, sans-serif
 * which resolves to Roboto on Android — so we use [FontFamily.Default],
 * NOT a bundled webfont.
 *
 * Weights (site's custom scale): normal 400 / medium 500 / semibold 560 /
 * bold 620 / extrabold 700 — approximated by the nearest Roboto weights.
 * Line-heights mirror the site's per-size tokens (1.4–1.5 body, 1.3 titles).
 */
private val WebFontFamily: FontFamily = FontFamily.Default

private fun weight(siteWeight: Int): FontWeight = when {
    siteWeight >= 700 -> FontWeight.Bold            // site 700–780 (extrabold/black)
    siteWeight >= 620 -> FontWeight.Bold            // site 620 (bold)
    siteWeight >= 560 -> FontWeight.SemiBold        // site 560 (semibold)
    siteWeight >= 500 -> FontWeight.Medium          // site 500 (medium)
    else -> FontWeight.Normal                       // site 400 (normal)
}

val AnikageTypography = Typography(
    // Display — page hero titles (title-hero on the site: clamp ~26–34px, 780).
    displayLarge = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(780), fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.02).sp),
    displayMedium = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(780), fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.02).sp),
    displaySmall = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(780), fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.015).sp),

    // Headline — info-page h1 (text-3xl, 700, tracking-tight).
    headlineLarge = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(700), fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.02).sp),
    headlineMedium = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(620), fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.015).sp),
    headlineSmall = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(620), fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.015).sp),

    // Title — title-section = text-xl (17sp phone), semibold 560, -0.012em, lh 1.3.
    titleLarge = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(560), fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.125).sp),
    titleMedium = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(560), fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.01).sp),
    titleSmall = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(500), fontSize = 14.sp, lineHeight = 21.sp),

    // Body — text-sm/base: 400, lh 1.5.
    bodyLarge = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(400), fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(400), fontSize = 13.sp, lineHeight = 19.5.sp),
    bodySmall = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(400), fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),

    // Label — text-2xs/xs: 500/600, tight.
    labelLarge = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(560), fontSize = 14.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(500), fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(600), fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.1.sp),
)

/** Text style helper mirroring the site's utility classes for exact parity. */
object WebTextStyles {
    /** text-2xs — badge/pill text (site: 9.5–10px, lh 1.4). */
    val xs2 = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(600), fontSize = 10.sp, lineHeight = 14.sp)

    /** text-xs — metadata rows, card titles (site: 11–12px, lh 1.45). */
    val xs = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(400), fontSize = 11.sp, lineHeight = 16.sp)

    /** text-sm — buttons, descriptions (site: 12.5–14px, lh 1.5). */
    val sm = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(400), fontSize = 13.sp, lineHeight = 19.5.sp)

    /** text-base — nav links, episode titles (site: 14.5–16px, lh 1.5). */
    val base = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(500), fontSize = 14.5f.sp, lineHeight = 21.75.sp)

    /** title-section — section headers (text-xl, 560, -0.012em, lh 1.3). */
    val titleSection = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(560), fontSize = 17.sp, lineHeight = 22.1.sp, letterSpacing = (-0.12).sp)

    /** title-hero — page titles (clamp 26–34px, 780, lh 1.2). */
    val titleHero = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(780), fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.025).sp)
}

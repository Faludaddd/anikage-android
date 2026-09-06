package com.anikage.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 * The site ships TWO type scales:
 *  - base (phone < 768px): fluid clamp() values, e.g.
 *      --text-2xs: clamp(.594rem … .625rem)   →  9.5–10px
 *      --text-xs:  clamp(.6875rem … .75rem)   → 11–12px
 *      --text-sm:  clamp(.781rem … .875rem)   → 12.5–14px
 *      --text-base:clamp(.906rem … 1rem)      → 14.5–16px
 *      --text-xl:  clamp(1.0625rem … 1.25rem) → 17–20px
 *  - >= 768px (tablet / >= 1920px TV): fixed rem values
 *      --text-2xs: .719rem (11.5px)   --text-xs: .875rem (14px)
 *      --text-sm:  1rem (16px)        --text-base: 1.156rem (18.5px)
 *      --text-lg:  1.3125rem (21px)   --text-xl: 1.4375rem (23px)
 *      --text-2xl: 1.75rem (28px)
 *
 * [WebTypeScale] flips between the two scales at the site's 768px CSS
 * breakpoint (≈ 768dp) so text sizes match the site at any device width.
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

/**
 * Tracks whether the device is in the site's >= 768px scale bracket.
 * MainActivity updates this from configuration; every WebTextStyles read
 * observes it, so a size change recomposes all text (like a CSS media query).
 */
object WebTypeScale {
    var wide: Boolean by mutableStateOf(false)
}

// ── Phone scale (site base clamps, evaluated at ~400px viewport) ──────────
private object PhoneScale {
    val xs2 = 10.sp       // clamp(.594rem, … .625rem)
    val xs = 11.5.sp      // clamp(.6875rem, … .75rem)
    val sm = 13.sp        // clamp(.781rem, … .875rem)
    val base = 15.sp      // clamp(.906rem, … 1rem)
    val lg = 16.5.sp      // clamp(.969rem, … 1.125rem)
    val xl = 18.sp        // clamp(1.0625rem, … 1.25rem)
    val xxl = 21.5.sp     // clamp(1.1875rem, … 1.4375rem)
}

// ── Tablet / TV scale (site @media width >= 768px / 1920px coarse) ────────
private object TabletScale {
    val xs2 = 11.5.sp     // .719rem
    val xs = 14.sp        // .875rem
    val sm = 16.sp        // 1rem
    val base = 18.5.sp    // 1.156rem
    val lg = 21.sp        // 1.3125rem
    val xl = 23.sp        // 1.4375rem
    val xxl = 28.sp       // 1.75rem
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

    // Title — title-section = text-xl, semibold 560, -0.012em, lh 1.3.
    titleLarge = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(560), fontSize = 18.sp, lineHeight = 23.4.sp, letterSpacing = (-0.12).sp),
    titleMedium = TextStyle(fontFamily = WebFontFamily, fontWeight = weight(560), fontSize = 16.sp, lineHeight = 20.8.sp, letterSpacing = (-0.01).sp),
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

/**
 * Text style helper mirroring the site's utility classes for exact parity.
 * Each read observes [WebTypeScale.wide] so the scale flips with the layout,
 * exactly like the site's `@media (width>=768px)` overrides.
 */
object WebTextStyles {
    /** text-2xs — badge/pill text (phone 9.5–10px / tablet 11.5px, lh 1.4). */
    val xs2: TextStyle
        get() = TextStyle(
            fontFamily = WebFontFamily, fontWeight = weight(600),
            fontSize = if (WebTypeScale.wide) TabletScale.xs2 else PhoneScale.xs2,
            lineHeight = if (WebTypeScale.wide) 16.1.sp else 14.sp,
        )

    /** text-xs — metadata rows, card titles (phone 11–12 / tablet 14px, lh 1.45). */
    val xs: TextStyle
        get() = TextStyle(
            fontFamily = WebFontFamily, fontWeight = weight(400),
            fontSize = if (WebTypeScale.wide) TabletScale.xs else PhoneScale.xs,
            lineHeight = if (WebTypeScale.wide) 20.3.sp else 16.sp,
        )

    /** text-sm — buttons, descriptions (phone 12.5–14 / tablet 16px, lh 1.5). */
    val sm: TextStyle
        get() = TextStyle(
            fontFamily = WebFontFamily, fontWeight = weight(400),
            fontSize = if (WebTypeScale.wide) TabletScale.sm else PhoneScale.sm,
            lineHeight = if (WebTypeScale.wide) 24.sp else 19.5.sp,
        )

    /** text-base — nav links, episode titles (phone 14.5–16 / tablet 18.5px, lh 1.5). */
    val base: TextStyle
        get() = TextStyle(
            fontFamily = WebFontFamily, fontWeight = weight(500),
            fontSize = if (WebTypeScale.wide) TabletScale.base else PhoneScale.base,
            lineHeight = if (WebTypeScale.wide) 27.75.sp else 22.5.sp,
        )

    /** text-lg (phone 15.5–18 / tablet 21px, lh 1.5). */
    val lg: TextStyle
        get() = TextStyle(
            fontFamily = WebFontFamily, fontWeight = weight(500),
            fontSize = if (WebTypeScale.wide) TabletScale.lg else PhoneScale.lg,
            lineHeight = if (WebTypeScale.wide) 31.5.sp else 24.75.sp,
        )

    /** title-section — section headers (text-xl, 560, -0.012em, lh 1.3). */
    val titleSection: TextStyle
        get() = TextStyle(
            fontFamily = WebFontFamily, fontWeight = weight(560),
            fontSize = if (WebTypeScale.wide) TabletScale.xl else PhoneScale.xl,
            lineHeight = if (WebTypeScale.wide) 29.9.sp else 23.4.sp,
            letterSpacing = (-0.12).sp,
        )

    /** title-hero — page titles (clamp 26–34px, 780, lh 1.2). */
    val titleHero: TextStyle
        get() = TextStyle(
            fontFamily = WebFontFamily, fontWeight = weight(780),
            fontSize = if (WebTypeScale.wide) 30.sp else 24.sp,
            lineHeight = if (WebTypeScale.wide) 36.sp else 28.8.sp,
            letterSpacing = (-0.025).sp,
        )
}

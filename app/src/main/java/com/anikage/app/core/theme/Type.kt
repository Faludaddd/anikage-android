package com.anikage.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.anikage.app.Config
import com.anikage.app.R

/**
 * App typography. Anikage on the web uses Rubik — we ship Rubik in res/font/
 * so the app visually matches the source design.
 */
private val Rubik = FontFamily(
    Font(R.font.rubik_light, FontWeight.Light),
    Font(R.font.rubik_regular, FontWeight.Normal),
    Font(R.font.rubik_medium, FontWeight.Medium),
    Font(R.font.rubik_semi_bold, FontWeight.SemiBold),
    Font(R.font.rubik_bold, FontWeight.Bold),
)

private val Fallback = FontFamily.Default

val AppFontFamily: FontFamily = if (Config.Typography.USE_RUBIK) Rubik else Fallback

val AnikageTypography = Typography(
    displayLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = Config.Typography.displayLarge, lineHeight = Config.Typography.displayLarge * 1.2),
    displayMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = Config.Typography.displayMedium, lineHeight = Config.Typography.displayMedium * 1.2),
    displaySmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = Config.Typography.displaySmall, lineHeight = Config.Typography.displaySmall * 1.2),
    headlineLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = Config.Typography.headlineLarge, lineHeight = Config.Typography.headlineLarge * 1.25),
    headlineMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = Config.Typography.headlineMedium, lineHeight = Config.Typography.headlineMedium * 1.25),
    headlineSmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = Config.Typography.headlineSmall, lineHeight = Config.Typography.headlineSmall * 1.25),
    titleLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = Config.Typography.titleLarge, lineHeight = Config.Typography.titleLarge * 1.3),
    titleMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = Config.Typography.titleMedium, lineHeight = Config.Typography.titleMedium * 1.3),
    titleSmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = Config.Typography.titleSmall, lineHeight = Config.Typography.titleSmall * 1.3),
    bodyLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = Config.Typography.bodyLarge, lineHeight = Config.Typography.bodyLarge * 1.4),
    bodyMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = Config.Typography.bodyMedium, lineHeight = Config.Typography.bodyMedium * 1.4),
    bodySmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = Config.Typography.bodySmall, lineHeight = Config.Typography.bodySmall * 1.4),
    labelLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = Config.Typography.labelLarge, lineHeight = Config.Typography.labelLarge * 1.2),
    labelMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = Config.Typography.labelMedium, lineHeight = Config.Typography.labelMedium * 1.2),
    labelSmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = Config.Typography.labelSmall, lineHeight = Config.Typography.labelSmall * 1.2),
)

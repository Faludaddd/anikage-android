package com.anikage.app.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 1:1 port of the website's radius scale (Tailwind tokens):
 *   sm .25rem(4) · md .375rem(6) · lg .5rem(8) · xl .75rem(12) ·
 *   2xl 1rem(16) · 3xl 1.5rem(24) · 4xl 2rem(32) · pill = full round.
 */
object WebRadius {
    val sm = RoundedCornerShape(4.dp)      // small chips, ep badges
    val md = RoundedCornerShape(6.dp)      // rank badges
    val lg = RoundedCornerShape(8.dp)      // server chips, inputs (rounded-lg)
    val xl = RoundedCornerShape(12.dp)     // covers, buttons (rounded-xl)
    val xl2 = RoundedCornerShape(16.dp)    // cards, panels (rounded-2xl)
    val xl3 = RoundedCornerShape(24.dp)    // sheets (rounded-3xl)
    val xl4 = RoundedCornerShape(28.dp)    // featured banner outer (1.75rem)
    val pill = RoundedCornerShape(50)      // full pills / dots
}

val AnikageShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),     // radius-xl — covers, buttons
    large = RoundedCornerShape(16.dp),      // radius-2xl — cards, panels
    extraLarge = RoundedCornerShape(24.dp), // radius-3xl — sheets
)

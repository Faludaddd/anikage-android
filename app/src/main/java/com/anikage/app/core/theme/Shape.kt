package com.anikage.app.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import com.anikage.app.Config

val AnikageShapes = Shapes(
    extraSmall = RoundedCornerShape(Config.Shape.card / 2),
    small = RoundedCornerShape(Config.Shape.card / 1.5f),
    medium = RoundedCornerShape(Config.Shape.card),
    large = RoundedCornerShape(Config.Shape.sheet),
    extraLarge = RoundedCornerShape(Config.Shape.sheet),
)

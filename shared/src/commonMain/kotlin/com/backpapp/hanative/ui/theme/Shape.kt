package com.backpapp.hanative.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val cardShape = RoundedCornerShape(18.dp)
val chipShape = RoundedCornerShape(8.dp)
val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
val buttonShape = RoundedCornerShape(12.dp)
val badgeShape = RoundedCornerShape(50)

val HaNativeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = chipShape,
    medium = buttonShape,
    large = cardShape,
    extraLarge = sheetShape,
)

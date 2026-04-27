package com.backpapp.hanative.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun HaNativeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HaNativeColorScheme,
        typography = haNativeTypography(),
        shapes = HaNativeShapes,
        content = content,
    )
}

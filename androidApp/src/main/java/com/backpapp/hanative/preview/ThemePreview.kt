package com.backpapp.hanative.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.backpapp.hanative.ui.theme.HaNativeTheme
import com.backpapp.hanative.ui.theme.accent
import com.backpapp.hanative.ui.theme.background
import com.backpapp.hanative.ui.theme.border
import com.backpapp.hanative.ui.theme.cardShape
import com.backpapp.hanative.ui.theme.connected
import com.backpapp.hanative.ui.theme.surface
import com.backpapp.hanative.ui.theme.surfaceActive
import com.backpapp.hanative.ui.theme.surfaceElevated
import com.backpapp.hanative.ui.theme.textPrimary
import com.backpapp.hanative.ui.theme.textSecondary
import com.backpapp.hanative.ui.theme.toggleOff

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun ColorPalettePreview() {
    HaNativeTheme {
        Column(
            modifier = Modifier
                .background(background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("HaNative Color Tokens", color = textPrimary)
            Spacer(Modifier.height(8.dp))
            ColorSwatch("background", background)
            ColorSwatch("surface", surface)
            ColorSwatch("surfaceElevated", surfaceElevated)
            ColorSwatch("surfaceActive", surfaceActive)
            ColorSwatch("accent", accent)
            ColorSwatch("textPrimary", textPrimary)
            ColorSwatch("textSecondary", textSecondary)
            ColorSwatch("connected", connected)
            ColorSwatch("border", border)
            ColorSwatch("toggleOff", toggleOff)
        }
    }
}

@Composable
private fun ColorSwatch(name: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color, cardShape),
        )
        Text(name, color = textPrimary)
    }
}

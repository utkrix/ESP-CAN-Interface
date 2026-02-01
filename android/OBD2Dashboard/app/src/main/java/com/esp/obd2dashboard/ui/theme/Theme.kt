package com.esp.obd2dashboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
        darkColorScheme(
                primary = Color(0xFF00BCD4),
                secondary = Color(0xFF03DAC6),
                tertiary = Color(0xFFFF5722),
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                onPrimary = Color.White,
                onSecondary = Color.Black,
                onBackground = Color.White,
                onSurface = Color.White,
        )

@Composable
fun OBD2DashboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}

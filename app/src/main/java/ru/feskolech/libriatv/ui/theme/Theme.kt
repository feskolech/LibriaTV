package ru.feskolech.libriatv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB32121),
    onPrimary = Color.White,
    background = Color(0xFF101010),
    onBackground = Color.White,
    surface = Color(0xFF181818),
    onSurface = Color.White,
)

@Composable
fun LibriaTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}

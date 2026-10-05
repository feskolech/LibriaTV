package ru.feskolech.libriatv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import ru.feskolech.libriatv.data.repo.AppearanceSettings
import ru.feskolech.libriatv.data.repo.SettingsStore

private val accents = listOf(0xFFB32121, 0xFFE87522, 0xFFE8C547, 0xFF43A858, 0xFF3988D5, 0xFF9362C9)

@Composable
fun LibriaTvTheme(settings: SettingsStore, content: @Composable () -> Unit) {
    val appearance by settings.appearance.collectAsState(initial = AppearanceSettings())
    val baseDensity = LocalDensity.current
    val scale = appearance.scale / 100f
    val colors = darkColorScheme(
        primary = Color(accents[appearance.accent]),
        onPrimary = if (appearance.accent == 2) Color.Black else Color.White,
        background = if (appearance.oled) Color.Black else Color(0xFF101010),
        onBackground = Color.White,
        surface = if (appearance.oled) Color(0xFF0A0A0A) else Color(0xFF181818),
        onSurface = Color.White,
    )
    CompositionLocalProvider(LocalDensity provides Density(baseDensity.density * scale, baseDensity.fontScale)) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

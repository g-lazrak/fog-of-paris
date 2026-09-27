package com.glazrak.fogofparis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Toujours sombre, quel que soit le réglage du téléphone : c'est l'identité
// du jeu (le brouillard, la nuit), et la carte est sombre elle aussi.
private val NightColorScheme = darkColorScheme(
    primary = Night.Gold,
    onPrimary = Night.GoldInk,
    secondary = Night.Teal,
    tertiary = Night.Periwinkle,
    background = Night.Background,
    onBackground = Night.Text,
    surface = Night.Surface,
    onSurface = Night.Text,
    surfaceVariant = Night.SurfaceHigh,
    onSurfaceVariant = Night.TextMuted,
    outline = Night.Border,
    outlineVariant = Night.Border,
)

@Composable
fun FogOfParisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightColorScheme,
        typography = Typography,
        content = content,
    )
}

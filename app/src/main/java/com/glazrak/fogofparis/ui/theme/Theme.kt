package com.glazrak.fogofparis.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun Palette.colorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Gold,
        onPrimary = GoldInk,
        secondary = Teal,
        tertiary = Periwinkle,
        background = Background,
        onBackground = Text,
        surface = Surface,
        onSurface = Text,
        surfaceVariant = SurfaceHigh,
        onSurfaceVariant = TextMuted,
        outline = Border,
        outlineVariant = Border,
    )
}

// Menus sombres ou clairs selon le réglage ; la carte a son propre réglage.
@Composable
fun FogOfParisTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.colorScheme(),
            typography = Typography,
            content = content,
        )
    }
}

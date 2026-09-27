package com.glazrak.fogofparis.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Couleurs des menus. Deux jeux : « Paris la nuit » (maquette validée par le
// propriétaire, 2026-09-27 : bleu nuit, papier ivoire, or des réverbères) et
// « Paris le jour » (papier, encre bleu nuit, or plus sombre pour rester lisible).
@Immutable
data class Palette(
    val isDark: Boolean,
    val Background: Color,
    val Surface: Color,
    val SurfaceHigh: Color,
    val Border: Color,
    val BorderStrong: Color,
    val NavBar: Color,
    val Text: Color,
    val TextSoft: Color,
    val TextMuted: Color,
    val Gold: Color,
    val GoldInk: Color,
    val Teal: Color,
    val Periwinkle: Color,
    val Coral: Color,
    val Orchid: Color,
    val Bronze: Color,
    val Silver: Color,
    val Violet: Color,
    val Amber: Color,
    val Diamond: Color,
    // Mosaïque : quartier jamais visité / entamé sans médaille.
    val Unexplored: Color,
    val Started: Color,
)

val DarkPalette = Palette(
    isDark = true,
    Background = Color(0xFF0B1020),
    Surface = Color(0xFF141A2E),
    SurfaceHigh = Color(0xFF1B2340),
    Border = Color(0xFF262E47),
    BorderStrong = Color(0xFF33406A),
    NavBar = Color(0xFF0E1428),
    Text = Color(0xFFEFE8D8),
    TextSoft = Color(0xFFC9CFDD),
    TextMuted = Color(0xFF9AA3BA),
    Gold = Color(0xFFE9B949),
    GoldInk = Color(0xFF2A1F05),
    Teal = Color(0xFF6FC3B2),
    Periwinkle = Color(0xFF8FA8FF),
    Coral = Color(0xFFE07A5F),
    Orchid = Color(0xFFD49BD8),
    Bronze = Color(0xFFC98546),
    Silver = Color(0xFFB7BDC6),
    Violet = Color(0xFF9C7BE0),
    Amber = Color(0xFFE8A05C),
    Diamond = Color(0xFF7FD6E8),
    Unexplored = Color(0xFF1A2138),
    Started = Color(0xFF34405F),
)

// Les couleurs vives sont assombries : sur fond clair, les teintes pastel
// de la nuit seraient trop pâles pour se lire.
val LightPalette = Palette(
    isDark = false,
    Background = Color(0xFFF4EEE1),
    Surface = Color(0xFFFFFBF3),
    SurfaceHigh = Color(0xFFEFE7D6),
    Border = Color(0xFFE0D5BE),
    BorderStrong = Color(0xFFC9BA98),
    NavBar = Color(0xFFFBF6EB),
    Text = Color(0xFF1C2033),
    TextSoft = Color(0xFF3E4459),
    TextMuted = Color(0xFF6E7389),
    Gold = Color(0xFFB07F12),
    GoldInk = Color(0xFFFFF8E6),
    Teal = Color(0xFF2A8574),
    Periwinkle = Color(0xFF4A62C4),
    Coral = Color(0xFFC0533A),
    Orchid = Color(0xFF9C4FA3),
    Bronze = Color(0xFFA9652B),
    Silver = Color(0xFF7D8591),
    Violet = Color(0xFF7552C4),
    Amber = Color(0xFFC0772E),
    Diamond = Color(0xFF2B98B3),
    Unexplored = Color(0xFFE4DBC7),
    Started = Color(0xFFBDB196),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

// Palette en cours. Garde le nom « Night » pour ne pas réécrire tous les écrans.
// Lisible seulement pendant la composition : dans un dessin (Canvas, drawBehind),
// la lire avant et la passer en variable.
val Night: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

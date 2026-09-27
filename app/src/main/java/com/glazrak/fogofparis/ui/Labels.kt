package com.glazrak.fogofparis.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.domain.Cardinal
import com.glazrak.fogofparis.domain.CollectionSet
import com.glazrak.fogofparis.domain.Medal
import com.glazrak.fogofparis.ui.theme.Night
import com.glazrak.fogofparis.ui.theme.Palette
import java.util.Locale
import kotlin.math.roundToInt

fun Medal.label(context: Context): String = context.getString(
    when (this) {
        Medal.BRONZE -> R.string.medal_bronze
        Medal.SILVER -> R.string.medal_silver
        Medal.GOLD -> R.string.medal_gold
        Medal.MASTERED -> R.string.medal_mastered
    }
)

// Couleur de chaque médaille (mosaïque, barres), selon la palette des menus.
val Medal.color: Color
    @Composable @ReadOnlyComposable get() = colorIn(Night)

fun Medal.colorIn(palette: Palette): Color = when (this) {
    Medal.BRONZE -> palette.Bronze
    Medal.SILVER -> palette.Silver
    Medal.GOLD -> palette.Gold
    Medal.MASTERED -> palette.Violet // violet : distinct des trois métaux
}

@get:DrawableRes
val CollectionSet.icon: Int
    get() = when (this) {
        CollectionSet.BRIDGES -> R.drawable.ic_set_bridges
        CollectionSet.TOWNHALLS -> R.drawable.ic_set_townhalls
        CollectionSet.PASSAGES -> R.drawable.ic_set_passages
        CollectionSet.MONUMENTS -> R.drawable.ic_set_monuments
        CollectionSet.PARKS -> R.drawable.ic_set_parks
        CollectionSet.SQUARES -> R.drawable.ic_set_squares
        CollectionSet.STATIONS -> R.drawable.ic_set_stations
        CollectionSet.TREASURES -> R.drawable.ic_treasure
    }

val CollectionSet.color: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        CollectionSet.BRIDGES -> Night.Periwinkle
        CollectionSet.TOWNHALLS -> Night.Gold
        CollectionSet.PASSAGES -> Night.Orchid
        CollectionSet.MONUMENTS -> Night.Coral
        CollectionSet.PARKS -> Night.Teal
        CollectionSet.SQUARES -> Night.Silver
        CollectionSet.STATIONS -> Night.Amber
        CollectionSet.TREASURES -> Night.Diamond
    }

fun Cardinal.label(context: Context): String = context.getString(
    when (this) {
        Cardinal.N -> R.string.cardinal_n
        Cardinal.NE -> R.string.cardinal_ne
        Cardinal.E -> R.string.cardinal_e
        Cardinal.SE -> R.string.cardinal_se
        Cardinal.S -> R.string.cardinal_s
        Cardinal.SW -> R.string.cardinal_sw
        Cardinal.W -> R.string.cardinal_w
        Cardinal.NW -> R.string.cardinal_nw
    }
)

// "650 m" ou "1,4 km".
fun formatDistance(meters: Double): String =
    if (meters < 1000) "${(meters / 10).roundToInt() * 10} m"
    else String.format(Locale.FRANCE, "%.1f km", meters / 1000)

val Medal.emoji: String
    get() = when (this) {
        Medal.BRONZE -> "🥉"
        Medal.SILVER -> "🥈"
        Medal.GOLD -> "🥇"
        Medal.MASTERED -> "🏆"
    }

// Une cellule = 0,002 % de Paris : 2 décimales sous 10 %, 1 au-delà.
fun formatPercent(percent: Double): String {
    val decimals = if (percent < 10) 2 else 1
    return String.format(Locale.FRANCE, "%.${decimals}f", percent)
}

fun formatPoints(points: Int): String = String.format(Locale.FRANCE, "%,d", points)

// "1er", "2e", … comme sur les plaques parisiennes.
fun arrondissementLabel(context: Context, number: Int): String =
    if (number == 1) context.getString(R.string.arrondissement_first)
    else context.getString(R.string.arrondissement_nth, number)

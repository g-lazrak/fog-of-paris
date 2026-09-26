package com.glazrak.fogofparis.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.domain.Medal
import java.util.Locale

fun Medal.label(context: Context): String = context.getString(
    when (this) {
        Medal.BRONZE -> R.string.medal_bronze
        Medal.SILVER -> R.string.medal_silver
        Medal.GOLD -> R.string.medal_gold
        Medal.MASTERED -> R.string.medal_mastered
    }
)

// Couleur de chaque médaille (barres de progression, pastilles).
val Medal.color: Color
    get() = when (this) {
        Medal.BRONZE -> Color(0xFFCD7F32)
        Medal.SILVER -> Color(0xFF9EA4AA)
        Medal.GOLD -> Color(0xFFE0A800)
        Medal.MASTERED -> Color(0xFF7E57C2) // violet : distinct des trois métaux
    }

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

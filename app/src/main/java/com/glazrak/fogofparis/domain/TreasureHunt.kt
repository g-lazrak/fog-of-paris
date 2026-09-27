package com.glazrak.fogofparis.domain

// Chasse au trésor « chaud / froid » (règles du propriétaire, 2026-09-27) :
// seulement dans le quartier du trésor, la distance et la tendance, jamais la
// direction. « Donner sa langue au chat » montre l'épingle exacte.

enum class Warmth(val maxMeters: Double) {
    BURNING(40.0),
    VERY_HOT(100.0),
    HOT(200.0),
    WARM(400.0),
    COLD(Double.MAX_VALUE),
}

fun warmthFor(distanceMeters: Double): Warmth = Warmth.entries.first { distanceMeters < it.maxMeters }

enum class Trend { CLOSER, FARTHER, STEADY }

// En dessous de 10 m d'écart, c'est du bruit GPS : on ne change pas d'avis.
const val TREND_THRESHOLD_M = 10.0

fun trendOf(previousMeters: Double?, currentMeters: Double): Trend = when {
    previousMeters == null -> Trend.STEADY
    currentMeters < previousMeters - TREND_THRESHOLD_M -> Trend.CLOSER
    currentMeters > previousMeters + TREND_THRESHOLD_M -> Trend.FARTHER
    else -> Trend.STEADY
}

sealed interface HuntStart {
    data object Started : HuntStart
    data object NoPosition : HuntStart
    data class WrongQuartier(val treasureQuartier: Quartier) : HuntStart
}

// On ne peut chasser un trésor que depuis son propre quartier.
fun canStartHunt(treasure: Place, position: GeoPosition?, quartiers: QuartierIndex): HuntStart {
    val treasureQuartier = quartiers.quartierOf(latLonToCell(treasure.position.lat, treasure.position.lon))
        ?: return HuntStart.NoPosition
    if (position == null) return HuntStart.NoPosition
    val here = quartiers.quartierOf(latLonToCell(position.lat, position.lon))
    return if (here?.id == treasureQuartier.id) HuntStart.Started else HuntStart.WrongQuartier(treasureQuartier)
}

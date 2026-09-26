package com.glazrak.fogofparis.domain

import kotlin.math.sqrt

// Une position GPS brute, telle que reçue du téléphone.
data class LocationFix(
    val position: GeoPosition,
    // Rayon d'incertitude en mètres (null si le téléphone ne le donne pas).
    val accuracyMeters: Float?,
    // Vitesse mesurée par le GPS (null si absente).
    val speedMetersPerSecond: Float?,
    val timeMillis: Long,
)

enum class FixRejection { INACCURATE, TOO_FAST, OUTSIDE_PARIS }

const val MAX_ACCURACY_METERS = 30f

// ~12 km/h : la course à pied compte, un bus lancé non. Ce n'est qu'un
// filet de sécurité ; la détection de marche (phase 5) fera le vrai tri.
const val MAX_ON_FOOT_SPEED_MPS = 12.0 / 3.6

// Sans vitesse GPS, on la déduit du déplacement depuis la position précédente,
// mais seulement sur un intervalle assez long pour que le bruit GPS (quelques
// mètres) ne fasse pas croire à une course, et assez court pour ne pas
// comparer à une position d'avant un trou (métro).
private const val MIN_INTERVAL_FOR_IMPLIED_SPEED_MS = 10_000L
private const val MAX_INTERVAL_FOR_IMPLIED_SPEED_MS = 60_000L

// null = position acceptée. Ne révèle jamais de chemin entre deux positions :
// seule la cellule de la position acceptée compte.
fun rejectionReason(fix: LocationFix, previous: LocationFix?, city: CityBoundary): FixRejection? {
    val accuracy = fix.accuracyMeters
    if (accuracy == null || accuracy > MAX_ACCURACY_METERS) return FixRejection.INACCURATE
    val speed = fix.speedMetersPerSecond?.toDouble() ?: impliedSpeed(previous, fix)
    if (speed != null && speed > MAX_ON_FOOT_SPEED_MPS) return FixRejection.TOO_FAST
    if (!city.contains(fix.position)) return FixRejection.OUTSIDE_PARIS
    return null
}

private fun impliedSpeed(previous: LocationFix?, fix: LocationFix): Double? {
    if (previous == null) return null
    val elapsed = fix.timeMillis - previous.timeMillis
    if (elapsed !in MIN_INTERVAL_FOR_IMPLIED_SPEED_MS..MAX_INTERVAL_FOR_IMPLIED_SPEED_MS) return null
    return distanceMeters(previous.position, fix.position) / (elapsed / 1000.0)
}

// Approximation équirectangulaire, cohérente avec la grille ; précise à
// l'échelle de Paris.
fun distanceMeters(a: GeoPosition, b: GeoPosition): Double {
    val dx = (b.lon - a.lon) * METERS_PER_DEGREE_LON_AT_PARIS
    val dy = (b.lat - a.lat) * METERS_PER_DEGREE_LAT
    return sqrt(dx * dx + dy * dy)
}

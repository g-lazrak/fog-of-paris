package com.glazrak.fogofparis.domain

import kotlin.math.atan2
import kotlin.math.roundToInt

// Le lieu non visité le plus proche, pour la carte « Prochain lieu ».
data class PlaceDirection(
    val place: Place,
    val distanceMeters: Double,
    // 0 = nord, 90 = est, dans le sens des aiguilles d'une montre.
    val bearingDegrees: Double,
) {
    val cardinal: Cardinal get() = cardinalOf(bearingDegrees)
}

enum class Cardinal { N, NE, E, SE, S, SW, W, NW }

fun cardinalOf(bearingDegrees: Double): Cardinal {
    val normalized = ((bearingDegrees % 360) + 360) % 360
    return Cardinal.entries[((normalized / 45.0).roundToInt()) % 8]
}

// Cap (en degrés depuis le nord) pour aller de `from` à `to`, en projection
// équirectangulaire comme la grille : précis à l'échelle de Paris.
fun bearingDegrees(from: GeoPosition, to: GeoPosition): Double {
    val east = (to.lon - from.lon) * METERS_PER_DEGREE_LON_AT_PARIS
    val north = (to.lat - from.lat) * METERS_PER_DEGREE_LAT
    val degrees = Math.toDegrees(atan2(east, north))
    return (degrees + 360) % 360
}

// Les trésors cachés ne sont jamais proposés : ils doivent rester une surprise.
fun nearestUnvisited(places: List<Place>, visitedIds: Set<String>, from: GeoPosition): PlaceDirection? =
    places.asSequence()
        .filter { it.id !in visitedIds && !it.set.hidden }
        .map { PlaceDirection(it, distanceMeters(from, it.position), bearingDegrees(from, it.position)) }
        .minByOrNull { it.distanceMeters }

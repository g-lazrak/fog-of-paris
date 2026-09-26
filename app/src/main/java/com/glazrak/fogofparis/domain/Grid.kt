package com.glazrak.fogofparis.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

// Origine de la grille (coin sud-ouest). Ce n'est pas une limite : les points
// plus à l'ouest ou au sud (ex. Bois de Boulogne) donnent des indices négatifs.
const val LAT_MIN = 48.815
const val LON_MIN = 2.250

const val CELL_SIZE_METERS = 50.0
const val METERS_PER_DEGREE_LAT = 111320.0
val METERS_PER_DEGREE_LON_AT_PARIS = METERS_PER_DEGREE_LAT * cos(48.85 * PI / 180.0)
val DELTA_LAT = CELL_SIZE_METERS / METERS_PER_DEGREE_LAT
val DELTA_LON = CELL_SIZE_METERS / METERS_PER_DEGREE_LON_AT_PARIS

data class CellId(val x: Int, val y: Int)

// Rectangle géographique d'une cellule. Type à nous pour que la logique
// ne dépende d'aucune bibliothèque de carte.
data class CellBounds(
    val latNorth: Double,
    val latSouth: Double,
    val lonEast: Double,
    val lonWest: Double,
)

fun latLonToCell(lat: Double, lon: Double): CellId {
    val cellX = floor((lon - LON_MIN) / DELTA_LON).toInt()
    val cellY = floor((lat - LAT_MIN) / DELTA_LAT).toInt()
    return CellId(cellX, cellY)
}

fun cellToBounds(cell: CellId): CellBounds = CellBounds(
    latNorth = LAT_MIN + (cell.y + 1) * DELTA_LAT,
    latSouth = LAT_MIN + cell.y * DELTA_LAT,
    lonEast = LON_MIN + (cell.x + 1) * DELTA_LON,
    lonWest = LON_MIN + cell.x * DELTA_LON,
)

package com.glazrak.fogofparis
import org.osmdroid.util.BoundingBox
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.floor

const val LAT_MIN = 48.815
const val LAT_MAX = 48.905
const val LON_MIN = 2.250
const val LON_MAX = 2.420

const val CELL_SIZE_METERS = 50.0
const val METERS_PER_DEGREE_LAT = 111320.0
val METERS_PER_DEGREE_LON_AT_PARIS = METERS_PER_DEGREE_LAT * cos(48.85 * PI / 180.0)
val DELTA_LAT = CELL_SIZE_METERS / METERS_PER_DEGREE_LAT
val DELTA_LON = CELL_SIZE_METERS / METERS_PER_DEGREE_LON_AT_PARIS
data class CellId(val x: Int, val y: Int)

fun latLonToCell(lat: Double, lon: Double): CellId {
    val cellX = floor((lon - LON_MIN) / DELTA_LON).toInt()
    val cellY = floor((lat - LAT_MIN) / DELTA_LAT).toInt()
    return CellId(cellX, cellY)
}

// Attention à l'ordre :
// BoundingBox(north = lat max, east = lon max, south = lat min, west = lon min)
fun cellToBounds(cell: CellId): BoundingBox {
    val latMax = LAT_MIN + (cell.y + 1) * DELTA_LAT
    val lonMax = LON_MIN + (cell.x + 1) * DELTA_LON
    val latMin = LAT_MIN + cell.y * DELTA_LAT
    val lonMin = LON_MIN + cell.x * DELTA_LON
    return BoundingBox(latMax, lonMax, latMin, lonMin)
}
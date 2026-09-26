package com.glazrak.fogofparis.domain

import kotlin.math.ceil
import kotlin.math.floor

// Rectangle de cellules (bornes incluses), ex. l'emprise de Paris sur la grille.
data class CellRect(val xMin: Int, val yMin: Int, val xMax: Int, val yMax: Int) {
    val width: Int get() = xMax - xMin + 1
    val height: Int get() = yMax - yMin + 1
}

// Suite de cellules d'une même ligne dont le centre est dans la ville.
private data class RowSpan(val xStart: Int, val xEnd: Int)

// Les cellules de la grille qui "appartiennent" à la ville : celles dont le
// centre est à l'intérieur. Sert à compter le % de Paris révélé.
class CityCells private constructor(
    private val rows: Map<Int, List<RowSpan>>,
    val extent: CellRect,
) {
    val totalCells: Int = rows.values.sumOf { spans -> spans.sumOf { it.xEnd - it.xStart + 1 } }

    fun contains(cell: CellId): Boolean =
        rows[cell.y]?.any { cell.x in it.xStart..it.xEnd } ?: false

    fun countInside(cells: Set<CellId>): Int = cells.count { contains(it) }

    companion object {
        // Balayage ligne par ligne : pour chaque rangée de cellules, on cherche où
        // l'horizontale passant par le centre des cellules croise le contour.
        // Bien plus rapide que de tester chaque cellule une par une.
        fun of(city: CityBoundary): CityCells {
            val allPoints = city.rings.flatMap { it.points }
            val south = latLonToCell(allPoints.minOf { it.lat }, allPoints.minOf { it.lon })
            val north = latLonToCell(allPoints.maxOf { it.lat }, allPoints.maxOf { it.lon })
            // Une cellule de marge autour du contour.
            val extent = CellRect(
                xMin = south.x - 1, yMin = south.y - 1,
                xMax = north.x + 1, yMax = north.y + 1,
            )
            val rows = HashMap<Int, List<RowSpan>>()
            for (y in extent.yMin..extent.yMax) {
                val spans = spansForRow(city, y)
                if (spans.isNotEmpty()) rows[y] = spans
            }
            return CityCells(rows, extent)
        }

        private fun spansForRow(city: CityBoundary, y: Int): List<RowSpan> {
            val lat = LAT_MIN + (y + 0.5) * DELTA_LAT
            val crossings = mutableListOf<Double>()
            for (ring in city.rings) {
                ring.points.zipWithNext { a, b ->
                    if ((a.lat > lat) != (b.lat > lat)) {
                        crossings += a.lon + (lat - a.lat) / (b.lat - a.lat) * (b.lon - a.lon)
                    }
                }
            }
            crossings.sort()
            // Règle pair-impair : l'intérieur est entre le 1er et le 2e croisement,
            // le 3e et le 4e, etc.
            return crossings.chunked(2).filter { it.size == 2 }.mapNotNull { (west, east) ->
                // Cellules dont le centre LON_MIN + (x + 0.5) * DELTA_LON est entre west et east.
                val xStart = ceil((west - LON_MIN) / DELTA_LON - 0.5).toInt()
                val xEnd = floor((east - LON_MIN) / DELTA_LON - 0.5).toInt()
                if (xStart <= xEnd) RowSpan(xStart, xEnd) else null
            }
        }
    }
}

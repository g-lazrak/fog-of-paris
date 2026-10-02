package com.glazrak.fogofparis.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Le brouillard est dessiné en formes vectorielles (contours nets à tous les
// zooms) plutôt qu'en petite image agrandie, qui donnait des bords pixelisés
// (propriétaire, 2026-10-03). Les contours suivent le même champ de brouillard
// que FogMask.kt (disques doux autour des cellules). Le dégradé du bord est
// rendu par plusieurs « bandes » empilées : chaque bande couvre tout ce qui est
// au moins aussi brumeux que son niveau.

// Point en unités de cellules : x vers l'est depuis LON_MIN, y vers le nord depuis LAT_MIN.
data class GridPoint(val x: Double, val y: Double) {
    fun toGeo(): GeoPosition = GeoPosition(lat = LAT_MIN + y * DELTA_LAT, lon = LON_MIN + x * DELTA_LON)
}

// Une zone de brouillard : son contour extérieur et les zones révélées qu'il entoure.
// Chaque anneau est fermé (le dernier point répète le premier).
data class FogPolygon(val outer: List<GridPoint>, val holes: List<List<GridPoint>>)

// Tout ce qui est au moins aussi brumeux que `level` (0 = dégagé, 1 = brouillard plein).
data class FogBand(val level: Double, val polygons: List<FogPolygon>)

// Finesse du calcul des contours : 6 points par cellule ≈ un point tous les 8 m.
const val FOG_SAMPLES_PER_CELL = 6

// Niveaux des bandes : assez nombreux pour que le bord paraisse dégradé.
val FOG_BAND_LEVELS = listOf(0.1, 0.3, 0.5, 0.7, 0.9)

// Au-delà de FOG_RADIUS (1,1 cellule) le brouillard est plein : 2 cellules de
// marge garantissent un bord de champ entièrement brumeux, donc des contours fermés.
private const val FIELD_MARGIN_CELLS = 2

fun fogBands(
    visitedCells: Set<CellId>,
    area: CellRect,
    levels: List<Double> = FOG_BAND_LEVELS,
    samplesPerCell: Int = FOG_SAMPLES_PER_CELL,
): List<FogBand> {
    if (visitedCells.isEmpty()) {
        val outer = rectangleRing(area)
        return levels.map { FogBand(it, listOf(FogPolygon(outer, emptyList()))) }
    }
    // Le champ ne couvre que les alentours des cellules visitées : ailleurs, brouillard plein.
    val field = CellRect(
        xMin = visitedCells.minOf { it.x } - FIELD_MARGIN_CELLS,
        yMin = visitedCells.minOf { it.y } - FIELD_MARGIN_CELLS,
        xMax = visitedCells.maxOf { it.x } + FIELD_MARGIN_CELLS,
        yMax = visitedCells.maxOf { it.y } + FIELD_MARGIN_CELLS,
    )
    val outer = rectangleRing(
        CellRect(
            xMin = min(area.xMin, field.xMin),
            yMin = min(area.yMin, field.yMin),
            xMax = max(area.xMax, field.xMax),
            yMax = max(area.yMax, field.yMax),
        )
    )
    val values = fogAlphaMask(visitedCells, field, samplesPerCell)
    val width = field.width * samplesPerCell
    val height = field.height * samplesPerCell
    // Point d'échantillon (colonne, ligne depuis le nord) → coordonnées de grille.
    fun toGrid(p: GridPoint) = GridPoint(
        x = field.xMin + (p.x + 0.5) / samplesPerCell,
        y = field.yMax + 1 - (p.y + 0.5) / samplesPerCell,
    )
    return levels.map { level ->
        val rings = contourRings(values, width, height, threshold = level * FOG_ALPHA)
            .map { ring -> ring.map(::toGrid) }
        FogBand(level, nestRings(rings, outer))
    }
}

private fun rectangleRing(r: CellRect): List<GridPoint> {
    val west = r.xMin.toDouble()
    val east = r.xMax + 1.0
    val south = r.yMin.toDouble()
    val north = r.yMax + 1.0
    return listOf(
        GridPoint(west, south), GridPoint(east, south), GridPoint(east, north),
        GridPoint(west, north), GridPoint(west, south),
    )
}

// « Marching squares » : pour chaque carré de 4 échantillons voisins, un ou deux
// segments séparent les coins dégagés (valeur < seuil) des coins brumeux. Les
// segments se rejoignent sur les arêtes et forment des anneaux fermés.
// Coordonnées renvoyées : x = colonne, y = ligne (vers le sud), en échantillons.
internal fun contourRings(values: IntArray, width: Int, height: Int, threshold: Double): List<List<GridPoint>> {
    fun value(col: Int, row: Int) = values[row * width + col]
    fun isClear(col: Int, row: Int) = value(col, row) < threshold
    // Une arête par point de contour : horizontale de (col, row) à (col+1, row),
    // ou verticale de (col, row) à (col, row+1).
    fun horizontal(col: Int, row: Int) = 2L * (row.toLong() * width + col)
    fun vertical(col: Int, row: Int) = 2L * (row.toLong() * width + col) + 1

    // Chaque point de contour touche exactement deux segments.
    val firstLink = HashMap<Long, Long>()
    val secondLink = HashMap<Long, Long>()
    fun attach(from: Long, to: Long) {
        if (from !in firstLink) firstLink[from] = to else secondLink[from] = to
    }
    fun segment(a: Long, b: Long) {
        attach(a, b)
        attach(b, a)
    }

    for (row in 0 until height - 1) {
        for (col in 0 until width - 1) {
            val topLeft = isClear(col, row)
            val topRight = isClear(col + 1, row)
            val bottomRight = isClear(col + 1, row + 1)
            val bottomLeft = isClear(col, row + 1)
            val case = (if (topLeft) 8 else 0) or (if (topRight) 4 else 0) or
                (if (bottomRight) 2 else 0) or (if (bottomLeft) 1 else 0)
            if (case == 0 || case == 15) continue
            val top = horizontal(col, row)
            val bottom = horizontal(col, row + 1)
            val left = vertical(col, row)
            val right = vertical(col + 1, row)
            when (case) {
                1, 14 -> segment(left, bottom)
                2, 13 -> segment(bottom, right)
                3, 12 -> segment(left, right)
                4, 11 -> segment(top, right)
                6, 9 -> segment(top, bottom)
                7, 8 -> segment(left, top)
                5, 10 -> {
                    // Deux coins opposés dégagés : le centre du carré décide s'ils sont reliés.
                    val centerClear = (value(col, row) + value(col + 1, row) +
                        value(col + 1, row + 1) + value(col, row + 1)) / 4.0 < threshold
                    // Cas 5 : coins NE et SO dégagés ; cas 10 : NO et SE.
                    val cutAroundNorthEastAndSouthWest = (case == 5) != centerClear
                    if (cutAroundNorthEastAndSouthWest) {
                        // Coupe autour des coins NE et SO.
                        segment(top, right)
                        segment(left, bottom)
                    } else {
                        // Coupe autour des coins NO et SE.
                        segment(left, top)
                        segment(bottom, right)
                    }
                }
            }
        }
    }

    // Position du point de contour sur son arête, par interpolation linéaire.
    fun pointOf(edge: Long): GridPoint {
        val index = edge / 2
        val col = (index % width).toInt()
        val row = (index / width).toInt()
        val start = value(col, row)
        return if (edge % 2 == 0L) {
            GridPoint(col + fraction(start, value(col + 1, row), threshold), row.toDouble())
        } else {
            GridPoint(col.toDouble(), row + fraction(start, value(col, row + 1), threshold))
        }
    }

    val used = HashSet<Long>()
    val rings = mutableListOf<List<GridPoint>>()
    for (start in firstLink.keys) {
        if (start in used) continue
        val ring = mutableListOf(pointOf(start))
        used.add(start)
        var previous = start
        var current = firstLink.getValue(start)
        while (current != start) {
            used.add(current)
            ring.add(pointOf(current))
            val next = if (firstLink[current] == previous) secondLink[current] else firstLink[current]
            previous = current
            current = next ?: break
        }
        if (ring.size >= 3) rings.add(ring + ring.first())
    }
    return rings
}

private fun fraction(from: Int, to: Int, threshold: Double): Double =
    if (from == to) 0.5 else ((threshold - from) / (to - from)).coerceIn(0.0, 1.0)

// Les anneaux ne se croisent jamais. Ceux qui ne sont dans aucun autre bordent
// des zones révélées (le bord du champ est brumeux) ; à l'intérieur, on alterne :
// îlot de brouillard, zone révélée dans l'îlot, etc.
private fun nestRings(rings: List<List<GridPoint>>, outer: List<GridPoint>): List<FogPolygon> {
    val boxes = rings.map { BoundingBox.of(it) }
    val areas = rings.map { abs(signedArea(it)) }
    val parent = IntArray(rings.size) { -1 }
    val depth = IntArray(rings.size)
    for (i in rings.indices) {
        val probe = rings[i].first()
        var best = -1
        for (j in rings.indices) {
            if (i == j || !boxes[j].contains(probe) || !contains(rings[j], probe)) continue
            depth[i]++
            if (best == -1 || areas[j] < areas[best]) best = j
        }
        parent[i] = best
    }
    val holesOf = rings.indices.filter { depth[it] % 2 == 0 }.groupBy { parent[it] }
    val islands = rings.indices.filter { depth[it] % 2 == 1 }
    return listOf(FogPolygon(outer, holesOf[-1].orEmpty().map { rings[it] })) +
        islands.map { island -> FogPolygon(rings[island], holesOf[island].orEmpty().map { rings[it] }) }
}

private class BoundingBox(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    fun contains(p: GridPoint) = p.x in minX..maxX && p.y in minY..maxY

    companion object {
        fun of(ring: List<GridPoint>) =
            BoundingBox(ring.minOf { it.x }, ring.minOf { it.y }, ring.maxOf { it.x }, ring.maxOf { it.y })
    }
}

// Test « pair-impair » : on compte les côtés croisés par une demi-droite vers l'est.
private fun contains(ring: List<GridPoint>, p: GridPoint): Boolean {
    var inside = false
    for (i in 0 until ring.size - 1) {
        val a = ring[i]
        val b = ring[i + 1]
        if ((a.y > p.y) != (b.y > p.y) && p.x < a.x + (p.y - a.y) * (b.x - a.x) / (b.y - a.y)) inside = !inside
    }
    return inside
}

private fun signedArea(ring: List<GridPoint>): Double {
    var sum = 0.0
    for (i in 0 until ring.size - 1) sum += ring[i].x * ring[i + 1].y - ring[i + 1].x * ring[i].y
    return sum / 2
}

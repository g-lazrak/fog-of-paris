package com.glazrak.fogofparis.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

// Opacité du brouillard (0 = transparent, 255 = noir complet).
const val FOG_ALPHA = 179 // ≈ 70 %

// Pixels par cellule dans l'image du brouillard (4 → un pixel ≈ 12 m).
const val FOG_PIXELS_PER_CELL = 4

// Chaque cellule révélée dégage un disque doux centré sur elle, au lieu d'un
// carré : entièrement clair jusqu'à CLEAR_RADIUS, puis le brouillard revient
// progressivement jusqu'à FOG_RADIUS (distances en cellules, 1 = 50 m).
// Deux cellules voisines, même en diagonale, se fondent en un trait continu.
// Ce n'est qu'un rendu : le score compte toujours des cellules entières.
const val CLEAR_RADIUS = 0.55
const val FOG_RADIUS = 1.1

// Image du brouillard sur l'emprise donnée : une valeur d'opacité par pixel,
// ligne par ligne en partant du NORD (comme une image), d'ouest en est.
fun fogAlphaMask(
    visitedCells: Set<CellId>,
    extent: CellRect,
    pixelsPerCell: Int = FOG_PIXELS_PER_CELL,
): IntArray {
    val widthPx = extent.width * pixelsPerCell
    val heightPx = extent.height * pixelsPerCell
    val alpha = IntArray(widthPx * heightPx) { FOG_ALPHA }
    for (cell in visitedCells) {
        // Centre de la cellule, en pixels depuis le coin nord-ouest de l'image.
        val centerCol = (cell.x - extent.xMin + 0.5) * pixelsPerCell
        val centerRow = (extent.yMax - cell.y + 0.5) * pixelsPerCell
        val reachPx = FOG_RADIUS * pixelsPerCell
        val colStart = floor(centerCol - reachPx).toInt().coerceAtLeast(0)
        val colEnd = ceil(centerCol + reachPx).toInt().coerceAtMost(widthPx - 1)
        val rowStart = floor(centerRow - reachPx).toInt().coerceAtLeast(0)
        val rowEnd = ceil(centerRow + reachPx).toInt().coerceAtMost(heightPx - 1)
        for (row in rowStart..rowEnd) {
            for (col in colStart..colEnd) {
                // Distance entre le centre du pixel et celui de la cellule, en cellules.
                val dx = (col + 0.5 - centerCol) / pixelsPerCell
                val dy = (row + 0.5 - centerRow) / pixelsPerCell
                val fog = fogAtDistance(sqrt(dx * dx + dy * dy))
                val index = row * widthPx + col
                // Plusieurs disques se chevauchent : on garde le plus clair.
                if (fog < alpha[index]) alpha[index] = fog
            }
        }
    }
    return alpha
}

// 0 près du centre, FOG_ALPHA au-delà de FOG_RADIUS, transition en douceur
// (courbe "smoothstep", sans cassure visible) entre les deux.
fun fogAtDistance(distanceInCells: Double): Int {
    val t = ((distanceInCells - CLEAR_RADIUS) / (FOG_RADIUS - CLEAR_RADIUS)).coerceIn(0.0, 1.0)
    val smooth = t * t * (3 - 2 * t)
    return (smooth * FOG_ALPHA).toInt()
}

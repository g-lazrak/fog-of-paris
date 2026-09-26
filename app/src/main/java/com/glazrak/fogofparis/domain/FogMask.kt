package com.glazrak.fogofparis.domain

// Opacité du brouillard (0 = transparent, 255 = noir complet).
const val FOG_ALPHA = 179 // ≈ 70 %

// Pixels par cellule dans l'image du brouillard. Avec 2, la carte lisse
// l'image en l'agrandissant : chaque zone révélée a un bord doux d'environ
// 25 m au lieu d'un angle net.
const val FOG_PIXELS_PER_CELL = 2

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
        if (cell.x !in extent.xMin..extent.xMax || cell.y !in extent.yMin..extent.yMax) continue
        val left = (cell.x - extent.xMin) * pixelsPerCell
        val top = (extent.yMax - cell.y) * pixelsPerCell
        for (row in top until top + pixelsPerCell) {
            for (col in left until left + pixelsPerCell) {
                alpha[row * widthPx + col] = 0
            }
        }
    }
    return alpha
}

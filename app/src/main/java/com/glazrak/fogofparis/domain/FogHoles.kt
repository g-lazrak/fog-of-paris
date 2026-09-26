package com.glazrak.fogofparis.domain

// Suite de cellules voisines sur une même ligne (y), de xStart à xEnd inclus.
data class CellRun(val y: Int, val xStart: Int, val xEnd: Int)

// Regroupe les cellules visitées en bandes horizontales : un trou par bande
// au lieu d'un trou par cellule, donc beaucoup moins de géométrie à dessiner.
fun mergeIntoRuns(cells: Set<CellId>): List<CellRun> {
    val runs = mutableListOf<CellRun>()
    for ((y, rowCells) in cells.groupBy { it.y }.toSortedMap()) {
        val xs = rowCells.map { it.x }.sorted()
        var start = xs.first()
        var previous = start
        for (x in xs.drop(1)) {
            if (x != previous + 1) {
                runs += CellRun(y = y, xStart = start, xEnd = previous)
                start = x
            }
            previous = x
        }
        runs += CellRun(y = y, xStart = start, xEnd = previous)
    }
    return runs
}

fun runToBounds(run: CellRun): CellBounds {
    val first = cellToBounds(CellId(run.xStart, run.y))
    val last = cellToBounds(CellId(run.xEnd, run.y))
    return first.copy(lonEast = last.lonEast)
}

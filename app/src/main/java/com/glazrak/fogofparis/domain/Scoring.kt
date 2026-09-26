package com.glazrak.fogofparis.domain

import kotlin.math.ceil

// Règles de jeu validées par le propriétaire (voir CLAUDE.md, règle 4).

data class Quartier(
    val id: Int,             // 1 à 80, numéro officiel
    val name: String,
    val arrondissement: Int, // 1 à 20
    val boundary: CityBoundary,
    val cells: CityCells,
)

// Une cellule révélée et le moment de sa première visite.
data class VisitedCell(val cell: CellId, val firstVisitedAt: Long)

enum class Medal(val thresholdPercent: Int) {
    BRONZE(10), SILVER(25), GOLD(50), MASTERED(75),
}

const val POINTS_PER_CELL = 1
const val POINTS_PER_MEDAL = 100

data class QuartierProgress(
    val quartier: Quartier,
    val revealedCells: Int,
    // Date de chaque médaille obtenue, dans l'ordre Bronze → Maîtrisé.
    val medalDates: Map<Medal, Long>,
) {
    val totalCells: Int get() = quartier.cells.totalCells
    val percent: Double get() = if (totalCells == 0) 0.0 else revealedCells * 100.0 / totalCells
    val medal: Medal? get() = medalDates.keys.maxByOrNull { it.thresholdPercent }
}

// Nombre de cellules à révéler pour atteindre une médaille (arrondi au-dessus :
// 10 % de 75 cellules = 7,5 → il en faut 8).
fun cellsNeededFor(medal: Medal, totalCells: Int): Int =
    ceil(medal.thresholdPercent * totalCells / 100.0).toInt()

// Retrouve l'appartenance de chaque cellule de Paris à son quartier (par son centre).
class QuartierIndex(val quartiers: List<Quartier>) {
    private val quartierOfCell: Map<CellId, Quartier> = buildMap {
        for (quartier in quartiers) {
            for (cell in quartier.cells.allCells()) put(cell, quartier)
        }
    }

    fun quartierOf(cell: CellId): Quartier? = quartierOfCell[cell]

    fun progress(visited: List<VisitedCell>): List<QuartierProgress> {
        val timesByQuartier = HashMap<Int, MutableList<Long>>()
        for (visit in visited) {
            val quartier = quartierOf(visit.cell) ?: continue
            timesByQuartier.getOrPut(quartier.id) { mutableListOf() } += visit.firstVisitedAt
        }
        return quartiers.map { quartier ->
            val times = timesByQuartier[quartier.id].orEmpty().sorted()
            QuartierProgress(
                quartier = quartier,
                revealedCells = times.size,
                medalDates = medalDates(times, quartier.cells.totalCells),
            )
        }
    }
}

// Une médaille est gagnée au moment où la n-ième cellule nécessaire a été
// révélée : c'est la date de cette cellule, dans les dates triées.
fun medalDates(sortedVisitTimes: List<Long>, totalCells: Int): Map<Medal, Long> =
    Medal.entries.mapNotNull { medal ->
        val needed = cellsNeededFor(medal, totalCells)
        if (needed in 1..sortedVisitTimes.size) medal to sortedVisitTimes[needed - 1] else null
    }.toMap()

// Points : 1 par cellule révélée dans Paris + 100 par médaille (cumulées :
// un quartier en Or rapporte Bronze + Argent + Or = 300).
fun totalPoints(revealedCellsInParis: Int, progress: List<QuartierProgress>): Int =
    revealedCellsInParis * POINTS_PER_CELL + progress.sumOf { it.medalDates.size } * POINTS_PER_MEDAL

// La médaille qui vient d'être gagnée en passant de `before` à `after`
// cellules révélées, s'il y en a une.
fun newMedal(before: Int, after: Int, totalCells: Int): Medal? =
    Medal.entries.lastOrNull { medal ->
        val needed = cellsNeededFor(medal, totalCells)
        needed in (before + 1)..after
    }

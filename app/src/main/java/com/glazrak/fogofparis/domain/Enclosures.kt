package com.glazrak.fogofparis.domain

// Règle des îlots (propriétaire, 2026-10-03) : l'intérieur d'un pâté de maisons
// est inaccessible, il ne doit pas rester dans le brouillard pour toujours.
// Une poche de brouillard entièrement entourée de cellules parcourues (ou de la
// limite de Paris) se dévoile si aucune voie piétonne publique ne la traverse,
// et si elle ne dépasse pas MAX_ENCLOSED_CELLS. Ses cellules comptent comme
// révélées (%, médailles, points) mais ne valident ni lieu ni trésor : il faut
// y passer en vrai.

// Garde-fou contre un trou dans les données des rues : ~25 ha.
const val MAX_ENCLOSED_CELLS = 100

// Cellules traversées en leur milieu par une voie où l'on peut marcher (rue,
// passage, sentier, escalier ; sans voies privées, autoroutes ni souterrains).
// Données OpenStreetMap, voir tools/walkable/.
class WalkableWays(private val cells: Set<CellId>) {
    fun crosses(cell: CellId): Boolean = cell in cells
}

// Toutes les poches dévoilées par la règle, à partir des cellules parcourues.
fun enclosedPatches(
    walked: Set<CellId>,
    city: CityCells,
    ways: WalkableWays,
    maxCells: Int = MAX_ENCLOSED_CELLS,
): List<Set<CellId>> {
    val patches = mutableListOf<Set<CellId>>()
    val settled = HashSet<CellId>()
    for (cell in city.allCells()) {
        if (cell in walked || cell in settled) continue
        val patch = enclosedPatchFrom(cell, walked, city, ways, maxCells, settled)
        if (patch != null) patches.add(patch)
    }
    return patches
}

// Les poches que la cellule qui vient d'être parcourue a refermées. Seules ses
// voisines peuvent l'être : une poche ne se ferme que par un nouveau « mur ».
fun patchesClosedBy(
    cell: CellId,
    walked: Set<CellId>,
    alreadyRevealed: Set<CellId>,
    city: CityCells,
    ways: WalkableWays,
    maxCells: Int = MAX_ENCLOSED_CELLS,
): List<Set<CellId>> {
    val settled = HashSet<CellId>()
    return cell.sideNeighbours()
        .filter { it !in walked && it !in alreadyRevealed && it !in settled && city.contains(it) }
        .mapNotNull { enclosedPatchFrom(it, walked, city, ways, maxCells, settled) }
}

// Explore la poche de brouillard qui contient `start` (voisins par les côtés :
// deux cellules parcourues en diagonale suffisent à faire un mur). S'arrête dès
// qu'elle est trop grande ou qu'une voie la traverse. Les cellules explorées
// sont ajoutées à `settled` : leur sort est réglé, inutile d'y revenir.
private fun enclosedPatchFrom(
    start: CellId,
    walked: Set<CellId>,
    city: CityCells,
    ways: WalkableWays,
    maxCells: Int,
    settled: MutableSet<CellId>,
): Set<CellId>? {
    val patch = hashSetOf(start)
    val queue = ArrayDeque(listOf(start))
    var enclosed = true
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (ways.crosses(current)) {
            enclosed = false
            break
        }
        for (next in current.sideNeighbours()) {
            // Hors de Paris = mur : la ville s'arrête là.
            if (next in walked || next in patch || !city.contains(next)) continue
            if (next in settled) {
                // Rejoint une poche déjà écartée : celle-ci l'est aussi.
                enclosed = false
                break
            }
            patch.add(next)
            queue.addLast(next)
        }
        if (!enclosed || patch.size > maxCells) {
            enclosed = false
            break
        }
    }
    settled.addAll(patch)
    return if (enclosed) patch else null
}

private fun CellId.sideNeighbours() = listOf(
    CellId(x + 1, y), CellId(x - 1, y), CellId(x, y + 1), CellId(x, y - 1),
)

// Cellules révélées = parcourues + poches dévoilées. Une poche prend la date
// à laquelle elle s'est refermée : la plus récente de ses cellules voisines.
fun withEnclosedCells(visits: List<VisitedCell>, city: CityCells, ways: WalkableWays): List<VisitedCell> {
    val firstVisit = visits.associateTo(HashMap(visits.size)) { it.cell to it.firstVisitedAt }
    val patches = enclosedPatches(firstVisit.keys, city, ways)
    if (patches.isEmpty()) return visits
    return visits + patches.flatMap { patch -> enclosedVisits(patch, firstVisit) }
}

fun enclosedVisits(patch: Set<CellId>, firstVisit: Map<CellId, Long>): List<VisitedCell> {
    val closedAt = patch.maxOf { cell ->
        (-1..1).maxOf { dx -> (-1..1).maxOf { dy -> firstVisit[CellId(cell.x + dx, cell.y + dy)] ?: 0L } }
    }
    return patch.map { VisitedCell(it, closedAt) }
}

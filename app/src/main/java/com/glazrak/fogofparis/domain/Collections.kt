package com.glazrak.fogofparis.domain

import kotlin.math.ceil

// Collections de lieux à visiter (règles validées par le propriétaire, voir CLAUDE.md).

// `hidden` : série secrète (trésors), jamais montrée sur la carte ni proposée
// comme destination tant que le lieu n'est pas trouvé. `pointsPerPlace` fixe.
enum class CollectionSet(val emoji: String, val hidden: Boolean = false, val pointsPerPlace: Int = POINTS_PER_PLACE) {
    BRIDGES("🌉"),
    TOWNHALLS("🏛️"),
    PASSAGES("🛍️"),
    MONUMENTS("🗼"),
    PARKS("🌳"),
    SQUARES("⛲"),
    STATIONS("🚉"),
    // Un trésor caché par quartier (80), avec un indice. Pas de bonus de série.
    TREASURES("💎", hidden = true, pointsPerPlace = POINTS_PER_TREASURE),
}

data class Place(
    val id: String,
    val name: String,
    val set: CollectionSet,
    val position: GeoPosition,
    // Distance à laquelle il faut passer pour que le lieu compte. Petite pour
    // un pont (il faut le traverser), grande pour une place à rond-point où
    // les trottoirs sont loin du centre (Étoile, Concorde…).
    val radiusMeters: Double = DEFAULT_PLACE_RADIUS_M,
    // Indice affiché tant qu'un trésor n'est pas trouvé.
    val hint: String? = null,
    // Petite histoire du lieu, montrée une fois trouvé (trésors seulement).
    val story: String? = null,
    // Titre de l'article Wikipédia en français, s'il en existe un.
    val wikiTitle: String? = null,
)

const val DEFAULT_PLACE_RADIUS_M = 75.0
const val POINTS_PER_PLACE = 50
const val POINTS_PER_TREASURE = 150
const val POINTS_PER_COMPLETED_SET = 500

// Les cellules dont le centre est à moins de `radiusMeters` du lieu : en
// révéler une seule suffit pour que le lieu compte comme visité.
fun cellsNear(place: Place): List<CellId> {
    val center = latLonToCell(place.position.lat, place.position.lon)
    val reach = ceil(place.radiusMeters / CELL_SIZE_METERS).toInt() + 1
    return (-reach..reach).flatMap { dx ->
        (-reach..reach).map { dy -> CellId(center.x + dx, center.y + dy) }
    }.filter { cell ->
        val bounds = cellToBounds(cell)
        val cellCenter = GeoPosition(
            lat = (bounds.latNorth + bounds.latSouth) / 2,
            lon = (bounds.lonEast + bounds.lonWest) / 2,
        )
        // La cellule du lieu compte toujours, même avec un tout petit rayon.
        cell == center || distanceMeters(place.position, cellCenter) <= place.radiusMeters
    }
}

fun isVisited(place: Place, visitedCells: Set<CellId>): Boolean =
    cellsNear(place).any { it in visitedCells }

data class CollectionProgress(val set: CollectionSet, val places: List<Place>, val visitedIds: Set<String>) {
    val visitedCount: Int get() = visitedIds.size
    val isComplete: Boolean get() = places.isNotEmpty() && visitedCount == places.size
}

fun collectionProgress(places: List<Place>, visitedCells: Set<CellId>): List<CollectionProgress> {
    val bySet = places.groupBy { it.set }
    return CollectionSet.entries.mapNotNull { set ->
        val setPlaces = bySet[set] ?: return@mapNotNull null
        CollectionProgress(
            set = set,
            places = setPlaces,
            visitedIds = setPlaces.filter { isVisited(it, visitedCells) }.mapTo(HashSet()) { it.id },
        )
    }
}

fun collectionPoints(progress: List<CollectionProgress>): Int =
    progress.sumOf { it.visitedCount * it.set.pointsPerPlace + completionBonus(it) }

// +500 par série complète, sauf les trésors (règle du propriétaire : pas de bonus de série).
fun completionBonus(progress: CollectionProgress): Int =
    if (progress.isComplete && !progress.set.hidden) POINTS_PER_COMPLETED_SET else 0

// Les lieux qu'on peut montrer ou proposer : tous, sauf les trésors pas encore trouvés.
fun isRevealedPlace(place: Place, visitedIds: Set<String>): Boolean =
    !place.set.hidden || place.id in visitedIds

// Date de découverte de chaque lieu visité : la première visite d'une des
// cellules qui le valident. Sert à la liste « Derniers lieux découverts ».
fun placeDiscoveryTimes(places: List<Place>, firstVisitByCell: Map<CellId, Long>): Map<String, Long> =
    places.mapNotNull { place ->
        cellsNear(place).mapNotNull { firstVisitByCell[it] }.minOrNull()?.let { place.id to it }
    }.toMap()

// Retrouve vite les lieux proches d'une cellule qui vient d'être révélée.
class PlaceIndex(val places: List<Place>) {
    private val placesNearCell: Map<CellId, List<Place>> = buildMap<CellId, MutableList<Place>> {
        for (place in places) {
            for (cell in cellsNear(place)) getOrPut(cell) { mutableListOf() } += place
        }
    }

    fun placesNear(cell: CellId): List<Place> = placesNearCell[cell].orEmpty()
}

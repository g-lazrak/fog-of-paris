package com.glazrak.fogofparis.domain

// Anneau = contour fermé (premier point == dernier point), comme en GeoJSON.
data class Ring(val points: List<GeoPosition>)

// Limite de la ville : un ou plusieurs anneaux. Un point est dedans s'il est
// entouré par un nombre impair d'anneaux, ce qui gère aussi d'éventuelles enclaves.
class CityBoundary(val rings: List<Ring>) {

    fun contains(position: GeoPosition): Boolean =
        rings.count { ringContains(it, position) } % 2 == 1

    companion object {
        // Les données sources ont de minuscules décalages entre voisins
        // (ex. ~3 m le long de l'avenue de Suffren, entre le 7e et le 15e) qui
        // laissent de fins interstices. Tout anneau plus petit est ignoré.
        const val MIN_RING_AREA_M2 = 10_000.0

        // Construit le contour extérieur d'un ensemble de zones jointives
        // (ex. les 20 arrondissements) : on retire chaque bord partagé par
        // deux zones, il ne reste que le pourtour de la ville.
        fun fromAdjacentAreas(areas: List<Ring>): CityBoundary {
            val edgeCounts = HashMap<Edge, Int>()
            for (area in areas) {
                for (edge in edgesOf(area)) {
                    val key = edge.undirected()
                    edgeCounts[key] = (edgeCounts[key] ?: 0) + 1
                }
            }
            val outerEdges = areas.flatMap { edgesOf(it) }
                .filter { edgeCounts[it.undirected()] == 1 }
            val rings = chainIntoRings(outerEdges).filter { areaInSquareMeters(it) >= MIN_RING_AREA_M2 }
            return CityBoundary(rings)
        }
    }
}

// Formule du lacet (shoelace) en projection équirectangulaire, comme la grille.
fun areaInSquareMeters(ring: Ring): Double {
    val doubleArea = ring.points.zipWithNext { a, b ->
        (a.lon * METERS_PER_DEGREE_LON_AT_PARIS) * (b.lat * METERS_PER_DEGREE_LAT) -
            (b.lon * METERS_PER_DEGREE_LON_AT_PARIS) * (a.lat * METERS_PER_DEGREE_LAT)
    }.sum()
    return kotlin.math.abs(doubleArea) / 2.0
}

// Test du rayon : on compte combien de côtés un rayon horizontal partant
// du point croise. Impair = dedans. Suffisant à l'échelle de Paris (degrés ≈ plan).
private fun ringContains(ring: Ring, position: GeoPosition): Boolean {
    var inside = false
    val points = ring.points
    var j = points.lastIndex
    for (i in points.indices) {
        val a = points[i]
        val b = points[j]
        if ((a.lat > position.lat) != (b.lat > position.lat)) {
            val crossingLon = a.lon + (position.lat - a.lat) / (b.lat - a.lat) * (b.lon - a.lon)
            if (position.lon < crossingLon) inside = !inside
        }
        j = i
    }
    return inside
}

private data class Edge(val from: GeoPosition, val to: GeoPosition) {
    // Même clé quel que soit le sens de parcours (deux voisins parcourent
    // leur frontière commune en sens opposés).
    fun undirected(): Edge = if (compare(from, to) <= 0) this else Edge(to, from)
}

private fun compare(a: GeoPosition, b: GeoPosition): Int =
    compareValuesBy(a, b, { it.lat }, { it.lon })

private fun edgesOf(ring: Ring): List<Edge> =
    ring.points.zipWithNext { a, b -> Edge(a, b) }.filter { it.from != it.to }

private fun chainIntoRings(edges: List<Edge>): List<Ring> {
    val remaining = edges.groupBy { it.from }.mapValues { it.value.toMutableList() }.toMutableMap()
    val rings = mutableListOf<Ring>()
    while (remaining.isNotEmpty()) {
        val start = remaining.keys.first()
        val points = mutableListOf(start)
        var current = start
        while (true) {
            val outgoing = remaining[current] ?: break
            val edge = outgoing.removeAt(outgoing.lastIndex)
            if (outgoing.isEmpty()) remaining.remove(current)
            points += edge.to
            current = edge.to
            if (current == start) break
        }
        rings += Ring(points)
    }
    return rings
}

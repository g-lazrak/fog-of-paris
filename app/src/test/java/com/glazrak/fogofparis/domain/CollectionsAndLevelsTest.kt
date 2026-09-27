package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsAndLevelsTest {

    private val pontNeuf = Place("pont-neuf", "Pont Neuf", CollectionSet.BRIDGES, GeoPosition(48.8575, 2.3413))
    private val pontDesArts = Place("pont-des-arts", "Pont des Arts", CollectionSet.BRIDGES, GeoPosition(48.8583, 2.3375))
    private val pantheon = Place("pantheon", "Panthéon", CollectionSet.MONUMENTS, GeoPosition(48.8462, 2.3464))

    private fun cellOf(place: Place) = latLonToCell(place.position.lat, place.position.lon)

    @Test
    fun place_is_visited_from_its_own_cell_or_a_nearby_one_only() {
        val c = cellOf(pontNeuf) // rayon par défaut 75 m
        assertTrue(isVisited(pontNeuf, setOf(c)))
        assertTrue(isVisited(pontNeuf, setOf(CellId(c.x + 1, c.y))))
        assertFalse(isVisited(pontNeuf, setOf(CellId(c.x + 3, c.y))))
    }

    @Test
    fun larger_radius_counts_cells_further_away() {
        val etoile = Place("etoile", "Place Charles-de-Gaulle", CollectionSet.SQUARES, GeoPosition(48.8738, 2.2950), 150.0)
        val c = cellOf(etoile)
        // ~130 m : le trottoir autour de l'Arc de Triomphe.
        assertTrue(isVisited(etoile, setOf(CellId(c.x + 2, c.y + 1))))
        assertFalse(isVisited(etoile, setOf(CellId(c.x + 4, c.y))))
    }

    @Test
    fun tiny_radius_still_counts_the_place_own_cell() {
        val small = pontNeuf.copy(radiusMeters = 1.0)
        assertEquals(listOf(cellOf(small)), cellsNear(small))
    }

    @Test
    fun collection_points_count_places_and_completed_sets() {
        val visited = setOf(cellOf(pontNeuf), cellOf(pontDesArts))
        val progress = collectionProgress(listOf(pontNeuf, pontDesArts, pantheon), visited)
        val bridges = progress.first { it.set == CollectionSet.BRIDGES }
        assertTrue(bridges.isComplete)
        assertEquals(0, progress.first { it.set == CollectionSet.MONUMENTS }.visitedCount)
        // 2 lieux × 50 + série des ponts complète 500.
        assertEquals(2 * POINTS_PER_PLACE + POINTS_PER_COMPLETED_SET, collectionPoints(progress))
    }

    @Test
    fun place_index_finds_places_around_a_cell() {
        val index = PlaceIndex(listOf(pontNeuf, pantheon))
        assertEquals(listOf(pontNeuf), index.placesNear(cellOf(pontNeuf)))
        assertEquals(emptyList<Place>(), index.placesNear(CellId(0, 0)))
    }

    @Test
    fun levels_follow_the_owner_order_and_thresholds() {
        assertEquals("Badaud", levelFor(0).level.title)
        assertEquals("Badaud", levelFor(499).level.title)
        assertEquals("Promeneur", levelFor(500).level.title)
        assertEquals("Touriste", levelFor(3_500).level.title)
        assertEquals("Baron Haussmann", levelFor(1_000_000).level.title)
        assertNull(levelFor(1_000_000).next)
        assertEquals(9, LEVELS.size)
    }

    @Test
    fun fraction_to_next_level_is_between_zero_and_one() {
        assertEquals(0.5f, levelFor(250).fractionToNext, 0.001f) // 250 / 500
        assertEquals(1f, levelFor(50_000).fractionToNext, 0.001f)
    }
}

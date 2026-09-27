package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompassTest {

    private val here = GeoPosition(lat = 48.8566, lon = 2.3522) // Hôtel de Ville
    private fun place(id: String, lat: Double, lon: Double) =
        Place(id, id, CollectionSet.MONUMENTS, GeoPosition(lat, lon))

    @Test
    fun bearing_points_to_the_right_cardinal_direction() {
        assertEquals(Cardinal.N, cardinalOf(bearingDegrees(here, GeoPosition(48.8666, 2.3522))))
        assertEquals(Cardinal.E, cardinalOf(bearingDegrees(here, GeoPosition(48.8566, 2.3722))))
        assertEquals(Cardinal.S, cardinalOf(bearingDegrees(here, GeoPosition(48.8466, 2.3522))))
        assertEquals(Cardinal.W, cardinalOf(bearingDegrees(here, GeoPosition(48.8566, 2.3322))))
        assertEquals(Cardinal.NE, cardinalOf(45.0))
        assertEquals(Cardinal.N, cardinalOf(350.0))
    }

    @Test
    fun nearest_unvisited_place_skips_visited_ones() {
        val close = place("close", 48.8570, 2.3522)
        val far = place("far", 48.8700, 2.3522)
        val result = nearestUnvisited(listOf(close, far), visitedIds = setOf("close"), from = here)
        assertEquals("far", result?.place?.id)
        assertEquals(1490.0, result!!.distanceMeters, 5.0)
    }

    @Test
    fun no_place_left_gives_nothing() {
        val only = place("only", 48.8570, 2.3522)
        assertNull(nearestUnvisited(listOf(only), setOf("only"), here))
    }
}

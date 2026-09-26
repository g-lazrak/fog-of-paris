package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CityBoundaryTest {

    private fun p(lon: Double, lat: Double) = GeoPosition(lat = lat, lon = lon)

    private fun square(west: Double, south: Double, size: Double) = Ring(
        listOf(
            p(west, south), p(west + size, south), p(west + size, south + size),
            p(west, south + size), p(west, south),
        )
    )

    @Test
    fun point_in_square_is_inside_and_outside_point_is_not() {
        val boundary = CityBoundary(listOf(square(west = 0.0, south = 0.0, size = 1.0)))
        assertTrue(boundary.contains(p(0.5, 0.5)))
        assertFalse(boundary.contains(p(1.5, 0.5)))
    }

    @Test
    fun ring_inside_ring_acts_as_a_hole() {
        val boundary = CityBoundary(
            listOf(square(west = 0.0, south = 0.0, size = 10.0), square(west = 4.0, south = 4.0, size = 2.0))
        )
        assertTrue(boundary.contains(p(1.0, 1.0)))
        assertFalse(boundary.contains(p(5.0, 5.0)))
    }

    @Test
    fun two_adjacent_squares_merge_into_one_outline() {
        val left = square(west = 0.0, south = 0.0, size = 1.0)
        val right = square(west = 1.0, south = 0.0, size = 1.0)

        val boundary = CityBoundary.fromAdjacentAreas(listOf(left, right))

        assertEquals(1, boundary.rings.size)
        // 6 coins distincts + le point de fermeture.
        assertEquals(7, boundary.rings.single().points.size)
        assertTrue(boundary.contains(p(0.5, 0.5)))
        assertTrue(boundary.contains(p(1.5, 0.5)))
        assertFalse(boundary.contains(p(2.5, 0.5)))
    }

    @Test
    fun area_of_a_50m_cell_is_2500_square_meters() {
        val bounds = cellToBounds(CellId(0, 0))
        val cell = Ring(
            listOf(
                p(bounds.lonWest, bounds.latSouth), p(bounds.lonEast, bounds.latSouth),
                p(bounds.lonEast, bounds.latNorth), p(bounds.lonWest, bounds.latNorth),
                p(bounds.lonWest, bounds.latSouth),
            )
        )
        assertEquals(2500.0, areaInSquareMeters(cell), 0.01)
    }
}

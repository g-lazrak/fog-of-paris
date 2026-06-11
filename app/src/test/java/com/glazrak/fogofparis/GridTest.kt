package com.glazrak.fogofparis

import org.junit.Assert.assertEquals
import org.junit.Test

class GridTest {

    @Test
    fun origin_maps_to_cell_zero() {
        // 1. Préparer / appeler
        val resultat = latLonToCell(LAT_MIN, LON_MIN)
        // 2. Vérifier
        assertEquals(CellId(0, 0), resultat)
    }

    @Test
    fun point_inside_first_cell_still_maps_to_zero() {
        val resultat = latLonToCell(LAT_MIN + DELTA_LAT/2, LON_MIN + DELTA_LON/2)
        assertEquals(CellId(0,0), resultat)
    }

    @Test
    fun bounds_of_cell_contains_its_own_center() {
        val boxTest = cellToBounds(CellId(10, 20))
        val lat = (boxTest.latNorth + boxTest.latSouth) / 2
        val lon = (boxTest.lonEast + boxTest.lonWest) / 2
        val resultat = latLonToCell(lat, lon)
        assertEquals(CellId(10,20), resultat)
    }
}
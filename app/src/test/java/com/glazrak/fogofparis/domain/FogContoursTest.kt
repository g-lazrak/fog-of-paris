package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class FogContoursTest {

    private val area = CellRect(xMin = 0, yMin = 0, xMax = 40, yMax = 40)

    private fun bandAt(level: Double, cells: Set<CellId>) = fogBands(cells, area, levels = listOf(level)).single()

    @Test
    fun without_visits_the_fog_is_one_plain_rectangle() {
        val bands = fogBands(emptySet(), area)
        assertEquals(FOG_BAND_LEVELS.size, bands.size)
        bands.forEach { band ->
            val polygon = band.polygons.single()
            assertTrue(polygon.holes.isEmpty())
            assertEquals(5, polygon.outer.size)
        }
    }

    @Test
    fun one_cell_opens_a_round_hole_of_the_expected_size() {
        val polygon = bandAt(0.5, setOf(CellId(20, 20))).polygons.single()
        val hole = polygon.holes.single()
        // Fog reaches half strength halfway between CLEAR_RADIUS and FOG_RADIUS (smoothstep).
        val expected = (CLEAR_RADIUS + FOG_RADIUS) / 2
        hole.forEach { p ->
            val distance = hypot(p.x - 20.5, p.y - 20.5)
            assertEquals(expected, distance, 0.05)
        }
        // Round, not a square: plenty of points.
        assertTrue(hole.size > 16)
        assertEquals(hole.first(), hole.last())
    }

    @Test
    fun distant_cells_open_separate_holes_and_neighbours_merge() {
        assertEquals(2, bandAt(0.5, setOf(CellId(5, 5), CellId(30, 30))).polygons.single().holes.size)
        assertEquals(1, bandAt(0.5, setOf(CellId(5, 5), CellId(6, 5), CellId(7, 6))).polygons.single().holes.size)
    }

    @Test
    fun a_walked_loop_leaves_a_fog_island_in_the_middle() {
        // The 9×9 outline of a block: its centre is 4 cells from any street.
        val loop = buildSet {
            for (i in 10..18) {
                add(CellId(i, 10)); add(CellId(i, 18)); add(CellId(10, i)); add(CellId(18, i))
            }
        }
        val polygons = bandAt(0.5, loop).polygons
        assertEquals(2, polygons.size)
        // Main fog: one hole (the loop and the outside of the island).
        assertEquals(1, polygons[0].holes.size)
        // The island itself, around the block's centre.
        val island = polygons[1]
        assertTrue(island.holes.isEmpty())
        assertTrue(island.outer.all { it.x in 11.0..18.0 && it.y in 11.0..18.0 })
    }

    @Test
    fun higher_levels_open_wider_holes() {
        val cell = setOf(CellId(20, 20))
        fun radius(level: Double) = bandAt(level, cell).polygons.single().holes.single()
            .map { hypot(it.x - 20.5, it.y - 20.5) }.average()
        assertTrue(radius(0.1) < radius(0.5))
        assertTrue(radius(0.5) < radius(0.9))
    }

    @Test
    fun grid_points_convert_back_to_the_cell_corner() {
        val corner = GridPoint(x = 3.0, y = 7.0).toGeo()
        val bounds = cellToBounds(CellId(3, 7))
        assertEquals(bounds.lonWest, corner.lon, 1e-9)
        assertEquals(bounds.latSouth, corner.lat, 1e-9)
    }
}

package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FogMaskTest {

    private val extent = CellRect(xMin = 10, yMin = 20, xMax = 12, yMax = 22) // 3 x 3 cellules

    @Test
    fun no_visited_cells_means_fog_everywhere() {
        val mask = fogAlphaMask(emptySet(), extent, pixelsPerCell = 2)
        assertEquals(36, mask.size)
        assertEquals(setOf(FOG_ALPHA), mask.toSet())
    }

    @Test
    fun visited_cell_clears_its_pixels_with_north_at_the_top() {
        // Cellule en haut à gauche de l'emprise : x le plus petit, y le plus grand.
        val mask = fogAlphaMask(setOf(CellId(10, 22)), extent, pixelsPerCell = 2)
        val width = 6
        val cleared = mask.indices.filter { mask[it] == 0 }.map { it % width to it / width }
        assertEquals(listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), cleared)
    }

    @Test
    fun cells_outside_extent_are_ignored() {
        val mask = fogAlphaMask(setOf(CellId(99, 99)), extent, pixelsPerCell = 2)
        assertEquals(setOf(FOG_ALPHA), mask.toSet())
    }
}

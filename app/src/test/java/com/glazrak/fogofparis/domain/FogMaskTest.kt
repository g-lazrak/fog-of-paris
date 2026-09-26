package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FogMaskTest {

    private val extent = CellRect(xMin = 10, yMin = 20, xMax = 14, yMax = 24) // 5 x 5 cellules
    private val ppc = 4
    private val widthPx = 5 * ppc

    // Opacité au centre de la cellule (x, y), avec le nord en haut de l'image.
    private fun alphaAtCellCenter(mask: IntArray, x: Int, y: Int): Int {
        val col = (x - extent.xMin) * ppc + ppc / 2
        val row = (extent.yMax - y) * ppc + ppc / 2
        return mask[row * widthPx + col]
    }

    @Test
    fun no_visited_cells_means_fog_everywhere() {
        val mask = fogAlphaMask(emptySet(), extent, ppc)
        assertEquals(25 * ppc * ppc, mask.size)
        assertEquals(setOf(FOG_ALPHA), mask.toSet())
    }

    @Test
    fun visited_cell_is_clear_at_its_center_and_foggy_two_cells_away() {
        val mask = fogAlphaMask(setOf(CellId(12, 22)), extent, ppc)
        assertEquals(0, alphaAtCellCenter(mask, 12, 22))
        assertEquals(FOG_ALPHA, alphaAtCellCenter(mask, 14, 22))
        // Le nord est en haut : la cellule y = 24 est sur la première rangée de pixels.
        assertEquals(FOG_ALPHA, alphaAtCellCenter(mask, 12, 24))
    }

    @Test
    fun diagonal_neighbours_blend_into_a_continuous_trail() {
        val mask = fogAlphaMask(setOf(CellId(11, 21), CellId(12, 22)), extent, ppc)
        // Point de contact des deux cellules : le coin commun, pixel juste à côté.
        val col = (12 - extent.xMin) * ppc
        val row = (extent.yMax - 22 + 1) * ppc
        assertTrue("Trail should stay mostly clear between diagonal cells", mask[row * widthPx + col] < FOG_ALPHA / 3)
    }

    @Test
    fun fog_rises_smoothly_with_distance() {
        assertEquals(0, fogAtDistance(0.0))
        assertEquals(0, fogAtDistance(CLEAR_RADIUS))
        assertEquals(FOG_ALPHA, fogAtDistance(FOG_RADIUS))
        val middle = fogAtDistance((CLEAR_RADIUS + FOG_RADIUS) / 2)
        assertTrue(middle in (FOG_ALPHA / 2 - 2)..(FOG_ALPHA / 2 + 2))
    }

    @Test
    fun cells_outside_extent_are_ignored() {
        val mask = fogAlphaMask(setOf(CellId(99, 99)), extent, ppc)
        assertEquals(setOf(FOG_ALPHA), mask.toSet())
    }
}

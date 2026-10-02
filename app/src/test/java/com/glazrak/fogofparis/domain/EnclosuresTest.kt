package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnclosuresTest {

    // A square test city covering cells 0..29 in both directions.
    private val city = CityCells.of(
        CityBoundary(
            listOf(
                Ring(
                    listOf(
                        GeoPosition(LAT_MIN, LON_MIN),
                        GeoPosition(LAT_MIN, LON_MIN + 30 * DELTA_LON),
                        GeoPosition(LAT_MIN + 30 * DELTA_LAT, LON_MIN + 30 * DELTA_LON),
                        GeoPosition(LAT_MIN + 30 * DELTA_LAT, LON_MIN),
                        GeoPosition(LAT_MIN, LON_MIN),
                    )
                )
            )
        )
    )
    private val noWays = WalkableWays(emptySet())

    // The streets around a block whose inside is xs × ys.
    private fun walkAround(xs: IntRange, ys: IntRange, cutCorners: Boolean = false): Set<CellId> = buildSet {
        for (x in xs.first - 1..xs.last + 1) {
            for (y in ys.first - 1..ys.last + 1) {
                val onEdge = x !in xs || y !in ys
                val corner = (x == xs.first - 1 || x == xs.last + 1) && (y == ys.first - 1 || y == ys.last + 1)
                if (onEdge && !(cutCorners && corner)) add(CellId(x, y))
            }
        }
    }

    private fun block(xs: IntRange, ys: IntRange) = buildSet { for (x in xs) for (y in ys) add(CellId(x, y)) }

    @Test
    fun a_block_walked_all_around_clears() {
        val patches = enclosedPatches(walkAround(10..12, 10..12), city, noWays)
        assertEquals(listOf(block(10..12, 10..12)), patches)
    }

    @Test
    fun a_walkable_way_inside_keeps_the_block_in_fog() {
        val ways = WalkableWays(setOf(CellId(11, 11)))
        assertTrue(enclosedPatches(walkAround(10..12, 10..12), city, ways).isEmpty())
    }

    @Test
    fun an_open_loop_does_not_clear() {
        val walked = walkAround(10..12, 10..12) - CellId(11, 13)
        assertTrue(enclosedPatches(walked, city, noWays).isEmpty())
    }

    @Test
    fun turning_a_corner_diagonally_still_closes_the_loop() {
        val patches = enclosedPatches(walkAround(10..12, 10..12, cutCorners = true), city, noWays)
        assertEquals(listOf(block(10..12, 10..12)), patches)
    }

    @Test
    fun blocks_bigger_than_the_limit_stay_in_fog() {
        // 11 × 11 = 121 cells > MAX_ENCLOSED_CELLS.
        assertTrue(enclosedPatches(walkAround(5..15, 5..15), city, noWays).isEmpty())
        assertEquals(1, enclosedPatches(walkAround(5..14, 5..14), city, noWays).size)
    }

    @Test
    fun the_city_limit_counts_as_a_wall() {
        // An L-shaped walk cutting off the city's south-west corner.
        val walked = buildSet { for (i in 0..3) { add(CellId(3, i)); add(CellId(i, 3)) } }
        assertEquals(listOf(block(0..2, 0..2)), enclosedPatches(walked, city, noWays))
    }

    @Test
    fun closing_the_loop_reveals_the_block_at_once() {
        val full = walkAround(10..12, 10..12)
        val last = CellId(11, 13)
        val before = full - last
        assertTrue(patchesClosedBy(CellId(11, 9), before, emptySet(), city, noWays).isEmpty())
        assertEquals(listOf(block(10..12, 10..12)), patchesClosedBy(last, full, emptySet(), city, noWays))
    }

    @Test
    fun cleared_cells_take_the_time_the_loop_was_closed() {
        val walls = walkAround(10..12, 10..12).toList()
        val visits = walls.mapIndexed { i, cell -> VisitedCell(cell, firstVisitedAt = 1_000L + i) }
        val revealed = withEnclosedCells(visits, city, noWays)
        assertEquals(walls.size + 9, revealed.size)
        val inside = revealed.filter { it.cell !in walls }
        assertTrue(inside.all { it.firstVisitedAt == 1_000L + walls.size - 1 })
    }
}

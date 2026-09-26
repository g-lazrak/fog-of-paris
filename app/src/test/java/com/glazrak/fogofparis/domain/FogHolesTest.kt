package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FogHolesTest {

    @Test
    fun adjacent_cells_on_a_row_become_one_run() {
        val cells = setOf(CellId(3, 5), CellId(1, 5), CellId(2, 5))
        assertEquals(listOf(CellRun(y = 5, xStart = 1, xEnd = 3)), mergeIntoRuns(cells))
    }

    @Test
    fun gap_on_a_row_splits_the_run() {
        val cells = setOf(CellId(1, 0), CellId(2, 0), CellId(4, 0))
        assertEquals(
            listOf(CellRun(y = 0, xStart = 1, xEnd = 2), CellRun(y = 0, xStart = 4, xEnd = 4)),
            mergeIntoRuns(cells),
        )
    }

    @Test
    fun different_rows_give_separate_runs_including_negative_indices() {
        val cells = setOf(CellId(-2, -1), CellId(-1, -1), CellId(0, 7))
        assertEquals(
            listOf(CellRun(y = -1, xStart = -2, xEnd = -1), CellRun(y = 7, xStart = 0, xEnd = 0)),
            mergeIntoRuns(cells),
        )
    }

    @Test
    fun no_cells_gives_no_runs() {
        assertEquals(emptyList<CellRun>(), mergeIntoRuns(emptySet()))
    }

    @Test
    fun run_bounds_span_from_first_to_last_cell() {
        val bounds = runToBounds(CellRun(y = 4, xStart = 10, xEnd = 12))
        val first = cellToBounds(CellId(10, 4))
        val last = cellToBounds(CellId(12, 4))
        assertEquals(first.lonWest, bounds.lonWest, 0.0)
        assertEquals(last.lonEast, bounds.lonEast, 0.0)
        assertEquals(first.latNorth, bounds.latNorth, 0.0)
        assertEquals(first.latSouth, bounds.latSouth, 0.0)
    }
}

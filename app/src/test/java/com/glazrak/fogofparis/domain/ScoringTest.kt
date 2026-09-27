package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ScoringTest {

    // Quartier carré de 10 x 10 cellules = 100 cellules.
    private fun squareQuartier(id: Int, x0: Int, y0: Int, size: Int = 10): Quartier {
        val sw = cellToBounds(CellId(x0, y0))
        val ne = cellToBounds(CellId(x0 + size - 1, y0 + size - 1))
        val ring = Ring(
            listOf(
                GeoPosition(sw.latSouth, sw.lonWest), GeoPosition(sw.latSouth, ne.lonEast),
                GeoPosition(ne.latNorth, ne.lonEast), GeoPosition(ne.latNorth, sw.lonWest),
                GeoPosition(sw.latSouth, sw.lonWest),
            )
        )
        val boundary = CityBoundary(listOf(ring))
        return Quartier(id, "Q$id", arrondissement = 1, boundary = boundary, cells = CityCells.of(boundary))
    }

    private val quartierA = squareQuartier(id = 1, x0 = 0, y0 = 0)
    private val quartierB = squareQuartier(id = 2, x0 = 10, y0 = 0)
    private val index = QuartierIndex(listOf(quartierA, quartierB))

    private fun visitsInA(count: Int) = (0 until count).map { i ->
        VisitedCell(CellId(i % 10, i / 10), firstVisitedAt = 1_000L * (i + 1))
    }

    @Test
    fun square_quartier_has_one_hundred_cells() {
        assertEquals(100, quartierA.cells.totalCells)
    }

    @Test
    fun cells_are_assigned_to_the_right_quartier() {
        assertEquals(1, index.quartierOf(CellId(9, 9))?.id)
        assertEquals(2, index.quartierOf(CellId(10, 0))?.id)
        assertNull(index.quartierOf(CellId(50, 50)))
    }

    @Test
    fun medals_follow_thresholds_and_are_dated_by_the_cell_that_reached_them() {
        val progress = index.progress(visitsInA(30)).first { it.quartier.id == 1 }
        assertEquals(30.0, progress.percent, 0.001)
        assertEquals(Medal.SILVER, progress.medal)
        // Bronze = 10e cellule (t = 10 000), Argent = 25e (t = 25 000).
        assertEquals(mapOf(Medal.BRONZE to 10_000L, Medal.SILVER to 25_000L), progress.medalDates)
    }

    @Test
    fun just_below_a_threshold_gives_no_medal() {
        val progress = index.progress(visitsInA(9)).first { it.quartier.id == 1 }
        assertNull(progress.medal)
    }

    @Test
    fun threshold_rounds_up_for_small_quartiers() {
        // 10 % de 75 = 7,5 → 8 cellules.
        assertEquals(8, cellsNeededFor(Medal.BRONZE, 75))
    }

    @Test
    fun points_are_cells_plus_one_hundred_per_cumulative_medal() {
        val progress = index.progress(visitsInA(50)) // Or dans A = 3 médailles
        assertEquals(50 + 300, totalPoints(revealedCellsInParis = 50, quartiers = progress, collections = emptyList()))
    }

    @Test
    fun new_medal_is_detected_only_when_crossing_a_threshold() {
        assertEquals(listOf(Medal.BRONZE), medalsCrossed(before = 9, after = 10, totalCells = 100))
        assertEquals(emptyList<Medal>(), medalsCrossed(before = 10, after = 11, totalCells = 100))
        assertEquals(listOf(Medal.MASTERED), medalsCrossed(before = 74, after = 75, totalCells = 100))
        // Quartier de 2 cellules : la 1re cellule fait passer Bronze, Argent et Or d'un coup.
        assertEquals(listOf(Medal.BRONZE, Medal.SILVER, Medal.GOLD), medalsCrossed(0, 1, totalCells = 2))
    }

    @Test
    fun weekly_history_counts_per_monday_week_including_empty_weeks() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 9, 26) // samedi
        fun at(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val times = listOf(
            at(LocalDate.of(2026, 9, 21)), // lundi de la semaine en cours
            at(LocalDate.of(2026, 9, 26)),
            at(LocalDate.of(2026, 9, 14)), // semaine précédente
            at(LocalDate.of(2025, 1, 1)),  // trop ancienne, ignorée
        )
        val history = weeklyHistory(times, today, zone, weeks = 3)
        assertEquals(
            listOf(
                WeekCount(LocalDate.of(2026, 9, 7), 0),
                WeekCount(LocalDate.of(2026, 9, 14), 1),
                WeekCount(LocalDate.of(2026, 9, 21), 2),
            ),
            history,
        )
    }
}

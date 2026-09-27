package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreasureHuntTest {

    @Test
    fun warmth_rises_as_you_get_closer() {
        assertEquals(Warmth.BURNING, warmthFor(20.0))
        assertEquals(Warmth.VERY_HOT, warmthFor(80.0))
        assertEquals(Warmth.HOT, warmthFor(150.0))
        assertEquals(Warmth.WARM, warmthFor(350.0))
        assertEquals(Warmth.COLD, warmthFor(900.0))
    }

    @Test
    fun trend_ignores_small_gps_jitter() {
        assertEquals(Trend.STEADY, trendOf(null, 200.0))
        assertEquals(Trend.STEADY, trendOf(200.0, 195.0))
        assertEquals(Trend.CLOSER, trendOf(200.0, 180.0))
        assertEquals(Trend.FARTHER, trendOf(200.0, 230.0))
    }

    // Deux quartiers carrés côte à côte (10 x 10 cellules chacun).
    private fun square(id: Int, x0: Int): Quartier {
        val sw = cellToBounds(CellId(x0, 0))
        val ne = cellToBounds(CellId(x0 + 9, 9))
        val ring = Ring(
            listOf(
                GeoPosition(sw.latSouth, sw.lonWest), GeoPosition(sw.latSouth, ne.lonEast),
                GeoPosition(ne.latNorth, ne.lonEast), GeoPosition(ne.latNorth, sw.lonWest),
                GeoPosition(sw.latSouth, sw.lonWest),
            )
        )
        val boundary = CityBoundary(listOf(ring))
        return Quartier(id, "Q$id", 1, boundary, CityCells.of(boundary))
    }

    private val index = QuartierIndex(listOf(square(1, 0), square(2, 10)))
    private fun centerOf(cell: CellId): GeoPosition {
        val b = cellToBounds(cell)
        return GeoPosition((b.latNorth + b.latSouth) / 2, (b.lonEast + b.lonWest) / 2)
    }
    private val treasure = Place("t", "T", CollectionSet.TREASURES, centerOf(CellId(5, 5)), hint = "…")

    @Test
    fun hunt_starts_only_from_the_treasure_quartier() {
        assertEquals(HuntStart.Started, canStartHunt(treasure, centerOf(CellId(2, 2)), index))
        val elsewhere = canStartHunt(treasure, centerOf(CellId(15, 5)), index)
        assertTrue(elsewhere is HuntStart.WrongQuartier && elsewhere.treasureQuartier.id == 1)
        assertEquals(HuntStart.NoPosition, canStartHunt(treasure, null, index))
    }
}

package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DailyHistoryTest {

    private val zone = ZoneOffset.UTC
    private fun at(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).toInstant(zone).toEpochMilli()
    private val today = LocalDate.of(2026, 9, 27)

    private val visits = listOf(
        VisitedCell(CellId(1, 1), at(today)),
        VisitedCell(CellId(1, 2), at(today, 23)),
        VisitedCell(CellId(5, 5), at(today.minusDays(3))),
        VisitedCell(CellId(9, 9), at(today.minusDays(30))),
    )

    @Test
    fun daily_history_counts_each_day_including_empty_ones() {
        val history = dailyHistory(visits.map { it.firstVisitedAt }, today, zone, days = 5)
        assertEquals(5, history.size)
        assertEquals(today, history.last().date)
        assertEquals(listOf(0, 1, 0, 0, 2), history.map { it.revealedCells })
    }

    @Test
    fun cells_revealed_on_a_day_are_only_that_day_first_visits() {
        assertEquals(setOf(CellId(1, 1), CellId(1, 2)), cellsRevealedOn(visits, today, zone))
        assertEquals(emptySet<CellId>(), cellsRevealedOn(visits, today.minusDays(1), zone))
    }

    @Test
    fun navigation_skips_empty_days() {
        assertEquals(today.minusDays(3), adjacentActiveDay(visits, today, zone, forward = false))
        assertEquals(today.minusDays(30), adjacentActiveDay(visits, today.minusDays(3), zone, forward = false))
        assertEquals(today, adjacentActiveDay(visits, today.minusDays(3), zone, forward = true))
        assertNull(adjacentActiveDay(visits, today, zone, forward = true))
    }
}

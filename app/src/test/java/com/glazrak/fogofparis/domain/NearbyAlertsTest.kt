package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyAlertsTest {

    private val minute = 60_000L
    private val now = 100 * minute

    private fun entries(total: Int, newOnes: Int, startMinutesAgo: Int = 9) =
        (0 until total).map { i -> CellEntry(now - startMinutesAgo * minute + i * 1_000L, wasNew = i < newOnes) }

    private val pont = Place("pont-neuf", "Pont Neuf", CollectionSet.BRIDGES, GeoPosition(48.8575, 2.3413))
    private fun near(distance: Double) = PlaceDirection(pont, distance, 0.0)

    @Test
    fun commute_on_known_streets_is_familiar_ground() {
        assertTrue(isFamiliarGround(entries(total = 20, newOnes = 2), now))
    }

    @Test
    fun exploring_new_streets_is_not_familiar_ground() {
        assertFalse(isFamiliarGround(entries(total = 20, newOnes = 10), now))
    }

    @Test
    fun too_few_squares_crossed_is_not_enough_to_judge() {
        assertFalse(isFamiliarGround(entries(total = 5, newOnes = 0), now))
    }

    @Test
    fun old_entries_outside_the_window_are_ignored() {
        // 20 entrées d'il y a 30 minutes : ça ne dit rien de maintenant.
        assertFalse(isFamiliarGround(entries(total = 20, newOnes = 0, startMinutesAgo = 30), now))
    }

    @Test
    fun announces_a_close_unvisited_place_on_familiar_ground() {
        assertEquals(pont, placeToAnnounce(near(150.0), familiar = true, emptySet(), lastAlertMillis = null, nowMillis = now))
    }

    @Test
    fun stays_quiet_when_exploring_far_already_announced_or_too_soon() {
        assertNull(placeToAnnounce(near(150.0), familiar = false, emptySet(), null, now))
        assertNull(placeToAnnounce(near(350.0), familiar = true, emptySet(), null, now))
        assertNull(placeToAnnounce(near(150.0), familiar = true, setOf("pont-neuf"), null, now))
        assertNull(placeToAnnounce(near(150.0), familiar = true, emptySet(), lastAlertMillis = now - 5 * minute, nowMillis = now))
    }
}

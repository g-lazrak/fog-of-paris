package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FixFilterTest {

    // Un grand carré autour de Paris, pour tester les filtres sans le vrai contour.
    private val city = CityBoundary(
        listOf(
            Ring(
                listOf(
                    GeoPosition(lat = 48.80, lon = 2.20), GeoPosition(lat = 48.80, lon = 2.50),
                    GeoPosition(lat = 48.92, lon = 2.50), GeoPosition(lat = 48.92, lon = 2.20),
                    GeoPosition(lat = 48.80, lon = 2.20),
                )
            )
        )
    )
    private val notreDame = GeoPosition(lat = 48.8530, lon = 2.3499)
    private val onFoot = Movement.ON_FOOT

    private fun fix(
        position: GeoPosition = notreDame,
        accuracy: Float? = 10f,
        speedKmh: Double? = 5.0,
        timeMillis: Long = 0L,
    ) = LocationFix(
        position = position,
        accuracyMeters = accuracy,
        speedMetersPerSecond = speedKmh?.let { (it / 3.6).toFloat() },
        timeMillis = timeMillis,
    )

    @Test
    fun accurate_walking_fix_in_paris_is_accepted() {
        assertNull(rejectionReason(fix(), previous = null, city = city, movement = onFoot))
    }

    @Test
    fun running_speed_is_accepted() {
        assertNull(rejectionReason(fix(speedKmh = 10.0), previous = null, city = city, movement = onFoot))
    }

    @Test
    fun slow_fix_is_rejected_unless_android_says_on_foot() {
        // Bus coincé dans les bouchons : lent, précis, dans Paris… mais pas à pied.
        val slowFix = fix(speedKmh = 3.0)
        assertEquals(FixRejection.NOT_ON_FOOT, rejectionReason(slowFix, null, city, Movement.VEHICLE))
        assertEquals(FixRejection.NOT_ON_FOOT, rejectionReason(slowFix, null, city, Movement.STILL))
        assertEquals(FixRejection.NOT_ON_FOOT, rejectionReason(slowFix, null, city, Movement.BICYCLE))
        assertEquals(FixRejection.NOT_ON_FOOT, rejectionReason(slowFix, null, city, Movement.UNKNOWN))
    }

    @Test
    fun imprecise_or_unknown_accuracy_is_rejected() {
        assertEquals(FixRejection.INACCURATE, rejectionReason(fix(accuracy = 45f), null, city, onFoot))
        assertEquals(FixRejection.INACCURATE, rejectionReason(fix(accuracy = null), null, city, onFoot))
    }

    @Test
    fun vehicle_speed_is_rejected() {
        assertEquals(FixRejection.TOO_FAST, rejectionReason(fix(speedKmh = 25.0), null, city, onFoot))
    }

    @Test
    fun outside_city_is_rejected() {
        val versailles = GeoPosition(lat = 48.8049, lon = 2.1204)
        assertEquals(FixRejection.OUTSIDE_PARIS, rejectionReason(fix(position = versailles), null, city, onFoot))
    }

    @Test
    fun without_gps_speed_a_fast_move_since_previous_fix_is_rejected() {
        // ~500 m en 20 s = 90 km/h.
        val previous = fix(position = notreDame, speedKmh = null, timeMillis = 0L)
        val moved = GeoPosition(lat = notreDame.lat + 500.0 / METERS_PER_DEGREE_LAT, lon = notreDame.lon)
        val current = fix(position = moved, speedKmh = null, timeMillis = 20_000L)
        assertEquals(FixRejection.TOO_FAST, rejectionReason(current, previous, city, onFoot))
    }

    @Test
    fun reappearing_after_a_long_gap_is_not_judged_on_speed() {
        // Sortie de métro : 2 km plus loin, 10 minutes après. On révèle seulement
        // la cellule d'arrivée (jamais le trajet), donc pas de rejet pour vitesse.
        val previous = fix(position = notreDame, speedKmh = null, timeMillis = 0L)
        val farAway = GeoPosition(lat = notreDame.lat + 2000.0 / METERS_PER_DEGREE_LAT, lon = notreDame.lon)
        val current = fix(position = farAway, speedKmh = null, timeMillis = 600_000L)
        assertNull(rejectionReason(current, previous, city, onFoot))
    }

    @Test
    fun distance_of_one_cell_is_fifty_meters() {
        val a = GeoPosition(lat = 48.85, lon = 2.35)
        val b = GeoPosition(lat = 48.85 + DELTA_LAT, lon = 2.35)
        assertEquals(50.0, distanceMeters(a, b), 0.01)
    }
}

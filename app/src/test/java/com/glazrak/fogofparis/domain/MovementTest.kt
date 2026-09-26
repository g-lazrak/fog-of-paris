package com.glazrak.fogofparis.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MovementTest {

    private fun enter(movement: Movement) = MovementTransition(movement, TransitionKind.ENTER)
    private fun exit(movement: Movement) = MovementTransition(movement, TransitionKind.EXIT)

    @Test
    fun entering_an_activity_makes_it_current() {
        assertEquals(Movement.ON_FOOT, applyTransitions(Movement.UNKNOWN, listOf(enter(Movement.ON_FOOT))))
    }

    @Test
    fun events_are_applied_in_order() {
        val transitions = listOf(exit(Movement.ON_FOOT), enter(Movement.VEHICLE))
        assertEquals(Movement.VEHICLE, applyTransitions(Movement.ON_FOOT, transitions))
    }

    @Test
    fun leaving_current_activity_without_a_new_one_means_unknown() {
        assertEquals(Movement.UNKNOWN, applyTransitions(Movement.ON_FOOT, listOf(exit(Movement.ON_FOOT))))
    }

    @Test
    fun late_exit_of_an_older_activity_does_not_erase_the_current_one() {
        assertEquals(Movement.ON_FOOT, applyTransitions(Movement.ON_FOOT, listOf(exit(Movement.VEHICLE))))
    }

    @Test
    fun only_on_foot_with_precise_gps_is_revealing() {
        assertEquals(TrackingStatus.REVEALING, trackingStatus(Movement.ON_FOOT, hasPreciseFix = true))
        assertEquals(TrackingStatus.SEARCHING_GPS, trackingStatus(Movement.ON_FOOT, hasPreciseFix = false))
        assertEquals(TrackingStatus.PAUSED_VEHICLE, trackingStatus(Movement.VEHICLE, hasPreciseFix = true))
        assertEquals(TrackingStatus.PAUSED_STILL, trackingStatus(Movement.STILL, hasPreciseFix = true))
        assertEquals(TrackingStatus.PAUSED_BICYCLE, trackingStatus(Movement.BICYCLE, hasPreciseFix = true))
        assertEquals(TrackingStatus.WAITING_DETECTION, trackingStatus(Movement.UNKNOWN, hasPreciseFix = true))
    }
}

package com.glazrak.fogofparis.domain

// Ce que fait l'utilisateur, d'après la détection d'activité d'Android.
// Seul ON_FOOT (marche ou course) révèle des cellules.
enum class Movement { UNKNOWN, ON_FOOT, STILL, VEHICLE, BICYCLE }

enum class TransitionKind { ENTER, EXIT }

data class MovementTransition(val movement: Movement, val kind: TransitionKind)

// Applique les changements signalés par Android, dans l'ordre.
// Sortir de l'activité en cours sans en annoncer une nouvelle = on ne sait plus
// (UNKNOWN), donc on ne révèle rien : mieux vaut rater une case qu'en inventer.
fun applyTransitions(current: Movement, transitions: List<MovementTransition>): Movement =
    transitions.fold(current) { state, transition ->
        when (transition.kind) {
            TransitionKind.ENTER -> transition.movement
            TransitionKind.EXIT -> if (state == transition.movement) Movement.UNKNOWN else state
        }
    }

// Ce qu'affiche l'écran pendant le suivi.
enum class TrackingStatus {
    REVEALING,          // à pied, GPS précis
    SEARCHING_GPS,      // à pied, mais GPS pas encore assez précis
    WAITING_DETECTION,  // pas encore d'info sur l'activité
    PAUSED_STILL,
    PAUSED_VEHICLE,
    PAUSED_BICYCLE,
}

fun trackingStatus(movement: Movement, hasPreciseFix: Boolean): TrackingStatus = when (movement) {
    Movement.ON_FOOT -> if (hasPreciseFix) TrackingStatus.REVEALING else TrackingStatus.SEARCHING_GPS
    Movement.UNKNOWN -> TrackingStatus.WAITING_DETECTION
    Movement.STILL -> TrackingStatus.PAUSED_STILL
    Movement.VEHICLE -> TrackingStatus.PAUSED_VEHICLE
    Movement.BICYCLE -> TrackingStatus.PAUSED_BICYCLE
}

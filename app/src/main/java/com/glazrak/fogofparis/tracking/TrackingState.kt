package com.glazrak.fogofparis.tracking

import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.Movement
import com.glazrak.fogofparis.domain.MovementTransition
import com.glazrak.fogofparis.domain.applyTransitions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// État du suivi partagé entre le service et le récepteur d'activité (qui
// écrivent) et l'écran (qui lit). Vit dans le processus de l'app : si Android
// tue le processus, le service meurt aussi et tout repart à zéro, ce qui reste cohérent.
object TrackingState {
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _lastPosition = MutableStateFlow<GeoPosition?>(null)
    val lastPosition: StateFlow<GeoPosition?> = _lastPosition.asStateFlow()

    private val _movement = MutableStateFlow(Movement.UNKNOWN)
    val movement: StateFlow<Movement> = _movement.asStateFlow()

    // La dernière position reçue était-elle assez précise pour révéler ?
    private val _hasPreciseFix = MutableStateFlow(false)
    val hasPreciseFix: StateFlow<Boolean> = _hasPreciseFix.asStateFlow()

    internal fun setTracking(tracking: Boolean) {
        _isTracking.value = tracking
        if (!tracking) {
            _lastPosition.value = null
            _movement.value = Movement.UNKNOWN
            _hasPreciseFix.value = false
        }
    }

    internal fun setPosition(position: GeoPosition, isPrecise: Boolean) {
        _lastPosition.value = position
        _hasPreciseFix.value = isPrecise
    }

    internal fun applyMovementTransitions(transitions: List<MovementTransition>) {
        _movement.update { applyTransitions(it, transitions) }
    }
}

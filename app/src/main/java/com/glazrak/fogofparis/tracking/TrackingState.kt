package com.glazrak.fogofparis.tracking

import com.glazrak.fogofparis.domain.GeoPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// État du suivi partagé entre le service (qui écrit) et l'écran (qui lit).
// Vit dans le processus de l'app : si Android tue le processus, le service
// meurt aussi et l'état repart à "arrêté", ce qui reste cohérent.
object TrackingState {
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _lastPosition = MutableStateFlow<GeoPosition?>(null)
    val lastPosition: StateFlow<GeoPosition?> = _lastPosition.asStateFlow()

    internal fun setTracking(tracking: Boolean) {
        _isTracking.value = tracking
        if (!tracking) _lastPosition.value = null
    }

    internal fun setPosition(position: GeoPosition) {
        _lastPosition.value = position
    }
}

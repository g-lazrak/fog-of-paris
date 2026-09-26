package com.glazrak.fogofparis.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glazrak.fogofparis.data.VisitedCellsStore
import com.glazrak.fogofparis.domain.CellId
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.latLonToCell
import com.glazrak.fogofparis.tracking.locationUpdates
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Prépare les données de l'écran carte. Survit aux rotations d'écran,
// contrairement au composable.
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val store = VisitedCellsStore(application)

    val visitedCells: StateFlow<Set<CellId>> = store.visitedCells
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _currentPosition = MutableStateFlow<GeoPosition?>(null)
    val currentPosition: StateFlow<GeoPosition?> = _currentPosition.asStateFlow()

    private var trackingJob: Job? = null

    // À appeler une fois la permission de localisation accordée.
    fun startTracking() {
        if (trackingJob != null) return
        trackingJob = viewModelScope.launch {
            var lastCell: CellId? = null
            locationUpdates(getApplication()).collect { position ->
                _currentPosition.value = position
                val cell = latLonToCell(position.lat, position.lon)
                // Évite une écriture disque tant qu'on reste dans la même cellule.
                if (cell != lastCell) {
                    store.addCell(cell)
                    lastCell = cell
                }
            }
        }
    }
}

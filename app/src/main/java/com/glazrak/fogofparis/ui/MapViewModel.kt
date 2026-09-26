package com.glazrak.fogofparis.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glazrak.fogofparis.data.ParisBoundaryCache
import com.glazrak.fogofparis.data.VisitedCellsRepository
import com.glazrak.fogofparis.domain.CellId
import com.glazrak.fogofparis.domain.CityBoundary
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.tracking.TrackingService
import com.glazrak.fogofparis.tracking.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Prépare les données de l'écran carte. Survit aux rotations d'écran,
// contrairement au composable. Le GPS lui-même est géré par TrackingService.
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VisitedCellsRepository.create(application)

    val visitedCells: StateFlow<Set<CellId>> = repository.visitedCells
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val isTracking: StateFlow<Boolean> = TrackingState.isTracking
    val currentPosition: StateFlow<GeoPosition?> = TrackingState.lastPosition

    // null tant que le fichier des arrondissements n'est pas lu (quelques ms).
    private val _parisBoundary = MutableStateFlow<CityBoundary?>(null)
    val parisBoundary: StateFlow<CityBoundary?> = _parisBoundary.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.importLegacyCells()
            } catch (e: Exception) {
                Log.e("MapViewModel", "Failed to import prototype cells", e)
            }
        }
        viewModelScope.launch {
            try {
                _parisBoundary.value = ParisBoundaryCache.get(application)
            } catch (e: Exception) {
                Log.e("MapViewModel", "Failed to load Paris boundary", e)
            }
        }
    }

    // À appeler une fois la permission de localisation précise accordée.
    fun startTracking() = TrackingService.start(getApplication())

    fun stopTracking() = TrackingService.stop(getApplication())
}

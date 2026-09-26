package com.glazrak.fogofparis.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glazrak.fogofparis.data.VisitedCellsRepository
import com.glazrak.fogofparis.data.loadParisBoundary
import com.glazrak.fogofparis.domain.CellId
import com.glazrak.fogofparis.domain.CityBoundary
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.latLonToCell
import com.glazrak.fogofparis.tracking.locationUpdates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Prépare les données de l'écran carte. Survit aux rotations d'écran,
// contrairement au composable.
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VisitedCellsRepository.create(application)

    val visitedCells: StateFlow<Set<CellId>> = repository.visitedCells
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _currentPosition = MutableStateFlow<GeoPosition?>(null)
    val currentPosition: StateFlow<GeoPosition?> = _currentPosition.asStateFlow()

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
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _parisBoundary.value = loadParisBoundary(application)
            } catch (e: Exception) {
                Log.e("MapViewModel", "Failed to load Paris boundary", e)
            }
        }
    }

    private var trackingJob: Job? = null

    // À appeler une fois la permission de localisation accordée.
    fun startTracking() {
        if (trackingJob != null) return
        trackingJob = viewModelScope.launch {
            val paris = parisBoundary.filterNotNull().first()
            var lastCell: CellId? = null
            locationUpdates(getApplication()).collect { position ->
                _currentPosition.value = position
                // Hors de Paris : on affiche la position mais on ne révèle rien.
                if (!paris.contains(position)) return@collect
                val cell = latLonToCell(position.lat, position.lon)
                // Évite une écriture disque tant qu'on reste dans la même cellule.
                if (cell != lastCell) {
                    repository.recordVisit(cell)
                    lastCell = cell
                }
            }
        }
    }
}

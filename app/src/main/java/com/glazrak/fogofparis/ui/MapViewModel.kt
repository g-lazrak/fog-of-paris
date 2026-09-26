package com.glazrak.fogofparis.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glazrak.fogofparis.data.ParisBoundaryCache
import com.glazrak.fogofparis.data.VisitedCellsRepository
import com.glazrak.fogofparis.domain.CellId
import com.glazrak.fogofparis.domain.CityBoundary
import com.glazrak.fogofparis.domain.CityCells
import com.glazrak.fogofparis.domain.FOG_PIXELS_PER_CELL
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.TrackingStatus
import com.glazrak.fogofparis.domain.fogAlphaMask
import com.glazrak.fogofparis.domain.trackingStatus
import com.glazrak.fogofparis.tracking.TrackingService
import com.glazrak.fogofparis.tracking.TrackingState
import com.glazrak.fogofparis.tracking.lastKnownPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Part de Paris révélée : cellules révélées dans Paris / cellules de Paris.
data class Progress(val revealedCells: Int, val totalCells: Int) {
    val percent: Double get() = if (totalCells == 0) 0.0 else revealedCells * 100.0 / totalCells
}

// Demande de déplacer la carte. Le numéro permet de redemander la même position.
data class CameraTarget(val position: GeoPosition, val requestId: Int)

// Prépare les données de l'écran carte. Survit aux rotations d'écran,
// contrairement au composable. Le GPS lui-même est géré par TrackingService.
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VisitedCellsRepository.create(application)

    private val visitedCells: StateFlow<Set<CellId>> = repository.visitedCells
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val isTracking: StateFlow<Boolean> = TrackingState.isTracking

    // Position lue à la demande quand le suivi est arrêté (bouton "me recentrer").
    private val oneShotPosition = MutableStateFlow<GeoPosition?>(null)

    // Pendant le suivi, la position du service ; sinon la dernière lue à la demande.
    val currentPosition: StateFlow<GeoPosition?> =
        combine(TrackingState.lastPosition, oneShotPosition) { tracked, oneShot -> tracked ?: oneShot }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // null quand le suivi est arrêté (rien à afficher).
    val trackingStatus: StateFlow<TrackingStatus?> = combine(
        TrackingState.isTracking,
        TrackingState.movement,
        TrackingState.hasPreciseFix,
    ) { tracking, movement, precise ->
        if (tracking) trackingStatus(movement, precise) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // null tant que le fichier des arrondissements n'est pas lu (quelques ms).
    private val _parisBoundary = MutableStateFlow<CityBoundary?>(null)
    val parisBoundary: StateFlow<CityBoundary?> = _parisBoundary.asStateFlow()

    private val cityCells = MutableStateFlow<CityCells?>(null)

    val progress: StateFlow<Progress?> = combine(visitedCells, cityCells.filterNotNull()) { cells, city ->
        Progress(revealedCells = city.countInside(cells), totalCells = city.totalCells)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Recalculée à chaque nouvelle cellule (une petite image, quelques ms).
    val fogImage: StateFlow<FogImage?> = combine(visitedCells, cityCells.filterNotNull()) { cells, city ->
        buildFogImage(cells, city)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _cameraTarget = MutableStateFlow<CameraTarget?>(null)
    val cameraTarget: StateFlow<CameraTarget?> = _cameraTarget.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.importLegacyCells()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import prototype cells", e)
            }
        }
        viewModelScope.launch {
            try {
                val boundary = ParisBoundaryCache.get(application)
                _parisBoundary.value = boundary
                cityCells.value = withContext(Dispatchers.Default) { CityCells.of(boundary) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Paris boundary", e)
            }
        }
        viewModelScope.launch { centerOnUserIfInParis() }
    }

    // À l'ouverture : sur l'utilisateur s'il est dans Paris, sinon on reste
    // sur le centre de Paris (le reste du monde est noir, sans intérêt).
    private suspend fun centerOnUserIfInParis() {
        if (!hasLocationPermission()) return
        val position = lastKnownPosition(getApplication()) ?: return
        val paris = parisBoundary.filterNotNull().first()
        if (paris.contains(position)) moveCameraTo(position)
    }

    // Bouton "me recentrer". La permission est vérifiée par l'écran.
    fun recenter() {
        viewModelScope.launch {
            val position = TrackingState.lastPosition.value ?: lastKnownPosition(getApplication())
            if (position == null) {
                Log.e(TAG, "No known position to recenter on")
                return@launch
            }
            if (!TrackingState.isTracking.value) oneShotPosition.value = position
            moveCameraTo(position)
        }
    }

    private fun moveCameraTo(position: GeoPosition) {
        val nextId = (_cameraTarget.value?.requestId ?: 0) + 1
        _cameraTarget.value = CameraTarget(position, nextId)
    }

    private fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        getApplication(), Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    // À appeler une fois les permissions accordées.
    fun startTracking() = TrackingService.start(getApplication())

    fun stopTracking() = TrackingService.stop(getApplication())

    private companion object {
        const val TAG = "MapViewModel"
    }
}

private fun buildFogImage(cells: Set<CellId>, city: CityCells): FogImage {
    val extent = city.extent
    val alpha = fogAlphaMask(cells, extent)
    // Noir avec l'opacité calculée : couleur ARGB = alpha << 24.
    val colors = IntArray(alpha.size) { alpha[it] shl 24 }
    val bitmap = Bitmap.createBitmap(
        colors,
        extent.width * FOG_PIXELS_PER_CELL,
        extent.height * FOG_PIXELS_PER_CELL,
        Bitmap.Config.ARGB_8888,
    )
    return FogImage(bitmap, extent)
}

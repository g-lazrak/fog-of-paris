package com.glazrak.fogofparis.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glazrak.fogofparis.data.ParisGeo
import com.glazrak.fogofparis.data.ParisGeoCache
import com.glazrak.fogofparis.data.MenuTheme
import com.glazrak.fogofparis.data.SettingsStore
import com.glazrak.fogofparis.data.VisitedCellsRepository
import com.glazrak.fogofparis.domain.CellId
import com.glazrak.fogofparis.domain.CityBoundary
import com.glazrak.fogofparis.domain.CityCells
import com.glazrak.fogofparis.domain.CollectionProgress
import com.glazrak.fogofparis.domain.CollectionSet
import com.glazrak.fogofparis.domain.isRevealedPlace
import com.glazrak.fogofparis.domain.latLonToCell
import com.glazrak.fogofparis.domain.Level
import com.glazrak.fogofparis.domain.LevelProgress
import com.glazrak.fogofparis.domain.PlaceDirection
import com.glazrak.fogofparis.domain.nearestUnvisited
import com.glazrak.fogofparis.domain.placeDiscoveryTimes
import com.glazrak.fogofparis.domain.Place
import com.glazrak.fogofparis.domain.collectionProgress
import com.glazrak.fogofparis.domain.levelFor
import com.glazrak.fogofparis.domain.FOG_ALPHA
import com.glazrak.fogofparis.domain.FOG_PIXELS_PER_CELL
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.Quartier
import com.glazrak.fogofparis.domain.QuartierProgress
import com.glazrak.fogofparis.domain.TrackingStatus
import com.glazrak.fogofparis.domain.VisitedCell
import com.glazrak.fogofparis.domain.DayCount
import com.glazrak.fogofparis.domain.HuntStart
import com.glazrak.fogofparis.domain.Trend
import com.glazrak.fogofparis.domain.Warmth
import com.glazrak.fogofparis.domain.canStartHunt
import com.glazrak.fogofparis.domain.distanceMeters
import com.glazrak.fogofparis.domain.trendOf
import com.glazrak.fogofparis.domain.warmthFor
import com.glazrak.fogofparis.domain.WeekCount
import com.glazrak.fogofparis.domain.adjacentActiveDay
import com.glazrak.fogofparis.domain.cellToBounds
import com.glazrak.fogofparis.domain.cellsRevealedOn
import com.glazrak.fogofparis.domain.dailyHistory
import com.glazrak.fogofparis.domain.fogAlphaMask
import com.glazrak.fogofparis.domain.totalPoints
import com.glazrak.fogofparis.domain.trackingStatus
import com.glazrak.fogofparis.domain.weeklyHistory
import com.glazrak.fogofparis.tracking.TrackingService
import com.glazrak.fogofparis.tracking.TrackingState
import com.glazrak.fogofparis.tracking.canTrack
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// Résumé affiché en haut de la carte et sur l'écran « Progrès ».
data class GameSummary(
    val revealedCells: Int,
    val totalCells: Int,
    val points: Int,
    val medalledQuartiers: Int,
    val visitedPlaces: Int,
    val totalPlaces: Int,
) {
    val percent: Double get() = if (totalCells == 0) 0.0 else revealedCells * 100.0 / totalCells
    val level: LevelProgress get() = levelFor(points)
}

// Un lieu de collection à afficher sur la carte.
data class PlaceMarker(val place: Place, val visited: Boolean)

// Chasse au trésor en cours, telle qu'affichée par le bandeau de la carte.
data class HuntState(
    val treasure: Place,
    // null tant qu'on ne connaît pas la position.
    val distanceMeters: Double?,
    val warmth: Warmth?,
    val trend: Trend,
    val pinRevealed: Boolean,
    val found: Boolean,
    // Suivi actif : la distance se met à jour en marchant ; sinon, elle est figée.
    val liveTracking: Boolean,
)

// Où déplacer la carte. Le numéro permet de redemander la même chose.
sealed interface CameraMove {
    val requestId: Int

    data class ToPosition(val position: GeoPosition, override val requestId: Int) : CameraMove
    data class ToArea(val boundary: CityBoundary, override val requestId: Int) : CameraMove
    data class ToBounds(
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double,
        override val requestId: Int,
    ) : CameraMove
}

// Données de tous les écrans (carte, progrès, quartiers, collections). Survit aux rotations d'écran,
// contrairement aux composables. Le GPS lui-même est géré par TrackingService.
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VisitedCellsRepository.create(application)

    private val visits: StateFlow<List<VisitedCell>> = repository.visits
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val visitedCells: StateFlow<Set<CellId>> = visits
        .map { list -> list.mapTo(HashSet(list.size)) { it.cell } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // null tant que les fichiers de Paris ne sont pas lus (moins d'une seconde).
    private val geo = MutableStateFlow<ParisGeo?>(null)
    val parisBoundary: StateFlow<CityBoundary?> = geo
        .map { it?.boundary }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    val quartierProgress: StateFlow<List<QuartierProgress>> =
        combine(visits, geo.filterNotNull()) { list, parisGeo -> parisGeo.quartiers.progress(list) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collections: StateFlow<List<CollectionProgress>> =
        combine(visitedCells, geo.filterNotNull()) { cells, parisGeo -> collectionProgress(parisGeo.places.places, cells) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Les trésors n'apparaissent sur la carte qu'une fois trouvés.
    val placeMarkers: StateFlow<List<PlaceMarker>> = collections
        .map { sets ->
            sets.flatMap { set ->
                set.places.filter { isRevealedPlace(it, set.visitedIds) }.map { PlaceMarker(it, it.id in set.visitedIds) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Le trésor de chaque quartier (un par quartier) et s'il a été trouvé.
    val treasureByQuartier: StateFlow<Map<Int, PlaceMarker>> =
        combine(geo.filterNotNull(), collections) { parisGeo, sets ->
            val treasures = sets.firstOrNull { it.set == CollectionSet.TREASURES } ?: return@combine emptyMap()
            treasures.places.mapNotNull { place ->
                val quartier = parisGeo.quartiers.quartierOf(latLonToCell(place.position.lat, place.position.lon))
                quartier?.let { it.id to PlaceMarker(place, place.id in treasures.visitedIds) }
            }.toMap()
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val summary: StateFlow<GameSummary?> =
        combine(visitedCells, geo.filterNotNull(), quartierProgress, collections) { cells, parisGeo, progress, sets ->
            val revealed = parisGeo.cells.countInside(cells)
            GameSummary(
                revealedCells = revealed,
                totalCells = parisGeo.cells.totalCells,
                points = totalPoints(revealed, progress, sets),
                medalledQuartiers = progress.count { it.medal != null },
                // Les 145 lieux visibles ; les trésors ont leur propre compteur (Collections).
                visitedPlaces = sets.filterNot { it.set.hidden }.sumOf { it.visitedCount },
                totalPlaces = sets.filterNot { it.set.hidden }.sumOf { it.places.size },
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Carte « Prochain lieu » : le lieu non visité le plus proche de la position connue.
    val nearestPlace: StateFlow<PlaceDirection?> =
        combine(currentPosition, collections) { position, sets ->
            if (position == null) return@combine null
            nearestUnvisited(
                places = sets.flatMap { it.places },
                visitedIds = sets.flatMapTo(HashSet()) { it.visitedIds },
                from = position,
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Les 5 derniers lieux découverts, du plus récent au plus ancien.
    val recentDiscoveries: StateFlow<List<Pair<Place, Long>>> =
        combine(visits, geo.filterNotNull()) { list, parisGeo ->
            val firstVisitByCell = list.associate { it.cell to it.firstVisitedAt }
            val places = parisGeo.places.places.associateBy { it.id }
            placeDiscoveryTimes(parisGeo.places.places, firstVisitByCell)
                .entries.sortedByDescending { it.value }
                .take(5)
                .mapNotNull { (id, time) -> places[id]?.let { it to time } }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val settings = SettingsStore(application)

    val nearbyAlerts: StateFlow<Boolean> = settings.nearbyAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setNearbyAlerts(enabled: Boolean) {
        viewModelScope.launch { settings.setNearbyAlerts(enabled) }
    }

    // Nouveau titre à fêter à l'écran, ou null. À la toute première ouverture,
    // on enregistre le niveau actuel sans fête (rien n'a été « gagné » à l'instant).
    val levelToCelebrate: StateFlow<Level?> =
        combine(summary.filterNotNull(), settings.lastCelebratedLevel) { current, lastCelebrated ->
            val level = current.level.level
            when {
                lastCelebrated == null -> {
                    settings.setLastCelebratedLevel(level.number)
                    null
                }
                level.number > lastCelebrated -> level
                else -> null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissCelebration(level: Level) {
        viewModelScope.launch { settings.setLastCelebratedLevel(level.number) }
    }

    val weeklyHistory: StateFlow<List<WeekCount>> = visits
        .map { list -> weeklyHistory(list.map { it.firstVisitedAt }, LocalDate.now(), ZoneId.systemDefault()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Recalculée à chaque nouvelle cellule (une petite image, quelques ms).
    // Journal de marche : les 14 derniers jours, pour l'écran « Progrès ».
    val dailyHistory: StateFlow<List<DayCount>> = visits
        .map { list -> dailyHistory(list.map { it.firstVisitedAt }, LocalDate.now(), ZoneId.systemDefault()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Jour affiché sur la carte (null = vue normale).
    private val _selectedDay = MutableStateFlow<LocalDate?>(null)
    val selectedDay: StateFlow<LocalDate?> = _selectedDay.asStateFlow()

    // Cellules révélées pour la première fois le jour affiché.
    val dayCells: StateFlow<Set<CellId>> = combine(visits, _selectedDay) { list, day ->
        if (day == null) emptySet() else cellsRevealedOn(list, day, ZoneId.systemDefault())
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun showDay(day: LocalDate) {
        _selectedDay.value = day
        val cells = cellsRevealedOn(visits.value, day, ZoneId.systemDefault())
        if (cells.isNotEmpty()) {
            // Cadre la carte sur les rues découvertes ce jour-là.
            val south = cells.minOf { it.y }
            val north = cells.maxOf { it.y }
            val west = cells.minOf { it.x }
            val east = cells.maxOf { it.x }
            val sw = cellToBounds(CellId(west, south))
            val ne = cellToBounds(CellId(east, north))
            _cameraMove.value = CameraMove.ToBounds(
                south = sw.latSouth, west = sw.lonWest, north = ne.latNorth, east = ne.lonEast,
                requestId = nextRequestId(),
            )
        }
    }

    // Jour précédent / suivant ayant au moins une nouvelle cellule.
    fun shiftDay(forward: Boolean) {
        val current = _selectedDay.value ?: return
        adjacentActiveDay(visits.value, current, ZoneId.systemDefault(), forward)?.let { showDay(it) }
    }

    fun clearDay() {
        _selectedDay.value = null
    }

    // --- Chasse au trésor « chaud / froid » ---

    private val huntedTreasure = MutableStateFlow<Place?>(null)
    private var lastHuntDistance: Double? = null
    private var lastHuntTrend = Trend.STEADY

    private val revealedPins: StateFlow<Set<String>> = settings.revealedTreasurePins
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // État de la chasse en cours (null = pas de chasse) : distance, chaleur,
    // tendance, épingle révélée, trouvé.
    val hunt: StateFlow<HuntState?> =
        combine(huntedTreasure, currentPosition, collections, revealedPins, TrackingState.isTracking) { treasure, position, sets, pins, tracking ->
            if (treasure == null) return@combine null
            val found = sets.any { treasure.id in it.visitedIds }
            val distance = position?.let { distanceMeters(it, treasure.position) }
            val newTrend = if (distance != null) trendOf(lastHuntDistance, distance) else Trend.STEADY
            // La référence n'avance que sur un vrai déplacement, pas sur le bruit GPS,
            // et la dernière tendance reste affichée tant qu'aucun vrai mouvement ne la contredit.
            if (newTrend != Trend.STEADY || lastHuntDistance == null) lastHuntDistance = distance
            if (newTrend != Trend.STEADY) lastHuntTrend = newTrend
            val trend = lastHuntTrend
            HuntState(
                treasure = treasure,
                distanceMeters = distance,
                warmth = distance?.let { warmthFor(it) },
                trend = trend,
                pinRevealed = treasure.id in pins,
                found = found,
                liveTracking = tracking,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Lance la chasse si l'on est dans le quartier du trésor ; sinon dit pourquoi.
    suspend fun startHunt(treasure: Place): HuntStart {
        val parisGeo = geo.value ?: return HuntStart.NoPosition
        val position = currentPosition.value ?: lastKnownPosition(getApplication())
        val result = canStartHunt(treasure, position, parisGeo.quartiers)
        if (result == HuntStart.Started) {
            if (!TrackingState.isTracking.value) oneShotPosition.value = position
            lastHuntDistance = null
            lastHuntTrend = Trend.STEADY
            huntedTreasure.value = treasure
            _selectedDay.value = null
        }
        return result
    }

    // « Donner sa langue au chat » : l'épingle exacte apparaît (points inchangés).
    fun giveUpHunt() {
        val treasure = huntedTreasure.value ?: return
        viewModelScope.launch { settings.addRevealedTreasurePin(treasure.id) }
        _cameraMove.value = CameraMove.ToPosition(treasure.position, nextRequestId())
    }

    fun stopHunt() {
        huntedTreasure.value = null
        lastHuntDistance = null
        lastHuntTrend = Trend.STEADY
    }

    // Carte claire par défaut ; la sombre est un réglage.
    val mapLook: StateFlow<MapLook> = settings.darkMap
        .map { dark -> if (dark) MapLook.DARK else MapLook.LIGHT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapLook.LIGHT)

    fun setDarkMap(enabled: Boolean) {
        viewModelScope.launch { settings.setDarkMap(enabled) }
    }

    val fogImage: StateFlow<FogImage?> = combine(visitedCells, geo.filterNotNull(), mapLook) { cells, parisGeo, look ->
        buildFogImage(cells, parisGeo.cells, look)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val quartierOutlines: StateFlow<List<Quartier>> = geo
        .map { it?.quartiers?.quartiers.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _cameraMove = MutableStateFlow<CameraMove?>(null)
    val cameraMove: StateFlow<CameraMove?> = _cameraMove.asStateFlow()

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
                geo.value = ParisGeoCache.get(application)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Paris geography", e)
            }
        }
        viewModelScope.launch { centerOnUserIfInParis() }
    }

    // À l'ouverture : sur l'utilisateur s'il est dans Paris, sinon on reste
    // sur le centre de Paris (le reste du monde est noir, sans intérêt).
    private suspend fun centerOnUserIfInParis() {
        if (!hasLocationPermission()) return
        val position = lastKnownPosition(getApplication()) ?: return
        // Sert aussi à la carte « Prochain lieu » quand le suivi est arrêté.
        if (!TrackingState.isTracking.value) oneShotPosition.value = position
        val paris = parisBoundary.filterNotNull().first()
        if (paris.contains(position)) {
            _cameraMove.value = CameraMove.ToPosition(position, nextRequestId())
        }
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
            _cameraMove.value = CameraMove.ToPosition(position, nextRequestId())
        }
    }

    fun showPlace(place: Place) {
        _cameraMove.value = CameraMove.ToPosition(place.position, nextRequestId())
    }

    fun showQuartier(quartier: Quartier) {
        _cameraMove.value = CameraMove.ToArea(quartier.boundary, nextRequestId())
    }

    private fun nextRequestId(): Int = (_cameraMove.value?.requestId ?: 0) + 1

    private fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        getApplication(), Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    // À appeler une fois les permissions accordées.
    fun startTracking() {
        TrackingService.start(getApplication())
        viewModelScope.launch { settings.setTrackingEnabled(true) }
    }

    // Le service retient lui-même l'arrêt (il vient aussi de la notification).
    fun stopTracking() = TrackingService.stop(getApplication())

    // À chaque retour dans l'app : relance le suivi s'il doit tourner et s'est
    // arrêté (redémarrage du téléphone, app fermée par Android). Sans la permission
    // « toujours », seule l'app visible a le droit de le lancer.
    fun resumeTrackingIfWanted() {
        viewModelScope.launch {
            val wanted = settings.trackingEnabled.first() != false
            if (wanted && !TrackingState.isTracking.value && canTrack(getApplication())) startTracking()
        }
    }

    // Menus sombres par défaut ; la carte a son propre réglage (mapLook).
    val menuTheme: StateFlow<MenuTheme> = settings.menuTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, MenuTheme.DARK)

    fun setMenuTheme(theme: MenuTheme) {
        viewModelScope.launch { settings.setMenuTheme(theme) }
    }

    private companion object {
        const val TAG = "MapViewModel"
    }
}

// Même bleu nuit que les menus (Night.Background), sans l'opacité.
private fun buildFogImage(cells: Set<CellId>, city: CityCells, look: MapLook): FogImage {
    val extent = city.extent
    val strength = fogAlphaMask(cells, extent)
    // Couleur du brouillard du style, opacité = force du masque × opacité du style.
    // Couleur ARGB = alpha << 24 | RGB.
    val colors = IntArray(strength.size) { ((strength[it] * look.fogOpacity / FOG_ALPHA) shl 24) or look.fogRgb }
    val bitmap = Bitmap.createBitmap(
        colors,
        extent.width * FOG_PIXELS_PER_CELL,
        extent.height * FOG_PIXELS_PER_CELL,
        Bitmap.Config.ARGB_8888,
    )
    return FogImage(bitmap, extent)
}

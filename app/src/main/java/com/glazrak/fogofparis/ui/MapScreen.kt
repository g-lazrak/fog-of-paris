package com.glazrak.fogofparis.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.Gravity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import com.glazrak.fogofparis.domain.TrackingStatus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.glazrak.fogofparis.domain.Warmth
import com.glazrak.fogofparis.domain.Trend
import com.glazrak.fogofparis.domain.CollectionSet
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.ui.theme.Night
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private val PARIS_CENTER = LatLng(48.8566, 2.3522)
private const val INITIAL_ZOOM = 12.0
private const val RECENTER_ZOOM = 15.5

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onOpenProgress: () -> Unit,
    // Hauteur de la barre d'onglets posée sur le bas de la carte.
    bottomBarHeight: Dp,
) {
    val context = LocalContext.current
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val trackingStatus by viewModel.trackingStatus.collectAsStateWithLifecycle()

    // Android affiche les demandes l'une après l'autre (position, activité, notifications).
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Sans position précise ou sans détection d'activité, rien ne serait jamais révélé.
        // Sans notifications, le suivi marche quand même, la notification est juste masquée.
        when {
            !isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ->
                Toast.makeText(context, R.string.precise_location_needed, Toast.LENGTH_LONG).show()
            !hasActivityRecognition(context) ->
                Toast.makeText(context, R.string.activity_permission_needed, Toast.LENGTH_LONG).show()
            else -> viewModel.startTracking()
        }
    }
    val onToggleTracking = {
        if (isTracking) {
            viewModel.stopTracking()
        } else {
            val missing = requiredPermissions().filterNot { isGranted(context, it) }
            if (missing.isEmpty()) viewModel.startTracking() else permissionLauncher.launch(missing.toTypedArray())
        }
    }

    // "Me recentrer" : demande seulement la position (pas les autres permissions du suivi).
    val recenterPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            viewModel.recenter()
        } else {
            Toast.makeText(context, R.string.precise_location_needed, Toast.LENGTH_LONG).show()
        }
    }
    val onRecenter = {
        if (isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            viewModel.recenter()
        } else {
            recenterPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    val fogImage by viewModel.fogImage.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val quartierOutlines by viewModel.quartierOutlines.collectAsStateWithLifecycle()
    val placeMarkers by viewModel.placeMarkers.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val hunt by viewModel.hunt.collectAsStateWithLifecycle()
    val dayCells by viewModel.dayCells.collectAsStateWithLifecycle()
    val cameraMove by viewModel.cameraMove.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val parisBoundary by viewModel.parisBoundary.collectAsStateWithLifecycle()

    val mapView = rememberMapViewWithLifecycle()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { loadedMap ->
            loadedMap.cameraPosition = CameraPosition.Builder()
                .target(PARIS_CENTER)
                .zoom(INITIAL_ZOOM)
                .build()
            map = loadedMap
        }
    }
    // (Re)charge le style à l'ouverture et quand on change carte claire / sombre.
    // Les couches du jeu sont rajoutées, puis tous les effets liés à `style` se
    // relancent (brouillard, lieux, position…).
    val mapLook by viewModel.mapLook.collectAsStateWithLifecycle()
    LaunchedEffect(map, mapLook) {
        val loadedMap = map ?: return@LaunchedEffect
        style = null
        loadedMap.setStyle(Style.Builder().fromUri(mapLook.styleUrl)) { loadedStyle ->
            useFrenchLabels(loadedStyle)
            addGameLayers(loadedStyle)
            style = loadedStyle
        }
    }

    LaunchedEffect(style, fogImage) {
        val fog = fogImage ?: return@LaunchedEffect
        style?.let { updateFog(it, fog) }
    }
    // Marges autour d'un quartier affiché : étroites sur les côtés, plus
    // grandes en haut et en bas pour laisser la place au bandeau et aux boutons.
    val sidePaddingPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val verticalPaddingPx = with(LocalDensity.current) { 140.dp.roundToPx() }
    LaunchedEffect(map, cameraMove) {
        val move = cameraMove ?: return@LaunchedEffect
        val update = when (move) {
            is CameraMove.ToPosition -> CameraUpdateFactory.newLatLngZoom(
                LatLng(move.position.lat, move.position.lon),
                RECENTER_ZOOM,
            )
            is CameraMove.ToArea -> {
                val bounds = LatLngBounds.Builder()
                move.boundary.rings.flatMap { it.points }.forEach { bounds.include(LatLng(it.lat, it.lon)) }
                // Marges positionnelles (API Java) : left, top, right, bottom.
                CameraUpdateFactory.newLatLngBounds(
                    bounds.build(), sidePaddingPx, verticalPaddingPx, sidePaddingPx, verticalPaddingPx,
                )
            }
            is CameraMove.ToBounds -> {
                val bounds = LatLngBounds.Builder()
                    .include(LatLng(move.south, move.west))
                    .include(LatLng(move.north, move.east))
                    .build()
                // Marges positionnelles (API Java) : left, top, right, bottom.
                CameraUpdateFactory.newLatLngBounds(bounds, sidePaddingPx, verticalPaddingPx, sidePaddingPx, verticalPaddingPx)
            }
        }
        map?.animateCamera(update)
    }
    // L'épingle n'apparaît qu'après « Donner sa langue au chat », et plus une fois trouvé.
    val huntPin = hunt?.takeIf { it.pinRevealed && !it.found }?.treasure?.position
    LaunchedEffect(style, huntPin) {
        style?.let { updateHuntPin(it, huntPin) }
    }
    LaunchedEffect(style, dayCells) {
        style?.let { updateDayCells(it, dayCells) }
    }
    LaunchedEffect(style, placeMarkers) {
        style?.let { updatePlaces(it, placeMarkers) }
    }
    LaunchedEffect(style, quartierOutlines) {
        style?.let { updateQuartierOutlines(it, quartierOutlines) }
    }
    LaunchedEffect(style, parisBoundary) {
        val boundary = parisBoundary ?: return@LaunchedEffect
        style?.let { updateOutside(it, boundary) }
    }
    LaunchedEffect(style, currentPosition) {
        style?.let { updatePosition(it, currentPosition) }
    }

    // La carte passe sous les barres système ; on décale seulement les
    // éléments de MapLibre (logo, crédits, boussole) pour qu'ils restent visibles.
    val insets = WindowInsets.safeDrawing
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insetsPx = SystemInsetsPx(
        left = insets.getLeft(density, layoutDirection),
        top = insets.getTop(density),
        right = insets.getRight(density, layoutDirection),
        bottom = insets.getBottom(density) + with(density) { bottomBarHeight.roundToPx() },
    )
    val basePx = with(density) { ORNAMENT_MARGIN.roundToPx() }
    LaunchedEffect(map, insetsPx) {
        map?.let { applyOrnamentMargins(it, insetsPx, basePx) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        val topBannerModifier = Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 12.dp)
        val day = selectedDay
        val currentHunt = hunt
        if (currentHunt != null) {
            HuntBanner(
                hunt = currentHunt,
                onGiveUp = viewModel::giveUpHunt,
                onClose = viewModel::stopHunt,
                modifier = topBannerModifier,
            )
        } else if (day != null) {
            DayBanner(
                day = day,
                cellCount = dayCells.size,
                onPrevious = { viewModel.shiftDay(forward = false) },
                onNext = { viewModel.shiftDay(forward = true) },
                onClose = viewModel::clearDay,
                modifier = topBannerModifier,
            )
        } else {
            summary?.let { SummaryBanner(summary = it, onClick = onOpenProgress, modifier = topBannerModifier) }
        }
        SmallFloatingActionButton(
            onClick = onRecenter,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                // Au-dessus du bouton (i) des crédits de la carte.
                .padding(end = 16.dp, bottom = bottomBarHeight + 48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_my_location),
                contentDescription = stringResource(R.string.recenter),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = bottomBarHeight + 20.dp),
        ) {
            trackingStatus?.let { StatusLabel(it) }
            TrackingButton(isTracking = isTracking, onClick = onToggleTracking)
        }
    }
}

// Bandeau du haut ; un appui ouvre l'écran « Progrès ».
@Composable
private fun SummaryBanner(summary: GameSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        // Le brouillard est du même bleu nuit : le bord détache le bandeau.
        border = BorderStroke(1.dp, Night.BorderStrong),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.progress_label,
                    summary.level.level.title,
                    formatPercent(summary.percent),
                    formatPoints(summary.points),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                painter = painterResource(R.drawable.ic_trophy),
                contentDescription = stringResource(R.string.open_quartiers),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StatusLabel(status: TrackingStatus) {
    val text = stringResource(
        when (status) {
            TrackingStatus.REVEALING -> R.string.status_revealing
            TrackingStatus.SEARCHING_GPS -> R.string.status_searching_gps
            TrackingStatus.WAITING_DETECTION -> R.string.status_waiting_detection
            TrackingStatus.PAUSED_STILL -> R.string.status_paused_still
            TrackingStatus.PAUSED_VEHICLE -> R.string.status_paused_vehicle
            TrackingStatus.PAUSED_BICYCLE -> R.string.status_paused_bicycle
        }
    )
    // Vert uniquement quand des cases peuvent se révéler, gris sinon.
    val dotColor = if (status == TrackingStatus.REVEALING) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TrackingButton(isTracking: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_notification_walk),
                contentDescription = null,
            )
        },
        text = { Text(stringResource(if (isTracking) R.string.tracking_stop else R.string.tracking_start)) },
        containerColor = if (isTracking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (isTracking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

// Activité physique : à demander depuis Android 10 ; notifications : depuis Android 13.
// Avant, ces permissions sont accordées à l'installation.
private fun requiredPermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

private fun hasActivityRecognition(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        isGranted(context, Manifest.permission.ACTIVITY_RECOGNITION)

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private val ORNAMENT_MARGIN = 8.dp

private data class SystemInsetsPx(val left: Int, val top: Int, val right: Int, val bottom: Int)

private fun applyOrnamentMargins(
    map: MapLibreMap,
    insets: SystemInsetsPx,
    basePx: Int,
) {
    val ui = map.uiSettings
    val bottom = insets.bottom + basePx
    // Le logo MapLibre est facultatif (licence BSD) : on le retire. Les crédits
    // OpenStreetMap / OpenFreeMap, eux, sont obligatoires : bouton (i) en bas à droite.
    ui.isLogoEnabled = false
    // Marges positionnelles (API Java) : left, top, right, bottom.
    ui.attributionGravity = Gravity.BOTTOM or Gravity.END
    ui.setAttributionMargins(0, 0, insets.right + basePx, bottom)
    ui.setCompassMargins(0, insets.top + basePx, insets.right + basePx, 0)
}

// Crée la MapView une seule fois et relaie les étapes de vie de l'écran
// (démarrage, pause…) dont MapLibre a besoin pour libérer ses ressources.
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    return mapView
}

private val DAY_FORMAT: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.FRANCE)

// Bandeau du journal : le jour affiché, ‹ › pour changer de jour, ✕ pour revenir à la vue normale.
@Composable
private fun DayBanner(
    day: java.time.LocalDate,
    cellCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, Night.Gold),
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onPrevious) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.day_previous))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    day.format(DAY_FORMAT).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    pluralStringResource(R.plurals.day_new_cells, cellCount, cellCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = Night.Gold,
                )
            }
            IconButton(onClick = onNext) {
                // La même flèche retournée : « jour suivant ».
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.day_next),
                    modifier = Modifier.rotate(180f),
                )
            }
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.day_close))
            }
        }
    }
}

// Bandeau de la chasse au trésor : indice, chaud / froid, tendance, et
// « Donner sa langue au chat » pour voir l'épingle.
@Composable
private fun HuntBanner(hunt: HuntState, onGiveUp: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val treasureColor = CollectionSet.TREASURES.color
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, treasureColor),
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_treasure), contentDescription = null, tint = treasureColor, modifier = Modifier.size(20.dp))
                Text(
                    if (hunt.found) stringResource(R.string.hunt_found, hunt.treasure.name) else stringResource(R.string.hunt_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = treasureColor,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.hunt_stop))
                }
            }
            if (!hunt.found) {
                Text(hunt.treasure.hint.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Night.TextSoft, modifier = Modifier.padding(end = 12.dp))
                val warmth = hunt.warmth
                val distance = hunt.distanceMeters
                if (warmth != null && distance != null) {
                    Text(
                        "${stringResource(warmthLabel(warmth))} · ${formatDistance(distance)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = warmthColor(warmth),
                    )
                    trendLabel(hunt.trend)?.let {
                        Text(stringResource(it), style = MaterialTheme.typography.labelLarge, color = Night.TextSoft)
                    }
                }
                if (!hunt.liveTracking) {
                    Text(stringResource(R.string.hunt_needs_tracking), style = MaterialTheme.typography.labelMedium, color = Night.TextMuted, modifier = Modifier.padding(end = 12.dp))
                }
                if (!hunt.pinRevealed) {
                    OutlinedButton(onClick = onGiveUp, border = BorderStroke(1.dp, Night.BorderStrong)) {
                        Text(stringResource(R.string.hunt_give_up), color = Night.Text)
                    }
                }
            }
        }
    }
}

private fun warmthLabel(warmth: Warmth): Int = when (warmth) {
    Warmth.BURNING -> R.string.warmth_burning
    Warmth.VERY_HOT -> R.string.warmth_very_hot
    Warmth.HOT -> R.string.warmth_hot
    Warmth.WARM -> R.string.warmth_warm
    Warmth.COLD -> R.string.warmth_cold
}

// Du bleu (froid) au rouge (brûlant) : se lit d'un coup d'œil.
private fun warmthColor(warmth: Warmth): Color = when (warmth) {
    Warmth.BURNING -> Color(0xFFFF5A36)
    Warmth.VERY_HOT -> Color(0xFFFF8A3D)
    Warmth.HOT -> Color(0xFFF2B33D)
    Warmth.WARM -> Color(0xFFE6D27A)
    Warmth.COLD -> Color(0xFF8FB8FF)
}

private fun trendLabel(trend: Trend): Int? = when (trend) {
    Trend.CLOSER -> R.string.trend_closer
    Trend.FARTHER -> R.string.trend_farther
    Trend.STEADY -> null
}

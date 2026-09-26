package com.glazrak.fogofparis.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.Gravity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private val PARIS_CENTER = LatLng(48.8566, 2.3522)
private const val INITIAL_ZOOM = 12.0

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasLocationPermission = isGranted }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.startTracking()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val visitedCells by viewModel.visitedCells.collectAsStateWithLifecycle()
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
            loadedMap.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { loadedStyle ->
                addGameLayers(loadedStyle)
                style = loadedStyle
            }
            map = loadedMap
        }
    }

    LaunchedEffect(style, visitedCells) {
        style?.let { updateFog(it, visitedCells) }
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
        bottom = insets.getBottom(density),
    )
    val basePx = with(density) { ORNAMENT_MARGIN.roundToPx() }
    LaunchedEffect(map, insetsPx) {
        map?.let { applyOrnamentMargins(it, insetsPx, basePx) }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

private val ORNAMENT_MARGIN = 8.dp

private data class SystemInsetsPx(val left: Int, val top: Int, val right: Int, val bottom: Int)

private fun applyOrnamentMargins(
    map: MapLibreMap,
    insets: SystemInsetsPx,
    basePx: Int,
) {
    val ui = map.uiSettings
    val bottom = insets.bottom + basePx
    // Marges positionnelles (API Java) : left, top, right, bottom.
    ui.setLogoMargins(insets.left + basePx, 0, 0, bottom)
    // Les crédits OpenStreetMap sont obligatoires : bouton (i) en bas à droite.
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

package com.glazrak.fogofparis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.glazrak.fogofparis.ui.theme.FogOfParisTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import org.osmdroid.views.overlay.Marker


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//      Configuration osmdroid : User-Agent pour identifier l'app
//      auprès des serveurs de tuiles (politesse + obligation OSM)
        Configuration.getInstance().userAgentValue = packageName

        setContent {
            FogOfParisTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ParisMap(
                        modifier = Modifier.fillMaxSize().padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ParisMap(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // État: a-t-on la permission de localisation ? Initialisé en interrogeant Android.
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    // Launcher pour afficher la popup système et recevoir la réponse de l'utilisateur.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasLocationPermission = isGranted
    }

    // Au premier affichage du composable: demander la permission si on ne l'a pas encore.
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // État: dernière position connue de l'utilisateur (null tant qu'on n'a rien reçu).
    var currentLocation by remember {
        mutableStateOf<GeoPoint?>(null)
    }

    // Service Android qui fournit la localisation.
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    // Callback appelé par Android à chaque mise à jour de position.
    val locationListener = remember {
        LocationListener { location ->
            currentLocation = GeoPoint(location.latitude, location.longitude)
        }
    }

    // Abonnement aux mises à jour de localisation seulement si on a la permission
    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,                   // minimum 2 secondes entre updates
                    5f,                       // minimum 5 mètres entre updates
                    locationListener
                )
            } catch (e: SecurityException) {
                Log.e("ParisMap", "Permission revoked", e)
            }
        }
        onDispose {
            locationManager.removeUpdates(locationListener)
        }
    }

    // On crée la MapView une seule fois et on la mémorise au fil des recompositions
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(48.8566, 2.3522)) // Paris
        }
    }

    // Lier le cycle de vie de la MapView à celui du composable
    // (osmdroid a des ressources à libérer/réactiver selon onResume/onPause).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }



    AndroidView(
        factory = { mapView },
        // Re-exécuté à chaque changement de state lu ici (currentLocation).
        // On efface les overlays, puis on (ré)ajoute un marqueur si position connue.
        update = { view ->
            view.overlays.clear()
            // Le ?.let donne un non-null garanti qu'on assigne à location
            currentLocation?.let { location ->
                val myPositionMarker = Marker(view).apply {
                    position = location
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Mistabibi"
                }
                view.overlays.add(myPositionMarker)
            }
            view.invalidate()
        },
        modifier = modifier
    )
}
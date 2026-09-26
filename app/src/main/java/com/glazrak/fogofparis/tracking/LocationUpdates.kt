package com.glazrak.fogofparis.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.glazrak.fogofparis.domain.GeoPosition
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Positions GPS tant que l'app est ouverte. Provisoire : remplacé en phase 4
// par un service de localisation qui tourne aussi en arrière-plan.
// L'appelant doit avoir vérifié la permission ACCESS_FINE_LOCATION.
@SuppressLint("MissingPermission")
fun locationUpdates(context: Context): Flow<GeoPosition> = callbackFlow {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener = LocationListener { location ->
        trySend(GeoPosition(lat = location.latitude, lon = location.longitude))
    }
    try {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            2000L, // minimum 2 secondes entre updates
            5f,    // minimum 5 mètres entre updates
            listener,
            Looper.getMainLooper(),
        )
    } catch (e: SecurityException) {
        Log.e("LocationUpdates", "Location permission revoked", e)
        close()
    }
    awaitClose { locationManager.removeUpdates(listener) }
}

package com.glazrak.fogofparis.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.glazrak.fogofparis.domain.GeoPosition
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Dernière position connue du téléphone (souvent déjà en mémoire, donc
// instantanée et sans allumer le GPS). null si inconnue ou sans permission.
@SuppressLint("MissingPermission") // SecurityException gérée ci-dessous.
suspend fun lastKnownPosition(context: Context): GeoPosition? = try {
    suspendCancellableCoroutine { continuation ->
        LocationServices.getFusedLocationProviderClient(context).lastLocation
            .addOnSuccessListener { location ->
                continuation.resume(location?.let { GeoPosition(lat = it.latitude, lon = it.longitude) })
            }
            .addOnFailureListener { error ->
                Log.e("LastKnownPosition", "Could not read last location", error)
                continuation.resume(null)
            }
    }
} catch (e: SecurityException) {
    Log.e("LastKnownPosition", "Location permission missing", e)
    null
}

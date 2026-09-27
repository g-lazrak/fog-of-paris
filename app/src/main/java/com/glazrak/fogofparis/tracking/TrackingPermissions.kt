package com.glazrak.fogofparis.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

// Permissions demandées pour le suivi. Activité physique : à demander depuis
// Android 10 ; notifications : depuis Android 13. Avant, elles sont accordées
// à l'installation.
fun trackingPermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

// Sans position précise ou sans détection d'activité, rien ne serait jamais révélé.
// Les notifications, elles, sont facultatives.
fun canTrack(context: Context): Boolean =
    isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) && hasActivityRecognition(context)

fun hasActivityRecognition(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        isGranted(context, Manifest.permission.ACTIVITY_RECOGNITION)

fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

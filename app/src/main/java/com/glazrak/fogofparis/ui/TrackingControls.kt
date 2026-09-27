package com.glazrak.fogofparis.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.tracking.hasActivityRecognition
import com.glazrak.fogofparis.tracking.isGranted
import com.glazrak.fogofparis.tracking.trackingPermissions

// Renvoie l'action « allumer le suivi » : demande d'abord les permissions qui
// manquent (Android les affiche l'une après l'autre), puis lance le suivi.
// Partagée par la pastille de la carte et l'interrupteur des réglages.
@Composable
fun rememberTrackingStarter(viewModel: MapViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Sans notifications, le suivi marche quand même, la notification est juste masquée.
        when {
            !isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ->
                Toast.makeText(context, R.string.precise_location_needed, Toast.LENGTH_LONG).show()
            !hasActivityRecognition(context) ->
                Toast.makeText(context, R.string.activity_permission_needed, Toast.LENGTH_LONG).show()
            else -> viewModel.startTracking()
        }
    }
    return {
        val missing = trackingPermissions().filterNot { isGranted(context, it) }
        if (missing.isEmpty()) viewModel.startTracking() else launcher.launch(missing.toTypedArray())
    }
}

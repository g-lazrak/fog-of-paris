package com.glazrak.fogofparis.tracking

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.glazrak.fogofparis.MainActivity
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.data.ParisGeoCache
import com.glazrak.fogofparis.data.VisitedCellsRepository
import com.glazrak.fogofparis.domain.CellId
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.LocationFix
import com.glazrak.fogofparis.domain.MAX_ACCURACY_METERS
import com.glazrak.fogofparis.domain.Movement
import com.glazrak.fogofparis.domain.latLonToCell
import com.glazrak.fogofparis.domain.rejectionReason
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Service "de premier plan" : Android le laisse tourner écran éteint et app
// fermée tant qu'une notification permanente est affichée. C'est lui qui
// reçoit le GPS et enregistre les cellules révélées.
class TrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val repository by lazy { VisitedCellsRepository.create(this) }
    private val medalNotifier by lazy { MedalNotifier(this, repository) }
    private val processingMutex = Mutex()

    private var previousFix: LocationFix? = null
    private var lastRecordedCell: CellId? = null
    private var isRunning = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { handleLocation(it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTracking()
            else -> startTracking()
        }
        // Pas de redémarrage automatique par Android : sans la permission
        // "toujours", il n'aurait de toute façon pas le droit à la position.
        return START_NOT_STICKY
    }

    private fun startTracking() {
        if (isRunning) return
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            // Ex. permission retirée entre-temps : on abandonne proprement.
            Log.e(TAG, "Could not start foreground tracking", e)
            stopSelf()
            return
        }
        isRunning = true
        TrackingState.setTracking(true)
        ActivityTransitionReceiver.register(this)
        // Change le rythme du GPS à chaque changement d'activité (économie de batterie).
        scope.launch {
            TrackingState.movement
                .map { it == Movement.ON_FOOT }
                .distinctUntilChanged()
                .collect { onFoot -> requestLocationUpdates(onFoot) }
        }
    }

    @SuppressLint("MissingPermission") // Vérifiée par l'écran avant de démarrer le service.
    private fun requestLocationUpdates(onFoot: Boolean) {
        val request = if (onFoot) {
            // GPS précis toutes les ~5 s : nécessaire pour des cellules de 50 m.
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, ON_FOOT_INTERVAL_MS)
                .setMinUpdateIntervalMillis(ON_FOOT_FASTEST_INTERVAL_MS)
                .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_M)
                .build()
        } else {
            // Pas à pied : rien à révéler, donc aucun GPS allumé par nous (le suivi
            // peut rester actif en permanence). "Passif" = on reçoit seulement les
            // positions déjà demandées par d'autres apps : coût nul, garde le point à jour.
            LocationRequest.Builder(Priority.PRIORITY_PASSIVE, PAUSED_INTERVAL_MS)
                .build()
        }
        try {
            // Remplace la demande précédente (même callback).
            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
            stopTracking()
        }
    }

    private fun handleLocation(location: Location) {
        val fix = LocationFix(
            position = GeoPosition(lat = location.latitude, lon = location.longitude),
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
            timeMillis = location.time,
        )
        val isPrecise = fix.accuracyMeters != null && fix.accuracyMeters <= MAX_ACCURACY_METERS
        TrackingState.setPosition(fix.position, isPrecise)
        // Activité au moment de la position, pas au moment du traitement.
        val movement = TrackingState.movement.value
        val previous = previousFix
        previousFix = fix
        scope.launch {
            // Une position à la fois, dans l'ordre : les comptes des médailles restent justes.
            processingMutex.withLock { processFix(fix, previous, movement) }
        }
    }

    private suspend fun processFix(fix: LocationFix, previous: LocationFix?, movement: Movement) {
        val geo = ParisGeoCache.get(applicationContext)
        val rejection = rejectionReason(fix, previous, geo.boundary, movement)
        if (rejection != null) {
            Log.d(TAG, "Fix ignored: $rejection")
            return
        }
        val cell = latLonToCell(fix.position.lat, fix.position.lon)
        // Évite une écriture en base tant qu'on reste dans la même cellule.
        if (cell == lastRecordedCell) return
        lastRecordedCell = cell
        try {
            medalNotifier.ensureLoaded(geo)
            val isNew = repository.recordVisit(cell, timeMillis = fix.timeMillis)
            if (isNew) medalNotifier.onNewCell(cell, geo)
        } catch (e: Exception) {
            Log.e(TAG, "Could not save visited cell $cell", e)
        }
    }

    private fun stopTracking() {
        locationClient.removeLocationUpdates(locationCallback)
        ActivityTransitionReceiver.unregister(this)
        isRunning = false
        TrackingState.setTracking(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationClient.removeLocationUpdates(locationCallback)
        ActivityTransitionReceiver.unregister(this)
        TrackingState.setTracking(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.tracking_channel_name),
                    // Discrète : pas de son ni de vibration.
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_walk)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.tracking_notification_stop), stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "TrackingService"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.glazrak.fogofparis.action.STOP_TRACKING"
        private const val ON_FOOT_INTERVAL_MS = 5_000L
        private const val ON_FOOT_FASTEST_INTERVAL_MS = 2_000L
        private const val PAUSED_INTERVAL_MS = 60_000L
        private const val MIN_UPDATE_DISTANCE_M = 5f

        // À appeler depuis l'écran, app visible, une fois la permission accordée.
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }
    }
}

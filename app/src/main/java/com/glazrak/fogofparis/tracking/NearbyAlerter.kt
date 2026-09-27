package com.glazrak.fogofparis.tracking

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.glazrak.fogofparis.MainActivity
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.data.ParisGeo
import com.glazrak.fogofparis.data.SettingsStore
import com.glazrak.fogofparis.domain.CellEntry
import com.glazrak.fogofparis.domain.FAMILIAR_WINDOW_MS
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.Place
import com.glazrak.fogofparis.domain.isFamiliarGround
import com.glazrak.fogofparis.domain.nearestUnvisited
import com.glazrak.fogofparis.domain.placeToAnnounce
import com.glazrak.fogofparis.ui.formatDistance
import kotlinx.coroutines.flow.first

// Alerte « lieu proche » (réglage désactivé par défaut) : quand on marche en
// terrain connu, vibre et signale un lieu de collection pas encore visité.
class NearbyAlerter(private val context: Context, private val settings: SettingsStore) {

    private val entries = ArrayDeque<CellEntry>()
    private var lastAlertMillis: Long? = null

    // Appelé à chaque changement de cellule pendant la marche.
    suspend fun onCellEntered(
        wasNew: Boolean,
        position: GeoPosition,
        timeMillis: Long,
        geo: ParisGeo,
        visitedPlaceIds: Set<String>,
    ) {
        entries.addLast(CellEntry(timeMillis, wasNew))
        while (entries.isNotEmpty() && timeMillis - entries.first().timeMillis > FAMILIAR_WINDOW_MS) entries.removeFirst()

        if (!settings.nearbyAlerts.first()) return
        val nearest = nearestUnvisited(geo.places.places, visitedPlaceIds, position)
        val place = placeToAnnounce(
            nearest = nearest,
            familiar = isFamiliarGround(entries.toList(), timeMillis),
            alreadyAnnouncedIds = settings.announcedPlaces.first(),
            lastAlertMillis = lastAlertMillis,
            nowMillis = timeMillis,
        ) ?: return
        lastAlertMillis = timeMillis
        settings.addAnnouncedPlace(place.id)
        notify(place, nearest?.distanceMeters ?: 0.0)
    }

    // Vérifié via areNotificationsEnabled, valable sur toutes les versions d'Android.
    @SuppressLint("MissingPermission")
    private fun notify(place: Place, distanceMeters: Double) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.d(TAG, "Notifications not allowed, nearby place not shown: ${place.name}")
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.nearby_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                // Vibration oui (choix du joueur en activant le réglage), son non.
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 120, 180)
            }
        )
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pin)
            .setContentTitle(context.getString(R.string.nearby_title, place.name))
            .setContentText(context.getString(R.string.nearby_text, formatDistance(distanceMeters)))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val TAG = "NearbyAlerter"
        const val CHANNEL_ID = "nearby"
        const val NOTIFICATION_ID = 4000
    }
}

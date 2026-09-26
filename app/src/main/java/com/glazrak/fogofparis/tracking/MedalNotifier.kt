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
import com.glazrak.fogofparis.data.VisitedCellsRepository
import com.glazrak.fogofparis.domain.CellId
import com.glazrak.fogofparis.domain.Medal
import com.glazrak.fogofparis.domain.newMedal
import com.glazrak.fogofparis.ui.emoji
import com.glazrak.fogofparis.ui.label

// Prévient (sans son) quand une nouvelle cellule fait gagner une médaille.
// Garde en mémoire le nombre de cellules révélées par quartier pour ne pas
// relire toute la base à chaque pas.
class MedalNotifier(
    private val context: Context,
    private val repository: VisitedCellsRepository,
) {
    private var revealedPerQuartier: MutableMap<Int, Int>? = null

    // À appeler AVANT d'enregistrer la nouvelle cellule, pour partir du bon compte.
    suspend fun ensureLoaded(geo: ParisGeo) {
        if (revealedPerQuartier != null) return
        val counts = HashMap<Int, Int>()
        for (visit in repository.currentVisits()) {
            val quartier = geo.quartiers.quartierOf(visit.cell) ?: continue
            counts[quartier.id] = (counts[quartier.id] ?: 0) + 1
        }
        revealedPerQuartier = counts
    }

    fun onNewCell(cell: CellId, geo: ParisGeo) {
        val counts = revealedPerQuartier ?: return
        val quartier = geo.quartiers.quartierOf(cell) ?: return
        val before = counts[quartier.id] ?: 0
        counts[quartier.id] = before + 1
        val medal = newMedal(before, before + 1, quartier.cells.totalCells) ?: return
        notify(medal, quartier.name, quartier.id)
    }

    // Vérifié via areNotificationsEnabled, valable sur toutes les versions d'Android.
    @SuppressLint("MissingPermission")
    private fun notify(medal: Medal, quartierName: String, quartierId: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.d(TAG, "Notifications not allowed, medal ${medal.name} for $quartierName not shown")
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.medal_channel_name),
                // Discrète : pas de son ni de vibration.
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trophy)
            .setContentTitle(
                context.getString(R.string.medal_notification_title, medal.emoji, medal.label(context), quartierName)
            )
            .setContentText(context.getString(R.string.medal_notification_text, medal.thresholdPercent))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        // Un identifiant par quartier : la médaille suivante remplace la précédente.
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + quartierId, notification)
    }

    private companion object {
        const val TAG = "MedalNotifier"
        const val CHANNEL_ID = "medals"
        const val NOTIFICATION_ID_BASE = 1000
    }
}

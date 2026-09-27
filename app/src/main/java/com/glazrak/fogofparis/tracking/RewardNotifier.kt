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
import com.glazrak.fogofparis.domain.CollectionSet
import com.glazrak.fogofparis.domain.Medal
import com.glazrak.fogofparis.domain.POINTS_PER_ARRONDISSEMENT_BADGE
import com.glazrak.fogofparis.domain.POINTS_PER_CELL
import com.glazrak.fogofparis.domain.cellsNeededFor
import com.glazrak.fogofparis.domain.POINTS_PER_COMPLETED_SET
import com.glazrak.fogofparis.domain.POINTS_PER_MEDAL
import com.glazrak.fogofparis.domain.collectionProgress
import com.glazrak.fogofparis.domain.levelFor
import com.glazrak.fogofparis.domain.medalsCrossed
import com.glazrak.fogofparis.domain.totalPoints
import com.glazrak.fogofparis.ui.arrondissementLabel
import com.glazrak.fogofparis.ui.emoji
import com.glazrak.fogofparis.ui.label

// Prévient (sans son) quand une nouvelle cellule fait gagner quelque chose :
// médaille de quartier, lieu d'une collection, série complète, niveau.
// Garde les compteurs en mémoire pour ne pas relire toute la base à chaque pas.
class RewardNotifier(
    private val context: Context,
    private val repository: VisitedCellsRepository,
) {
    private class Counters(
        val visitedCells: HashSet<CellId>,
        val revealedPerQuartier: HashMap<Int, Int>,
        val visitedPlaceIds: HashSet<String>,
        var points: Int,
    )

    private var counters: Counters? = null

    // À appeler AVANT d'enregistrer la nouvelle cellule, pour partir du bon compte.
    suspend fun ensureLoaded(geo: ParisGeo) {
        if (counters != null) return
        val visits = repository.currentVisits()
        val cells = visits.mapTo(HashSet()) { it.cell }
        val quartierProgress = geo.quartiers.progress(visits)
        val collections = collectionProgress(geo.places.places, cells)
        counters = Counters(
            visitedCells = cells,
            revealedPerQuartier = quartierProgress.associateTo(HashMap()) { it.quartier.id to it.revealedCells },
            visitedPlaceIds = collections.flatMapTo(HashSet()) { it.visitedIds },
            points = totalPoints(geo.cells.countInside(cells), quartierProgress, collections),
        )
    }

    fun visitedPlaceIds(): Set<String> = counters?.visitedPlaceIds.orEmpty()

    fun onNewCell(cell: CellId, geo: ParisGeo) {
        val state = counters ?: return
        val pointsBefore = state.points
        state.visitedCells += cell
        if (geo.cells.contains(cell)) state.points += POINTS_PER_CELL

        geo.quartiers.quartierOf(cell)?.let { quartier ->
            val before = state.revealedPerQuartier[quartier.id] ?: 0
            state.revealedPerQuartier[quartier.id] = before + 1
            val medals = medalsCrossed(before, before + 1, quartier.cells.totalCells)
            state.points += medals.size * POINTS_PER_MEDAL
            medals.lastOrNull()?.let { medal ->
                notify(
                    id = MEDAL_ID_BASE + quartier.id,
                    title = context.getString(R.string.medal_notification_title, medal.emoji, medal.label(context), quartier.name),
                    text = context.getString(R.string.medal_notification_text, medal.thresholdPercent),
                )
            }
            // Ce Bronze était-il le dernier qui manquait à l'arrondissement ?
            if (Medal.BRONZE in medals) {
                val siblings = geo.quartiers.quartiers.filter { it.arrondissement == quartier.arrondissement }
                val allBronze = siblings.all { sibling ->
                    (state.revealedPerQuartier[sibling.id] ?: 0) >= cellsNeededFor(Medal.BRONZE, sibling.cells.totalCells)
                }
                if (allBronze) {
                    state.points += POINTS_PER_ARRONDISSEMENT_BADGE
                    notify(
                        id = BADGE_ID_BASE + quartier.arrondissement,
                        title = context.getString(R.string.badge_title, arrondissementLabel(context, quartier.arrondissement)),
                        text = context.getString(R.string.badge_text, POINTS_PER_ARRONDISSEMENT_BADGE),
                    )
                }
            }
        }

        for (place in geo.places.placesNear(cell)) {
            if (!state.visitedPlaceIds.add(place.id)) continue
            state.points += place.set.pointsPerPlace
            val setPlaces = geo.places.places.filter { it.set == place.set }
            val visitedInSet = setPlaces.count { it.id in state.visitedPlaceIds }
            if (place.set.hidden) {
                // Trésor : on révèle enfin son nom. Pas de bonus de série.
                notify(
                    id = PLACE_ID,
                    title = context.getString(R.string.treasure_found_title, place.name),
                    text = context.getString(R.string.treasure_found_text, place.set.pointsPerPlace, visitedInSet, setPlaces.size),
                )
            } else if (visitedInSet == setPlaces.size) {
                state.points += POINTS_PER_COMPLETED_SET
                notify(
                    id = SET_ID_BASE + place.set.ordinal,
                    title = context.getString(R.string.set_complete_title, place.set.emoji, setName(place.set)),
                    text = context.getString(R.string.set_complete_text, POINTS_PER_COMPLETED_SET),
                )
            } else {
                notify(
                    id = PLACE_ID,
                    title = context.getString(R.string.place_found_title, place.set.emoji, place.name),
                    text = context.getString(R.string.place_found_text, setName(place.set), visitedInSet, setPlaces.size),
                )
            }
        }

        val levelBefore = levelFor(pointsBefore).level
        val levelAfter = levelFor(state.points).level
        if (levelAfter.number > levelBefore.number) {
            notify(
                id = LEVEL_ID,
                title = context.getString(R.string.level_up_title, levelAfter.title),
                text = context.getString(R.string.level_up_text, levelAfter.number),
            )
        }
    }

    private fun setName(set: CollectionSet): String = context.getString(collectionNameRes(set))

    // Vérifié via areNotificationsEnabled, valable sur toutes les versions d'Android.
    @SuppressLint("MissingPermission")
    private fun notify(id: Int, title: String, text: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.d(TAG, "Notifications not allowed, reward not shown: $title")
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reward_channel_name),
                // Discrète : pas de son ni de vibration.
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        // Ancien canal de la phase 7, remplacé par celui-ci.
        manager.deleteNotificationChannel(OLD_MEDAL_CHANNEL_ID)
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trophy)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private companion object {
        const val TAG = "RewardNotifier"
        const val CHANNEL_ID = "rewards"
        const val OLD_MEDAL_CHANNEL_ID = "medals"
        // Un identifiant par quartier / série : la récompense suivante remplace la précédente.
        const val MEDAL_ID_BASE = 1000
        const val SET_ID_BASE = 2000
        const val BADGE_ID_BASE = 2500
        const val PLACE_ID = 3000
        const val LEVEL_ID = 3001
    }
}

fun collectionNameRes(set: CollectionSet): Int = when (set) {
    CollectionSet.BRIDGES -> R.string.set_bridges
    CollectionSet.TOWNHALLS -> R.string.set_townhalls
    CollectionSet.PASSAGES -> R.string.set_passages
    CollectionSet.MONUMENTS -> R.string.set_monuments
    CollectionSet.PARKS -> R.string.set_parks
    CollectionSet.SQUARES -> R.string.set_squares
    CollectionSet.STATIONS -> R.string.set_stations
    CollectionSet.TREASURES -> R.string.set_treasures
}

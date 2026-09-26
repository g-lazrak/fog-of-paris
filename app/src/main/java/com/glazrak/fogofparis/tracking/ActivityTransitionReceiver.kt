package com.glazrak.fogofparis.tracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.glazrak.fogofparis.domain.Movement
import com.glazrak.fogofparis.domain.MovementTransition
import com.glazrak.fogofparis.domain.TransitionKind
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

// Reçoit d'Android les changements d'activité (on se met à marcher, on monte
// dans un véhicule…). La détection utilise les capteurs de mouvement, pas le
// GPS : elle distingue un bus au pas d'un piéton grâce aux vibrations.
class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val transitions = result.transitionEvents.mapNotNull { event ->
            val movement = movementOf(event.activityType) ?: return@mapNotNull null
            val kind = if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                TransitionKind.ENTER
            } else {
                TransitionKind.EXIT
            }
            MovementTransition(movement, kind)
        }
        TrackingState.applyMovementTransitions(transitions)
        Log.d(TAG, "Transitions $transitions -> ${TrackingState.movement.value}")
    }

    companion object {
        private const val TAG = "ActivityTransitions"

        private val WATCHED_ACTIVITIES = listOf(
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.STILL,
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
        )

        private fun movementOf(activityType: Int): Movement? = when (activityType) {
            DetectedActivity.WALKING, DetectedActivity.RUNNING -> Movement.ON_FOOT
            DetectedActivity.STILL -> Movement.STILL
            DetectedActivity.IN_VEHICLE -> Movement.VEHICLE
            DetectedActivity.ON_BICYCLE -> Movement.BICYCLE
            else -> null
        }

        private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ActivityTransitionReceiver::class.java),
            // MUTABLE obligatoire : les services Google ajoutent les résultats à l'intent.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        // La permission "activité physique" est vérifiée par l'écran avant le démarrage.
        @SuppressLint("MissingPermission")
        fun register(context: Context) {
            val transitions = WATCHED_ACTIVITIES.flatMap { activity ->
                listOf(
                    ActivityTransition.ACTIVITY_TRANSITION_ENTER,
                    ActivityTransition.ACTIVITY_TRANSITION_EXIT,
                ).map { type ->
                    ActivityTransition.Builder()
                        .setActivityType(activity)
                        .setActivityTransition(type)
                        .build()
                }
            }
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent(context))
                .addOnFailureListener { Log.e(TAG, "Could not register activity transitions", it) }
        }

        @SuppressLint("MissingPermission")
        fun unregister(context: Context) {
            ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(pendingIntent(context))
                .addOnFailureListener { Log.e(TAG, "Could not unregister activity transitions", it) }
        }
    }
}

package com.glazrak.fogofparis.domain

// Règles de l'alerte « lieu proche » (validées par le propriétaire, 2026-09-27) :
// seulement quand on marche en terrain connu (trajet quotidien, courses…), jamais
// quand on explore de nouvelles rues — là, on est déjà concentré sur la balade.

// Une entrée dans une cellule pendant la marche, et si elle était nouvelle.
data class CellEntry(val timeMillis: Long, val wasNew: Boolean)

const val FAMILIAR_WINDOW_MS = 10 * 60 * 1000L
const val FAMILIAR_MIN_ENTRIES = 8
const val FAMILIAR_MAX_NEW_SHARE = 0.2
const val NEARBY_ALERT_RADIUS_M = 200.0
const val NEARBY_ALERT_COOLDOWN_MS = 15 * 60 * 1000L

// Terrain connu : sur les 10 dernières minutes, assez de cellules traversées
// (on marche vraiment) et presque toutes déjà révélées.
fun isFamiliarGround(entries: List<CellEntry>, nowMillis: Long): Boolean {
    val recent = entries.filter { nowMillis - it.timeMillis <= FAMILIAR_WINDOW_MS }
    if (recent.size < FAMILIAR_MIN_ENTRIES) return false
    return recent.count { it.wasNew }.toDouble() / recent.size <= FAMILIAR_MAX_NEW_SHARE
}

// Le lieu à signaler maintenant, ou null. Chaque lieu n'est signalé qu'une fois,
// et au plus une alerte par quart d'heure pour ne pas harceler.
fun placeToAnnounce(
    nearest: PlaceDirection?,
    familiar: Boolean,
    alreadyAnnouncedIds: Set<String>,
    lastAlertMillis: Long?,
    nowMillis: Long,
): Place? {
    if (!familiar || nearest == null) return null
    if (nearest.distanceMeters > NEARBY_ALERT_RADIUS_M) return null
    if (nearest.place.id in alreadyAnnouncedIds) return null
    if (lastAlertMillis != null && nowMillis - lastAlertMillis < NEARBY_ALERT_COOLDOWN_MS) return null
    return nearest.place
}

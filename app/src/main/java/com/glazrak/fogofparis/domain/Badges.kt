package com.glazrak.fogofparis.domain

// Badge d'arrondissement : les 4 quartiers de l'arrondissement ont au moins le Bronze.
const val POINTS_PER_ARRONDISSEMENT_BADGE = 300

data class ArrondissementBadge(
    val arrondissement: Int,
    // Nombre de quartiers de l'arrondissement ayant au moins le Bronze (sur 4).
    val bronzeQuartiers: Int,
    val quartierCount: Int,
    // Date du dernier Bronze nécessaire, si le badge est gagné.
    val earnedAt: Long?,
) {
    val earned: Boolean get() = earnedAt != null
}

fun arrondissementBadges(progress: List<QuartierProgress>): List<ArrondissementBadge> =
    progress.groupBy { it.quartier.arrondissement }.toSortedMap().map { (arrondissement, quartiers) ->
        val bronzeDates = quartiers.mapNotNull { it.medalDates[Medal.BRONZE] }
        ArrondissementBadge(
            arrondissement = arrondissement,
            bronzeQuartiers = bronzeDates.size,
            quartierCount = quartiers.size,
            earnedAt = if (bronzeDates.size == quartiers.size) bronzeDates.max() else null,
        )
    }

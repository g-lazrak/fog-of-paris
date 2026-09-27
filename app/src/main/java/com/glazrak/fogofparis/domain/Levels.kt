package com.glazrak.fogofparis.domain

// Niveaux et titres, dans l'ordre choisi par le propriétaire (2026-09-27).
// Premiers paliers volontairement espacés : un titre doit se mériter.
data class Level(val number: Int, val title: String, val minPoints: Int)

val LEVELS: List<Level> = listOf(
    "Badaud" to 0,
    "Promeneur" to 500,
    "Flâneur" to 1_500,
    "Touriste" to 3_500,
    "Arpenteur" to 7_000,
    "Explorateur" to 12_000,
    "Parisien" to 20_000,
    "Cartographe" to 30_000,
    "Baron Haussmann" to 45_000,
).mapIndexed { index, (title, minPoints) -> Level(index + 1, title, minPoints) }

data class LevelProgress(val level: Level, val next: Level?, val points: Int) {
    // Avancement vers le niveau suivant, de 0 à 1 (1 au niveau maximum).
    val fractionToNext: Float
        get() = next?.let { (points - level.minPoints).toFloat() / (it.minPoints - level.minPoints) } ?: 1f
}

fun levelFor(points: Int): LevelProgress {
    val index = LEVELS.indexOfLast { points >= it.minPoints }.coerceAtLeast(0)
    return LevelProgress(level = LEVELS[index], next = LEVELS.getOrNull(index + 1), points = points)
}

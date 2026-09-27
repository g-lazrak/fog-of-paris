package com.glazrak.fogofparis.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// Nombre de cellules révélées pendant une semaine (du lundi au dimanche).
data class WeekCount(val weekStart: LocalDate, val revealedCells: Int)

// Les `weeks` dernières semaines, de la plus ancienne à la semaine en cours,
// y compris les semaines sans aucune cellule (barre vide).
fun weeklyHistory(
    visitTimes: List<Long>,
    today: LocalDate,
    zone: ZoneId,
    weeks: Int = 8,
): List<WeekCount> {
    val currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val firstWeek = currentWeek.minusWeeks((weeks - 1).toLong())
    val counts = visitTimes
        .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .filter { !it.isBefore(firstWeek) }
        .groupingBy { it }
        .eachCount()
    return (0 until weeks).map { i ->
        val week = firstWeek.plusWeeks(i.toLong())
        WeekCount(week, counts[week] ?: 0)
    }
}

// Journal de marche : nombre de cellules révélées pour la PREMIÈRE fois chaque jour.
// (Les rues déjà connues parcourues ce jour-là ne sont pas enregistrées : choix du
// propriétaire, pour ne rien stocker de plus.)
data class DayCount(val date: LocalDate, val revealedCells: Int)

private fun dayOf(timeMillis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()

// Les `days` derniers jours, du plus ancien à aujourd'hui, jours vides compris.
fun dailyHistory(visitTimes: List<Long>, today: LocalDate, zone: ZoneId, days: Int = 14): List<DayCount> {
    val first = today.minusDays((days - 1).toLong())
    val counts = visitTimes.map { dayOf(it, zone) }.filter { !it.isBefore(first) }.groupingBy { it }.eachCount()
    return (0 until days).map { i ->
        val date = first.plusDays(i.toLong())
        DayCount(date, counts[date] ?: 0)
    }
}

// Les cellules révélées pour la première fois ce jour-là.
fun cellsRevealedOn(visits: List<VisitedCell>, date: LocalDate, zone: ZoneId): Set<CellId> =
    visits.filter { dayOf(it.firstVisitedAt, zone) == date }.mapTo(HashSet()) { it.cell }

// Le jour précédent / suivant qui a au moins une nouvelle cellule, pour
// naviguer dans le journal sans tomber sur des jours vides.
fun adjacentActiveDay(visits: List<VisitedCell>, from: LocalDate, zone: ZoneId, forward: Boolean): LocalDate? {
    val days = visits.mapTo(HashSet()) { dayOf(it.firstVisitedAt, zone) }
    return if (forward) days.filter { it.isAfter(from) }.minOrNull() else days.filter { it.isBefore(from) }.maxOrNull()
}

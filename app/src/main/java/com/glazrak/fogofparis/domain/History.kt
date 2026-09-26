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

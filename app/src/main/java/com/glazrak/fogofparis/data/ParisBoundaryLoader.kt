package com.glazrak.fogofparis.data

import android.content.Context
import com.glazrak.fogofparis.domain.CityBoundary
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.Ring
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Polygon

// Source : Paris OpenData, jeu "arrondissements" (20 polygones jointifs).
// La commune (bois de Boulogne et de Vincennes compris) = leur réunion.
const val ARRONDISSEMENTS_ASSET = "arrondissements.geojson"

fun loadParisBoundary(context: Context): CityBoundary {
    val json = context.assets.open(ARRONDISSEMENTS_ASSET).bufferedReader().use { it.readText() }
    return parisBoundaryFromGeoJson(json)
}

// Séparé du Context pour pouvoir être testé sur le vrai fichier sans Android.
fun parisBoundaryFromGeoJson(json: String): CityBoundary {
    val areas = FeatureCollection.fromJson(json).features().orEmpty().flatMap { feature ->
        when (val geometry = feature.geometry()) {
            is Polygon -> listOf(outerRingOf(geometry))
            is MultiPolygon -> geometry.polygons().map { outerRingOf(it) }
            else -> emptyList()
        }
    }
    return CityBoundary.fromAdjacentAreas(areas)
}

private fun outerRingOf(polygon: Polygon): Ring =
    Ring(polygon.coordinates().first().map { GeoPosition(lat = it.latitude(), lon = it.longitude()) })

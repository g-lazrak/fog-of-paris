package com.glazrak.fogofparis.data

import android.content.Context
import com.glazrak.fogofparis.domain.CityBoundary
import com.glazrak.fogofparis.domain.CityCells
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.Quartier
import com.glazrak.fogofparis.domain.QuartierIndex
import com.glazrak.fogofparis.domain.Ring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Polygon

// Sources : Paris OpenData, jeux "arrondissements" (20 polygones jointifs, dont
// la réunion = la commune, bois compris) et "quartier_paris" (80 quartiers).
const val ARRONDISSEMENTS_ASSET = "arrondissements.geojson"
const val QUARTIERS_ASSET = "quartiers.geojson"

// Toute la géographie du jeu, calculée une fois.
class ParisGeo(
    val boundary: CityBoundary,
    val cells: CityCells,
    val quartiers: QuartierIndex,
)

// Lu une seule fois par processus (~ une seconde au plus), puis partagé entre
// l'écran et le service de suivi.
object ParisGeoCache {
    private val mutex = Mutex()
    private var geo: ParisGeo? = null

    suspend fun get(context: Context): ParisGeo = mutex.withLock {
        geo ?: withContext(Dispatchers.Default) { load(context) }.also { geo = it }
    }

    private fun load(context: Context): ParisGeo {
        val boundary = parisBoundaryFromGeoJson(readAsset(context, ARRONDISSEMENTS_ASSET))
        val quartiers = quartiersFromGeoJson(readAsset(context, QUARTIERS_ASSET))
        return ParisGeo(boundary, CityCells.of(boundary), QuartierIndex(quartiers))
    }

    private fun readAsset(context: Context, name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}

// Les fonctions ci-dessous sont séparées du Context pour pouvoir être testées
// sur les vrais fichiers sans Android.

fun parisBoundaryFromGeoJson(json: String): CityBoundary {
    val areas = polygonsOf(json).map { outerRingOf(it) }
    return CityBoundary.fromAdjacentAreas(areas)
}

fun quartiersFromGeoJson(json: String): List<Quartier> =
    FeatureCollection.fromJson(json).features().orEmpty().map { feature ->
        val polygons = when (val geometry = feature.geometry()) {
            is Polygon -> listOf(geometry)
            is MultiPolygon -> geometry.polygons()
            else -> emptyList()
        }
        // Tous les anneaux (extérieurs et trous éventuels) : la règle pair-impair
        // de CityBoundary gère les trous.
        val boundary = CityBoundary(polygons.flatMap { polygon -> polygon.coordinates().map { ringOf(it) } })
        Quartier(
            id = feature.getStringProperty("c_qu").toInt(),
            name = feature.getStringProperty("l_qu"),
            arrondissement = feature.getNumberProperty("c_ar").toInt(),
            boundary = boundary,
            cells = CityCells.of(boundary),
        )
    }.sortedBy { it.id }

private fun polygonsOf(json: String): List<Polygon> =
    FeatureCollection.fromJson(json).features().orEmpty().flatMap { feature ->
        when (val geometry = feature.geometry()) {
            is Polygon -> listOf(geometry)
            is MultiPolygon -> geometry.polygons()
            else -> emptyList()
        }
    }

private fun outerRingOf(polygon: Polygon): Ring = ringOf(polygon.coordinates().first())

private fun ringOf(points: List<org.maplibre.geojson.Point>): Ring =
    Ring(points.map { GeoPosition(lat = it.latitude(), lon = it.longitude()) })

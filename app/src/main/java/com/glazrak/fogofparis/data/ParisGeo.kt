package com.glazrak.fogofparis.data

import android.content.Context
import com.glazrak.fogofparis.domain.CityBoundary
import com.glazrak.fogofparis.domain.CityCells
import com.glazrak.fogofparis.domain.CollectionSet
import com.glazrak.fogofparis.domain.DEFAULT_PLACE_RADIUS_M
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.Place
import com.glazrak.fogofparis.domain.PlaceIndex
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

// Lieux des collections, extraits une fois d'OpenStreetMap (voir tools/places/).
const val PLACES_ASSET = "places.geojson"

// Trésors cachés, un par quartier (voir tools/places/treasures.tsv).
const val TREASURES_ASSET = "treasures.geojson"

// Toute la géographie du jeu, calculée une fois.
class ParisGeo(
    val boundary: CityBoundary,
    val cells: CityCells,
    val quartiers: QuartierIndex,
    val places: PlaceIndex,
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
        val places = placesFromGeoJson(readAsset(context, PLACES_ASSET)) +
            placesFromGeoJson(readAsset(context, TREASURES_ASSET))
        return ParisGeo(boundary, CityCells.of(boundary), QuartierIndex(quartiers), PlaceIndex(places))
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

// Chaque lieu est un point avec ses propriétés "id", "name" et "set"
// (nom d'une CollectionSet, ex. "BRIDGES"), et en option "radius" et "hint".
fun placesFromGeoJson(json: String): List<Place> =
    FeatureCollection.fromJson(json).features().orEmpty().mapNotNull { feature ->
        val point = feature.geometry() as? org.maplibre.geojson.Point ?: return@mapNotNull null
        Place(
            id = feature.getStringProperty("id"),
            name = feature.getStringProperty("name"),
            set = CollectionSet.valueOf(feature.getStringProperty("set")),
            position = GeoPosition(lat = point.latitude(), lon = point.longitude()),
            radiusMeters = if (feature.hasProperty("radius")) {
                feature.getNumberProperty("radius").toDouble()
            } else {
                DEFAULT_PLACE_RADIUS_M
            },
            hint = if (feature.hasProperty("hint")) feature.getStringProperty("hint") else null,
        )
    }

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

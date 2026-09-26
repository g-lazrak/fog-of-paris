package com.glazrak.fogofparis.ui

import android.graphics.Color
import com.glazrak.fogofparis.domain.CellBounds
import com.glazrak.fogofparis.domain.CellId
import com.glazrak.fogofparis.domain.CityBoundary
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.mergeIntoRuns
import com.glazrak.fogofparis.domain.runToBounds
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val FOG_SOURCE_ID = "fog-source"
private const val FOG_LAYER_ID = "fog-layer"
private const val OUTSIDE_SOURCE_ID = "outside-source"
private const val OUTSIDE_LAYER_ID = "outside-layer"
private const val POSITION_SOURCE_ID = "position-source"
private const val POSITION_LAYER_ID = "position-layer"

// Web Mercator ne va pas jusqu'aux pôles : ±85° couvre toute la carte affichable.
private const val WORLD_LAT_LIMIT = 85.0

private val WORLD_RING = listOf(
    Point.fromLngLat(-180.0, -WORLD_LAT_LIMIT),
    Point.fromLngLat(180.0, -WORLD_LAT_LIMIT),
    Point.fromLngLat(180.0, WORLD_LAT_LIMIT),
    Point.fromLngLat(-180.0, WORLD_LAT_LIMIT),
    Point.fromLngLat(-180.0, -WORLD_LAT_LIMIT),
)

// Ajoutés après le style de base, donc dessinés par-dessus, dans cet ordre :
// brouillard, noir hors de Paris (masque aussi les bords des cellules qui
// débordent de la limite), puis la position.
fun addGameLayers(style: Style) {
    style.addSource(GeoJsonSource(FOG_SOURCE_ID))
    style.addLayer(
        FillLayer(FOG_LAYER_ID, FOG_SOURCE_ID).withProperties(
            fillColor(Color.BLACK),
            fillOpacity(0.7f),
        )
    )
    style.addSource(GeoJsonSource(OUTSIDE_SOURCE_ID))
    style.addLayer(
        FillLayer(OUTSIDE_LAYER_ID, OUTSIDE_SOURCE_ID).withProperties(
            fillColor(Color.BLACK),
            fillOpacity(1f),
        )
    )
    style.addSource(GeoJsonSource(POSITION_SOURCE_ID))
    style.addLayer(
        CircleLayer(POSITION_LAYER_ID, POSITION_SOURCE_ID).withProperties(
            circleRadius(8f),
            circleColor(Color.rgb(33, 150, 243)),
            circleStrokeWidth(2f),
            circleStrokeColor(Color.WHITE),
        )
    )
}

// Le brouillard est un seul polygone couvrant le monde, avec un trou par
// bande de cellules visitées. La carte le dessine elle-même à chaque image,
// donc rien à recalculer quand on déplace ou zoome.
fun updateFog(style: Style, visitedCells: Set<CellId>) {
    val holes = mergeIntoRuns(visitedCells).map { run -> ringOf(runToBounds(run)) }
    style.getSourceAs<GeoJsonSource>(FOG_SOURCE_ID)
        ?.setGeoJson(Polygon.fromLngLats(listOf(WORLD_RING) + holes))
}

// Tout le monde en noir, sauf un trou en forme de Paris.
fun updateOutside(style: Style, boundary: CityBoundary) {
    val parisHoles = boundary.rings.map { ring ->
        ring.points.map { Point.fromLngLat(it.lon, it.lat) }
    }
    style.getSourceAs<GeoJsonSource>(OUTSIDE_SOURCE_ID)
        ?.setGeoJson(Polygon.fromLngLats(listOf(WORLD_RING) + parisHoles))
}

fun updatePosition(style: Style, position: GeoPosition?) {
    val source = style.getSourceAs<GeoJsonSource>(POSITION_SOURCE_ID) ?: return
    if (position == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    } else {
        source.setGeoJson(Point.fromLngLat(position.lon, position.lat))
    }
}

private fun ringOf(bounds: CellBounds): List<Point> = listOf(
    Point.fromLngLat(bounds.lonWest, bounds.latSouth),
    Point.fromLngLat(bounds.lonWest, bounds.latNorth),
    Point.fromLngLat(bounds.lonEast, bounds.latNorth),
    Point.fromLngLat(bounds.lonEast, bounds.latSouth),
    Point.fromLngLat(bounds.lonWest, bounds.latSouth),
)

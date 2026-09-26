package com.glazrak.fogofparis.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.glazrak.fogofparis.domain.CellId
import com.glazrak.fogofparis.domain.CellRect
import com.glazrak.fogofparis.domain.CityBoundary
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.cellToBounds
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.rasterFadeDuration
import org.maplibre.android.style.layers.PropertyFactory.rasterResampling
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
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

// Image du brouillard prête à afficher, avec la zone de la grille qu'elle couvre.
class FogImage(val bitmap: Bitmap, val extent: CellRect)

// Ajoutés après le style de base, donc dessinés par-dessus, dans cet ordre :
// brouillard (ajouté plus tard, voir updateFog), noir hors de Paris (masque
// aussi les bords des cellules qui débordent de la limite), puis la position.
fun addGameLayers(style: Style) {
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

// Le style OpenFreeMap affiche les noms en anglais ("18th Arrondissement").
// On prend le nom français, sinon le nom local.
fun useFrenchLabels(style: Style) {
    style.layers.filterIsInstance<SymbolLayer>()
        .filter { it.textField.expression?.toString()?.contains("name_en") == true }
        .forEach { it.setProperties(textField(coalesce(get("name:fr"), get("name")))) }
}

// Le brouillard est une petite image posée sur Paris (quelques pixels par
// cellule). La carte l'agrandit en la lissant, ce qui adoucit les bords des
// zones révélées. Elle est créée au premier appel, quand l'emprise est connue.
fun updateFog(style: Style, fog: FogImage) {
    val source = style.getSourceAs<ImageSource>(FOG_SOURCE_ID)
    if (source != null) {
        source.setImage(fog.bitmap)
        return
    }
    style.addSource(ImageSource(FOG_SOURCE_ID, quadOf(fog.extent), fog.bitmap))
    style.addLayerBelow(
        RasterLayer(FOG_LAYER_ID, FOG_SOURCE_ID).withProperties(
            rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
            // Pas de fondu à chaque mise à jour de l'image.
            rasterFadeDuration(0f),
        ),
        OUTSIDE_LAYER_ID,
    )
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

// Coins géographiques de l'emprise, dans l'ordre attendu par MapLibre :
// haut-gauche, haut-droite, bas-droite, bas-gauche.
private fun quadOf(extent: CellRect): LatLngQuad {
    val northWest = cellToBounds(CellId(extent.xMin, extent.yMax))
    val southEast = cellToBounds(CellId(extent.xMax, extent.yMin))
    return LatLngQuad(
        LatLng(northWest.latNorth, northWest.lonWest),
        LatLng(northWest.latNorth, southEast.lonEast),
        LatLng(southEast.latSouth, southEast.lonEast),
        LatLng(southEast.latSouth, northWest.lonWest),
    )
}

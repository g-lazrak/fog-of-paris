package com.glazrak.fogofparis.ui

import android.graphics.Color
import com.glazrak.fogofparis.domain.CellId
import com.glazrak.fogofparis.domain.FOG_BAND_LEVELS
import com.glazrak.fogofparis.domain.FogBand
import com.glazrak.fogofparis.domain.CityBoundary
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.Quartier
import com.glazrak.fogofparis.domain.cellToBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOpacity
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.pow

// Deux fonds de carte au choix dans les Réglages (propriétaire, 2026-10-03), tous
// deux générés par tools/mapstyles/make_styles.pl et rangés dans l'app.
// Le brouillard est bleu nuit (fond des menus) ; fogOpacity = brouillard plein.
enum class MapLook(val styleUri: String, val fogRgb: Int, val fogOpacity: Double) {
    // Couleurs de carte ancienne, sans arrêts de transport ni icônes (par défaut).
    EXPLORER("asset://map_style_explorer.json", fogRgb = 0x0B1020, fogOpacity = 0.74),
    // Photos aériennes de l'IGN avec les noms des rues.
    AERIAL("asset://map_style_aerial.json", fogRgb = 0x0B1020, fogOpacity = 0.80),
}

private const val FOG_SOURCE_PREFIX = "fog-band-source-"
private const val FOG_LAYER_PREFIX = "fog-band-layer-"
private const val QUARTIERS_SOURCE_ID = "quartiers-source"
private const val QUARTIERS_LAYER_ID = "quartiers-layer"
private const val HUNT_SOURCE_ID = "hunt-source"
private const val HUNT_LAYER_ID = "hunt-layer"
private const val DAY_SOURCE_ID = "day-source"
private const val DAY_LAYER_ID = "day-layer"
private const val OUTSIDE_SOURCE_ID = "outside-source"
private const val OUTSIDE_LAYER_ID = "outside-layer"
private const val PLACES_SOURCE_ID = "places-source"
private const val PLACES_DOT_LAYER_ID = "places-dots"
private const val PLACES_LABEL_LAYER_ID = "places-labels"
private const val NAME_PROPERTY = "name"
private const val VISITED_PROPERTY = "visited"
private val PLACE_VISITED_COLOR = Color.rgb(255, 193, 7)
private val PLACE_UNVISITED_COLOR = Color.rgb(158, 158, 158)
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

// Formes du brouillard prêtes à afficher : une collection par bande (FogContours.kt).
class FogShapes(val bands: List<FeatureCollection>)

// Les bandes s'empilent : avec n bandes d'opacité a, le brouillard plein vaut
// 1 - (1 - a)^n, qu'on fait correspondre à l'opacité voulue du style.
private fun bandOpacity(look: MapLook): Float =
    (1 - (1 - look.fogOpacity).pow(1.0 / FOG_BAND_LEVELS.size)).toFloat()

// Calcul (lourd) à faire hors du fil principal.
fun fogShapesOf(bands: List<FogBand>): FogShapes = FogShapes(
    bands.map { band ->
        FeatureCollection.fromFeatures(
            band.polygons.map { polygon ->
                Feature.fromGeometry(
                    Polygon.fromLngLats(
                        (listOf(polygon.outer) + polygon.holes).map { ring ->
                            ring.map { point -> point.toGeo().let { Point.fromLngLat(it.lon, it.lat) } }
                        }
                    )
                )
            }
        )
    }
)

// Ajoutés après le style de base, donc dessinés par-dessus, dans cet ordre :
// bandes du brouillard, contours des quartiers, noir hors de Paris (masque aussi les bords des cellules qui débordent de la
// limite), puis la position.
fun addGameLayers(style: Style, look: MapLook) {
    FOG_BAND_LEVELS.indices.forEach { band ->
        style.addSource(GeoJsonSource(FOG_SOURCE_PREFIX + band))
        style.addLayer(
            FillLayer(FOG_LAYER_PREFIX + band, FOG_SOURCE_PREFIX + band).withProperties(
                fillColor(Color.rgb(Color.red(look.fogRgb), Color.green(look.fogRgb), Color.blue(look.fogRgb))),
                fillOpacity(bandOpacity(look)),
            )
        )
    }
    style.addSource(GeoJsonSource(QUARTIERS_SOURCE_ID))
    style.addLayer(
        LineLayer(QUARTIERS_LAYER_ID, QUARTIERS_SOURCE_ID).withProperties(
            lineColor(Color.WHITE),
            lineOpacity(0.25f),
            lineWidth(1f),
        )
    )
    // Journal : les carrés découverts le jour choisi, en doré par-dessus le brouillard.
    style.addSource(GeoJsonSource(DAY_SOURCE_ID))
    style.addLayer(
        FillLayer(DAY_LAYER_ID, DAY_SOURCE_ID).withProperties(
            fillColor(Color.rgb(233, 185, 73)),
            fillOpacity(0.65f),
        )
    )
    style.addSource(GeoJsonSource(OUTSIDE_SOURCE_ID))
    style.addLayer(
        FillLayer(OUTSIDE_LAYER_ID, OUTSIDE_SOURCE_ID).withProperties(
            fillColor(Color.BLACK),
            fillOpacity(1f),
        )
    )
    // Lieux des collections : visibles à travers le brouillard pour donner des
    // destinations. Gris tant que non visités, dorés ensuite.
    style.addSource(GeoJsonSource(PLACES_SOURCE_ID))
    style.addLayer(
        CircleLayer(PLACES_DOT_LAYER_ID, PLACES_SOURCE_ID).withProperties(
            circleRadius(interpolate(linear(), zoom(), stop(11, 2.5f), stop(16, 7f))),
            circleColor(switchCase(get(VISITED_PROPERTY), color(PLACE_VISITED_COLOR), color(PLACE_UNVISITED_COLOR))),
            circleStrokeWidth(1.5f),
            circleStrokeColor(Color.WHITE),
        )
    )
    style.addLayer(
        SymbolLayer(PLACES_LABEL_LAYER_ID, PLACES_SOURCE_ID).withProperties(
            textField(get(NAME_PROPERTY)),
            textFont(arrayOf("Noto Sans Regular")),
            textSize(12f),
            textOffset(arrayOf(0f, 1.2f)),
            textAnchor(Property.TEXT_ANCHOR_TOP),
            textColor(switchCase(get(VISITED_PROPERTY), color(PLACE_VISITED_COLOR), color(Color.WHITE))),
            textHaloColor(Color.BLACK),
            textHaloWidth(1.2f),
            // Noms seulement de près, pour ne pas surcharger la carte.
            textOpacity(interpolate(linear(), zoom(), stop(14, 0f), stop(14.5, 1f))),
        )
    )
    // Épingle du trésor, seulement après « Donner sa langue au chat ».
    style.addSource(GeoJsonSource(HUNT_SOURCE_ID))
    style.addLayer(
        CircleLayer(HUNT_LAYER_ID, HUNT_SOURCE_ID).withProperties(
            circleRadius(10f),
            circleColor(Color.rgb(127, 214, 232)),
            circleStrokeWidth(3f),
            circleStrokeColor(Color.WHITE),
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

fun updateFog(style: Style, fog: FogShapes) {
    fog.bands.forEachIndexed { band, shapes ->
        style.getSourceAs<GeoJsonSource>(FOG_SOURCE_PREFIX + band)?.setGeoJson(shapes)
    }
}

fun updateHuntPin(style: Style, position: GeoPosition?) {
    val source = style.getSourceAs<GeoJsonSource>(HUNT_SOURCE_ID) ?: return
    if (position == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    } else {
        source.setGeoJson(Point.fromLngLat(position.lon, position.lat))
    }
}

fun updateDayCells(style: Style, cells: Set<CellId>) {
    val squares = cells.map { cell ->
        val b = cellToBounds(cell)
        Feature.fromGeometry(
            Polygon.fromLngLats(
                listOf(
                    listOf(
                        Point.fromLngLat(b.lonWest, b.latSouth), Point.fromLngLat(b.lonEast, b.latSouth),
                        Point.fromLngLat(b.lonEast, b.latNorth), Point.fromLngLat(b.lonWest, b.latNorth),
                        Point.fromLngLat(b.lonWest, b.latSouth),
                    )
                )
            )
        )
    }
    style.getSourceAs<GeoJsonSource>(DAY_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(squares))
}

fun updatePlaces(style: Style, markers: List<PlaceMarker>) {
    val features = markers.map { marker ->
        Feature.fromGeometry(Point.fromLngLat(marker.place.position.lon, marker.place.position.lat)).apply {
            addStringProperty(NAME_PROPERTY, marker.place.name)
            addBooleanProperty(VISITED_PROPERTY, marker.visited)
        }
    }
    style.getSourceAs<GeoJsonSource>(PLACES_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(features))
}

// Contours fins des 80 quartiers, pour se repérer dans le jeu.
fun updateQuartierOutlines(style: Style, quartiers: List<Quartier>) {
    val polygons = quartiers.map { quartier ->
        Polygon.fromLngLats(
            quartier.boundary.rings.map { ring -> ring.points.map { Point.fromLngLat(it.lon, it.lat) } }
        )
    }
    style.getSourceAs<GeoJsonSource>(QUARTIERS_SOURCE_ID)
        ?.setGeoJson(FeatureCollection.fromFeatures(polygons.map { Feature.fromGeometry(it) }))
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

package com.glazrak.fogofparis.data

import com.glazrak.fogofparis.domain.CityBoundary
import com.glazrak.fogofparis.domain.CityCells
import com.glazrak.fogofparis.domain.CollectionSet
import com.glazrak.fogofparis.domain.Quartier
import com.glazrak.fogofparis.domain.QuartierIndex
import com.glazrak.fogofparis.domain.latLonToCell
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.areaInSquareMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

// Vérifie le vrai fichier livré avec l'app (les tests tournent depuis le dossier app/).
class ParisBoundaryTest {

    companion object {
        private lateinit var paris: CityBoundary
        private lateinit var quartiers: List<Quartier>

        @BeforeClass
        @JvmStatic
        fun loadBoundary() {
            paris = parisBoundaryFromGeoJson(File("src/main/assets/$ARRONDISSEMENTS_ASSET").readText())
            quartiers = quartiersFromGeoJson(File("src/main/assets/$QUARTIERS_ASSET").readText())
        }
    }

    @Test
    fun arrondissements_merge_into_a_single_outline() {
        assertEquals(1, paris.rings.size)
    }

    @Test
    fun outline_area_matches_official_paris_area() {
        // Superficie officielle de la commune : ~105,4 km².
        assertEquals(105.4, areaInSquareMeters(paris.rings.single()) / 1_000_000, 1.0)
    }

    @Test
    fun all_145_places_load_are_unique_and_inside_paris() {
        val places = placesFromGeoJson(File("src/main/assets/$PLACES_ASSET").readText())
        assertEquals(145, places.size)
        assertEquals(places.size, places.map { it.id }.toSet().size)
        // Les trésors sont dans leur propre fichier.
        assertEquals(CollectionSet.entries.filterNot { it.hidden }.toSet(), places.map { it.set }.toSet())
        val outside = places.filterNot { paris.contains(it.position) }.map { it.name }
        assertTrue("Places outside Paris: $outside", outside.isEmpty())
    }

    @Test
    fun exactly_one_hidden_treasure_per_quartier_each_with_a_hint() {
        val treasures = placesFromGeoJson(File("src/main/assets/$TREASURES_ASSET").readText())
        assertEquals(80, treasures.size)
        assertTrue(treasures.all { it.set == CollectionSet.TREASURES && !it.hint.isNullOrBlank() })
        val quartierOf = treasures.associate { t -> t.id to quartiers.firstOrNull { it.boundary.contains(t.position) }?.id }
        val outside = quartierOf.filterValues { it == null }.keys
        assertTrue("Treasures outside every quartier: $outside", outside.isEmpty())
        val perQuartier = quartierOf.values.groupingBy { it }.eachCount()
        val doubles = perQuartier.filterValues { it > 1 }
        assertTrue("Quartiers with several treasures: $doubles", doubles.isEmpty())
        assertEquals((1..80).toSet(), perQuartier.keys)
    }

    @Test
    fun every_treasure_has_a_short_story() {
        val treasures = placesFromGeoJson(File("src/main/assets/$TREASURES_ASSET").readText())
        val missing = treasures.filter { it.story.isNullOrBlank() }.map { it.id }
        assertTrue("Treasures without a story: $missing", missing.isEmpty())
        // « Pas trop long » (propriétaire) : deux ou trois phrases.
        val tooLong = treasures.filter { (it.story?.length ?: 0) > 260 }.map { it.id }
        assertTrue("Stories too long: $tooLong", tooLong.isEmpty())
    }

    @Test
    fun there_are_80_quartiers_4_per_arrondissement() {
        assertEquals(80, quartiers.size)
        assertEquals((1..20).toSet(), quartiers.map { it.arrondissement }.toSet())
        assertTrue(quartiers.groupBy { it.arrondissement }.values.all { it.size == 4 })
    }

    @Test
    fun quartiers_cover_paris_cells_almost_exactly() {
        val parisCells = CityCells.of(paris)
        val index = QuartierIndex(quartiers)
        val sumOfQuartiers = quartiers.sumOf { it.cells.totalCells }
        // Deux sources différentes (arrondissements / quartiers) : on tolère 0,5 %.
        assertEquals(parisCells.totalCells.toDouble(), sumOfQuartiers.toDouble(), parisCells.totalCells * 0.005)
        val orphans = parisCells.allCells().count { index.quartierOf(it) == null }
        assertTrue("$orphans Paris cells without quartier", orphans < parisCells.totalCells * 0.005)
    }

    @Test
    fun landmarks_fall_in_their_quartier() {
        val index = QuartierIndex(quartiers)
        assertEquals("Gros-Caillou", index.quartierOf(latLonToCell(48.8584, 2.2945))?.name) // Tour Eiffel
        assertEquals("Notre-Dame", index.quartierOf(latLonToCell(48.8530, 2.3499))?.name)
        assertEquals("Bel-Air", index.quartierOf(latLonToCell(48.8330, 2.4180))?.name) // Lac Daumesnil
    }

    @Test
    fun paris_cells_add_up_to_the_city_area() {
        val cells = CityCells.of(paris)
        // 105,4 km² / 2 500 m² par cellule ≈ 42 160 cellules.
        assertEquals(42_160.0, cells.totalCells.toDouble(), 42_160.0 * 0.01)
    }

    @Test
    fun paris_cells_match_point_in_polygon_on_landmarks() {
        val cells = CityCells.of(paris)
        val eiffel = latLonToCell(48.8584, 2.2945)
        val boulogneBillancourt = latLonToCell(48.8350, 2.2400)
        assertTrue(cells.contains(eiffel))
        assertFalse(cells.contains(boulogneBillancourt))
    }

    @Test
    fun central_landmarks_are_inside() {
        assertTrue(paris.contains(GeoPosition(lat = 48.8584, lon = 2.2945))) // Tour Eiffel
        assertTrue(paris.contains(GeoPosition(lat = 48.8530, lon = 2.3499))) // Notre-Dame
    }

    @Test
    fun both_woods_are_inside() {
        assertTrue(paris.contains(GeoPosition(lat = 48.8580, lon = 2.2330))) // Longchamp, bois de Boulogne
        assertTrue(paris.contains(GeoPosition(lat = 48.8330, lon = 2.4180))) // Lac Daumesnil, bois de Vincennes
    }

    @Test
    fun neighbouring_towns_are_outside() {
        assertFalse(paris.contains(GeoPosition(lat = 48.8350, lon = 2.2400))) // Boulogne-Billancourt
        assertFalse(paris.contains(GeoPosition(lat = 48.8846, lon = 2.2697))) // Neuilly-sur-Seine
        assertFalse(paris.contains(GeoPosition(lat = 48.8638, lon = 2.4485))) // Montreuil
        assertFalse(paris.contains(GeoPosition(lat = 48.8477, lon = 2.4392))) // Vincennes (ville)
    }
}

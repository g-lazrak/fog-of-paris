package com.glazrak.fogofparis.data

import com.glazrak.fogofparis.domain.CityBoundary
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

        @BeforeClass
        @JvmStatic
        fun loadBoundary() {
            paris = parisBoundaryFromGeoJson(File("src/main/assets/$ARRONDISSEMENTS_ASSET").readText())
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

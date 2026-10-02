package com.glazrak.fogofparis.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Réglages de l'utilisateur, dans un petit fichier séparé de la base des cellules.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val NEARBY_ALERTS_KEY = booleanPreferencesKey("nearby_alerts")
private val LAST_CELEBRATED_LEVEL_KEY = intPreferencesKey("last_celebrated_level")
private val ANNOUNCED_PLACES_KEY = stringSetPreferencesKey("announced_places")
private val MAP_STYLE_KEY = stringPreferencesKey("map_style")
private val REVEALED_PINS_KEY = stringSetPreferencesKey("revealed_treasure_pins")
private val TRACKING_ENABLED_KEY = booleanPreferencesKey("tracking_enabled")
private val MENU_THEME_KEY = stringPreferencesKey("menu_theme")

class SettingsStore(private val context: Context) {

    // Désactivé par défaut : c'est au joueur de choisir d'être prévenu.
    val nearbyAlerts: Flow<Boolean> = context.settingsDataStore.data.map { it[NEARBY_ALERTS_KEY] ?: false }

    suspend fun setNearbyAlerts(enabled: Boolean) {
        context.settingsDataStore.edit { it[NEARBY_ALERTS_KEY] = enabled }
    }

    // Lieux déjà signalés par l'alerte « lieu proche » : jamais deux fois le même,
    // même des jours plus tard (sinon on serait prévenu à chaque trajet quotidien).
    val announcedPlaces: Flow<Set<String>> = context.settingsDataStore.data.map { it[ANNOUNCED_PLACES_KEY].orEmpty() }

    suspend fun addAnnouncedPlace(id: String) {
        context.settingsDataStore.edit { it[ANNOUNCED_PLACES_KEY] = it[ANNOUNCED_PLACES_KEY].orEmpty() + id }
    }

    // Trésors dont on a « donné sa langue au chat » : leur épingle reste affichée.
    val revealedTreasurePins: Flow<Set<String>> = context.settingsDataStore.data.map { it[REVEALED_PINS_KEY].orEmpty() }

    suspend fun addRevealedTreasurePin(id: String) {
        context.settingsDataStore.edit { it[REVEALED_PINS_KEY] = it[REVEALED_PINS_KEY].orEmpty() + id }
    }

    // Fond de carte choisi (nom d'un MapLook) ; null tant qu'on n'a rien choisi.
    val mapStyle: Flow<String?> = context.settingsDataStore.data.map { it[MAP_STYLE_KEY] }

    suspend fun setMapStyle(name: String) {
        context.settingsDataStore.edit { it[MAP_STYLE_KEY] = name }
    }

    // Dernier niveau déjà fêté à l'écran ; null avant la toute première ouverture.
    val lastCelebratedLevel: Flow<Int?> = context.settingsDataStore.data.map { it[LAST_CELEBRATED_LEVEL_KEY] }

    suspend fun setLastCelebratedLevel(level: Int) {
        context.settingsDataStore.edit { it[LAST_CELEBRATED_LEVEL_KEY] = level }
    }

    // Suivi voulu par le joueur : null tant qu'il n'a jamais été allumé ni éteint
    // (alors on le lance dès que les permissions sont là : « on le laisse allumé »).
    val trackingEnabled: Flow<Boolean?> = context.settingsDataStore.data.map { it[TRACKING_ENABLED_KEY] }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[TRACKING_ENABLED_KEY] = enabled }
    }

    // Thème des menus, indépendant de la carte. Sombre par défaut (l'identité du jeu).
    val menuTheme: Flow<MenuTheme> = context.settingsDataStore.data.map { prefs ->
        MenuTheme.entries.firstOrNull { it.name == prefs[MENU_THEME_KEY] } ?: MenuTheme.DARK
    }

    suspend fun setMenuTheme(theme: MenuTheme) {
        context.settingsDataStore.edit { it[MENU_THEME_KEY] = theme.name }
    }
}

enum class MenuTheme { DARK, LIGHT, SYSTEM }

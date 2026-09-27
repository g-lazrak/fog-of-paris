package com.glazrak.fogofparis.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Réglages de l'utilisateur, dans un petit fichier séparé de la base des cellules.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val NEARBY_ALERTS_KEY = booleanPreferencesKey("nearby_alerts")
private val LAST_CELEBRATED_LEVEL_KEY = intPreferencesKey("last_celebrated_level")

class SettingsStore(private val context: Context) {

    // Désactivé par défaut : c'est au joueur de choisir d'être prévenu.
    val nearbyAlerts: Flow<Boolean> = context.settingsDataStore.data.map { it[NEARBY_ALERTS_KEY] ?: false }

    suspend fun setNearbyAlerts(enabled: Boolean) {
        context.settingsDataStore.edit { it[NEARBY_ALERTS_KEY] = enabled }
    }

    // Dernier niveau déjà fêté à l'écran ; null avant la toute première ouverture.
    val lastCelebratedLevel: Flow<Int?> = context.settingsDataStore.data.map { it[LAST_CELEBRATED_LEVEL_KEY] }

    suspend fun setLastCelebratedLevel(level: Int) {
        context.settingsDataStore.edit { it[LAST_CELEBRATED_LEVEL_KEY] = level }
    }
}

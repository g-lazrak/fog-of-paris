package com.glazrak.fogofparis

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Crée (une seule fois) un DataStore nommé "fog_data", accessible via context.dataStore.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fog_data")

// Clé typée pour notre ensemble de cellules, stockées en strings "x,y".
private val VISITED_CELLS_KEY = stringSetPreferencesKey("visited_cells")

class VisitedCellsStore(private val context: Context) {

    // Flux des cellules visitées. Émet une nouvelle valeur à chaque changement.
    val visitedCells: Flow<Set<CellId>> = context.dataStore.data.map { prefs ->
        val raw = prefs[VISITED_CELLS_KEY] ?: emptySet()
        raw.map { str ->
            val (x, y) = str.split(",")
            CellId(x.toInt(), y.toInt())
        }.toSet()
    }

    // Ajoute une cellule à l'ensemble persisté.
    suspend fun addCell(cell: CellId) {
        context.dataStore.edit { prefs ->
            val current = prefs[VISITED_CELLS_KEY] ?: emptySet()
            prefs[VISITED_CELLS_KEY] = current + "${cell.x},${cell.y}"
        }
    }
}
package com.glazrak.fogofparis.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.glazrak.fogofparis.domain.CellId
import kotlinx.coroutines.flow.first

// Ancien stockage du prototype (DataStore "fog_data", cellules en texte "x,y").
// Ne sert plus qu'à importer ces cellules dans la base Room, une seule fois.
private val Context.legacyDataStore: DataStore<Preferences> by preferencesDataStore(name = "fog_data")

private val VISITED_CELLS_KEY = stringSetPreferencesKey("visited_cells")

class LegacyVisitedCellsStore(private val context: Context) {

    suspend fun readAll(): Set<CellId> {
        val raw = context.legacyDataStore.data.first()[VISITED_CELLS_KEY] ?: return emptySet()
        return raw.mapNotNullTo(HashSet()) { text ->
            parseLegacyCell(text).also {
                if (it == null) Log.e("LegacyVisitedCellsStore", "Skipping malformed cell '$text'")
            }
        }
    }

    suspend fun clear() {
        context.legacyDataStore.edit { it.remove(VISITED_CELLS_KEY) }
    }
}

// "12,-3" -> CellId(12, -3) ; null si le texte est abîmé.
fun parseLegacyCell(text: String): CellId? {
    val parts = text.split(",")
    if (parts.size != 2) return null
    val x = parts[0].trim().toIntOrNull() ?: return null
    val y = parts[1].trim().toIntOrNull() ?: return null
    return CellId(x, y)
}

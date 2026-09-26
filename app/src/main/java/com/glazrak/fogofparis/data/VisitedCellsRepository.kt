package com.glazrak.fogofparis.data

import android.content.Context
import com.glazrak.fogofparis.domain.CellId
import com.glazrak.fogofparis.domain.VisitedCell
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Seul point d'accès aux cellules révélées pour le reste de l'app.
class VisitedCellsRepository(
    private val dao: VisitedCellDao,
    private val legacyStore: LegacyVisitedCellsStore,
) {

    // Mis à jour automatiquement à chaque nouvelle cellule.
    val visits: Flow<List<VisitedCell>> = dao.observeAll().map { rows ->
        rows.map { VisitedCell(CellId(it.x, it.y), it.firstVisitedAt) }
    }

    suspend fun currentVisits(): List<VisitedCell> = visits.first()

    // true si la cellule n'avait encore jamais été révélée.
    suspend fun recordVisit(cell: CellId, timeMillis: Long = System.currentTimeMillis()): Boolean {
        val rowIds = dao.insertIfAbsent(listOf(VisitedCellEntity(cell.x, cell.y, timeMillis)))
        return rowIds.single() != -1L
    }

    // Reprend une fois les cellules de l'ancien stockage (prototype). Leur vraie
    // date de visite est inconnue : on met la date de l'import.
    suspend fun importLegacyCells(timeMillis: Long = System.currentTimeMillis()) {
        val legacyCells = legacyStore.readAll()
        if (legacyCells.isEmpty()) return
        dao.insertIfAbsent(legacyCells.map { VisitedCellEntity(it.x, it.y, timeMillis) })
        // Effacé seulement après l'écriture réussie en base : rien ne peut se perdre.
        legacyStore.clear()
    }

    companion object {
        fun create(context: Context) = VisitedCellsRepository(
            dao = FogDatabase.getInstance(context).visitedCellDao(),
            legacyStore = LegacyVisitedCellsStore(context),
        )
    }
}

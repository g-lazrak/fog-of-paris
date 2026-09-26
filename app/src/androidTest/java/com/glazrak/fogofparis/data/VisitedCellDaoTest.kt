package com.glazrak.fogofparis.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Tourne sur un appareil (Room a besoin d'Android), avec une base en mémoire.
@RunWith(AndroidJUnit4::class)
class VisitedCellDaoTest {

    private lateinit var database: FogDatabase
    private lateinit var dao: VisitedCellDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FogDatabase::class.java,
        ).build()
        dao = database.visitedCellDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun revisiting_a_cell_keeps_its_first_visit_time() = runBlocking {
        dao.insertIfAbsent(listOf(VisitedCellEntity(x = 3, y = 4, firstVisitedAt = 1_000L)))
        dao.insertIfAbsent(listOf(VisitedCellEntity(x = 3, y = 4, firstVisitedAt = 9_000L)))

        val rows = dao.observeAll().first()

        assertEquals(listOf(VisitedCellEntity(x = 3, y = 4, firstVisitedAt = 1_000L)), rows)
    }

    @Test
    fun distinct_cells_are_all_stored() = runBlocking {
        dao.insertIfAbsent(
            listOf(
                VisitedCellEntity(x = 0, y = 0, firstVisitedAt = 1L),
                VisitedCellEntity(x = -1, y = 0, firstVisitedAt = 2L),
                VisitedCellEntity(x = 0, y = -1, firstVisitedAt = 3L),
            )
        )

        assertEquals(3, dao.observeAll().first().size)
    }
}

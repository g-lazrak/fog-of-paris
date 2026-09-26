package com.glazrak.fogofparis.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// Une ligne par cellule révélée. La clé (x, y) garantit qu'une cellule
// n'est enregistrée qu'une fois.
@Entity(tableName = "visited_cells", primaryKeys = ["x", "y"])
data class VisitedCellEntity(
    val x: Int,
    val y: Int,
    // Millisecondes depuis 1970 (System.currentTimeMillis()).
    val firstVisitedAt: Long,
)

@Dao
interface VisitedCellDao {

    @Query("SELECT * FROM visited_cells")
    fun observeAll(): Flow<List<VisitedCellEntity>>

    // IGNORE : si la cellule existe déjà, on garde la date de première visite.
    // Renvoie, pour chaque cellule, son numéro de ligne, ou -1 si elle existait déjà.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(cells: List<VisitedCellEntity>): List<Long>
}

@Database(entities = [VisitedCellEntity::class], version = 1)
abstract class FogDatabase : RoomDatabase() {

    abstract fun visitedCellDao(): VisitedCellDao

    companion object {
        @Volatile
        private var instance: FogDatabase? = null

        // Une seule base pour toute l'app (écran et, plus tard, service de suivi).
        fun getInstance(context: Context): FogDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FogDatabase::class.java,
                    "fog.db",
                ).build().also { instance = it }
            }
    }
}

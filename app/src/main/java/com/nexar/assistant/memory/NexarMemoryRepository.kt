package com.nexar.assistant.memory

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Entity(tableName = "memories")
data class MemoryFact(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllFacts(): Flow<List<MemoryFact>>

    @Query("SELECT * FROM memories WHERE key LIKE :query OR value LIKE :query OR description LIKE :query")
    suspend fun searchFacts(query: String): List<MemoryFact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: MemoryFact)

    @Delete
    suspend fun deleteFact(fact: MemoryFact)

    @Query("DELETE FROM memories")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int
}

@Database(entities = [MemoryFact::class], version = 1, exportSchema = false)
abstract class NexarDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: NexarDatabase? = null

        fun getInstance(context: Context): NexarDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexarDatabase::class.java,
                    "nexar_memory.db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class NexarMemoryRepository(context: Context) {
    private val db = NexarDatabase.getInstance(context)
    private val dao = db.memoryDao()

    val allFacts: Flow<List<MemoryFact>> = dao.getAllFacts()

    suspend fun saveFact(key: String, value: String, description: String) {
        withContext(Dispatchers.IO) {
            // Sanitize key - no passwords/tokens
            val sanitizedKey = key.lowercase().trim()
            val forbidden = listOf("password", "api_key", "apikey", "token", "secret", "credential", "pin", "bank", "credit", "debit")
            if (forbidden.any { sanitizedKey.contains(it) }) {
                return@withContext // Silently reject sensitive keys
            }
            dao.insertFact(MemoryFact(sanitizedKey, value, description))
        }
    }

    suspend fun searchFacts(query: String): List<MemoryFact> {
        return withContext(Dispatchers.IO) {
            dao.searchFacts("%$query%")
        }
    }

    suspend fun deleteFact(fact: MemoryFact) {
        withContext(Dispatchers.IO) {
            dao.deleteFact(fact)
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            dao.clearAll()
        }
    }
}

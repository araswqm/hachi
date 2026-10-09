package com.araswqm.hatir.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.araswqm.hatir.data.db.entity.MemoryStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryStateDao {

    @Query("SELECT * FROM memory_state WHERE characterId = :characterId")
    suspend fun get(characterId: String): MemoryStateEntity?

    @Query("SELECT * FROM memory_state WHERE characterId = :characterId")
    fun observe(characterId: String): Flow<MemoryStateEntity?>

    /** Hata ayiklama ekrani: tum karakterlerin yakinlik durumu. */
    @Query("SELECT * FROM memory_state")
    fun observeAll(): Flow<List<MemoryStateEntity>>

    @Upsert
    suspend fun upsert(state: MemoryStateEntity)

    @Query("DELETE FROM memory_state WHERE characterId = :characterId")
    suspend fun delete(characterId: String)
}

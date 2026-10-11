package com.araswqm.hachi.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.araswqm.hachi.data.db.entity.MemoryStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryStateDao {

    @Query("SELECT * FROM memory_state WHERE chatId = :chatId")
    suspend fun get(chatId: String): MemoryStateEntity?

    @Query("SELECT * FROM memory_state WHERE chatId = :chatId")
    fun observe(chatId: String): Flow<MemoryStateEntity?>

    /** Hata ayiklama ekrani: tum sohbetlerin yakinlik durumu. */
    @Query("SELECT * FROM memory_state")
    fun observeAll(): Flow<List<MemoryStateEntity>>

    @Upsert
    suspend fun upsert(state: MemoryStateEntity)

    @Query("DELETE FROM memory_state WHERE chatId = :chatId")
    suspend fun delete(chatId: String)
}

package com.araswqm.hatir.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.araswqm.hatir.data.db.entity.ProactiveLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProactiveLogDao {

    @Insert
    suspend fun insert(log: ProactiveLogEntity): Long

    /** "Neden yazmadi?" gorunumu icin son kayitlar. */
    @Query(
        "SELECT * FROM proactive_log WHERE characterId = :characterId " +
            "ORDER BY timestamp DESC LIMIT :limit",
    )
    fun observeRecent(characterId: String, limit: Int): Flow<List<ProactiveLogEntity>>

    @Query("SELECT * FROM proactive_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentAll(limit: Int): Flow<List<ProactiveLogEntity>>

    @Query(
        "SELECT COUNT(*) FROM proactive_log WHERE characterId = :characterId " +
            "AND result = 'sent' AND timestamp >= :since",
    )
    suspend fun countSentSince(characterId: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM proactive_log WHERE result = 'sent' AND timestamp >= :since")
    suspend fun countSentSinceAll(since: Long): Int

    @Query("DELETE FROM proactive_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

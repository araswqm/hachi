package com.araswqm.hachi.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.araswqm.hachi.data.db.entity.FollowupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowupDao {

    /** Acik (tamamlanmamis) takipler: vadesi olanlar once, sonra vadesizler. */
    @Query(
        """
        SELECT * FROM followups
        WHERE characterId = :characterId AND done = 0
        ORDER BY (dueAt IS NULL) ASC, dueAt ASC, createdAt ASC
        """,
    )
    fun observeOpen(characterId: String): Flow<List<FollowupEntity>>

    @Query(
        """
        SELECT * FROM followups
        WHERE characterId = :characterId
        ORDER BY done ASC, (dueAt IS NULL) ASC, dueAt ASC, createdAt ASC
        """,
    )
    fun observeAll(characterId: String): Flow<List<FollowupEntity>>

    /** Vadesi gelmis takipler — kapi (gate) kuralini gecersiz kilabilir. */
    @Query(
        """
        SELECT * FROM followups
        WHERE characterId = :characterId AND done = 0 AND dueAt IS NOT NULL AND dueAt <= :now
        ORDER BY dueAt ASC
        """,
    )
    suspend fun getDue(characterId: String, now: Long): List<FollowupEntity>

    @Insert
    suspend fun insert(followup: FollowupEntity): Long

    @Update
    suspend fun update(followup: FollowupEntity)

    @Query("UPDATE followups SET done = 1, doneAt = :at WHERE id = :id")
    suspend fun markDone(id: Long, at: Long)

    @Query("DELETE FROM followups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM followups WHERE characterId = :characterId")
    suspend fun deleteAllForCharacter(characterId: String)
}

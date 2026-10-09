package com.araswqm.hatir.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.araswqm.hatir.data.db.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    /** Sohbet listesi: en son aktivite ustte; hic konusulmamis karakterler de gorunur. */
    @Query("SELECT * FROM characters ORDER BY COALESCE(lastMessageAt, createdAt) DESC")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :id")
    fun observeById(id: String): Flow<CharacterEntity?>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getById(id: String): CharacterEntity?

    @Query("SELECT COUNT(*) FROM characters")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(character: CharacterEntity)

    @Update
    suspend fun update(character: CharacterEntity)

    @Upsert
    suspend fun upsert(character: CharacterEntity)

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE characters SET lastMessageAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun touchLastMessage(id: String, at: Long)

    @Query("UPDATE characters SET unreadCount = :count WHERE id = :id")
    suspend fun setUnreadCount(id: String, count: Int)

    // ------------------------------------------------------------------
    // Kendiliğinden mesaj zamanlayicisi
    // ------------------------------------------------------------------

    @Query("UPDATE characters SET nextCheckAt = :nextCheckAt WHERE id = :id")
    suspend fun setNextCheckAt(id: String, nextCheckAt: Long?)

    @Query(
        """
        SELECT * FROM characters
        WHERE proactiveEnabled = 1
          AND (nextCheckAt IS NULL OR nextCheckAt <= :now)
        """,
    )
    suspend fun getDueForCheck(now: Long): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE proactiveEnabled = 1")
    suspend fun getProactiveCharacters(): List<CharacterEntity>
}

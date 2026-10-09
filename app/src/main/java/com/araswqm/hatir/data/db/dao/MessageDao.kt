package com.araswqm.hatir.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.araswqm.hatir.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** Sohbet ekrani: eskiden yeniye. */
    @Query("SELECT * FROM messages WHERE characterId = :characterId ORDER BY createdAt ASC, id ASC")
    fun observeForCharacter(characterId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    /**
     * Prompt icin son N mesaj. DESC + LIMIT ile en yeniler alinir; cagiran taraf
     * kronolojik siraya cevirir (bkz. PromptBuilder).
     */
    @Query(
        "SELECT * FROM messages WHERE characterId = :characterId " +
            "ORDER BY createdAt DESC, id DESC LIMIT :limit",
    )
    suspend fun getLast(characterId: String, limit: Int): List<MessageEntity>

    /** Ozetleme/kalicilik icin belirli bir id'den SONRAKI mesajlar. */
    @Query(
        "SELECT * FROM messages WHERE characterId = :characterId AND id > :afterId " +
            "ORDER BY id ASC LIMIT :limit",
    )
    suspend fun getAfter(characterId: String, afterId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE characterId = :characterId")
    suspend fun count(characterId: String): Int

    @Query("SELECT COUNT(*) FROM messages WHERE characterId = :characterId AND id > :afterId")
    suspend fun countAfter(characterId: String, afterId: Long): Int

    /** Karakterin son mesajini bulmak icin (zaman farki notu). */
    @Query(
        "SELECT * FROM messages WHERE characterId = :characterId " +
            "ORDER BY createdAt DESC, id DESC LIMIT 1",
    )
    suspend fun getLatest(characterId: String): MessageEntity?

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE characterId = :characterId")
    suspend fun deleteAllForCharacter(characterId: String)

    @Query("UPDATE messages SET isUnread = 0 WHERE characterId = :characterId AND isUnread = 1")
    suspend fun markAllRead(characterId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE characterId = :characterId AND isUnread = 1")
    suspend fun unreadCount(characterId: String): Int

    @Query("SELECT COUNT(*) FROM messages WHERE characterId = :characterId AND isUnread = 1")
    fun observeUnreadCount(characterId: String): Flow<Int>
}

package com.araswqm.hachi.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.araswqm.hachi.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Karakter bazli onbellek kullanimi ozeti. Yalnizca hata ayiklama ekrani okur.
 */
data class CacheStatRow(
    val characterId: String,
    val hit: Long,
    val miss: Long,
    val requests: Long,
)

@Dao
interface MessageDao {

    /** Sohbet ekrani: eskiden yeniye (sohbet bazli, §8). */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    fun observeForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    /** Prompt icin son N mesaj (DESC; cagiran kronolojik siraya cevirir). */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun getLast(chatId: String, limit: Int): List<MessageEntity>

    /** Ozetleme icin belirli bir id'den SONRAKI mesajlar. */
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun getAfter(chatId: String, afterId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun count(chatId: String): Int

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND id > :afterId")
    suspend fun countAfter(chatId: String, afterId: Long): Int

    /** §2.1 — [beforeId]'den onceki en buyuk mesaj id'si; yoksa null. */
    @Query("SELECT MAX(id) FROM messages WHERE chatId = :chatId AND id < :beforeId")
    suspend fun lastIdBefore(chatId: String, beforeId: Long): Long?

    /** §2.1 — [maxId] dahil olmak uzere ozetlenmis sayisi. */
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND id <= :maxId")
    suspend fun countUpTo(chatId: String, maxId: Long): Int

    /** Sohbetin son mesajini bulmak icin. */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getLatest(chatId: String): MessageEntity?

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllForChat(chatId: String)

    @Query("UPDATE messages SET isUnread = 0 WHERE chatId = :chatId AND isUnread = 1")
    suspend fun markAllRead(chatId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isUnread = 1")
    suspend fun unreadCount(chatId: String): Int

    /** Hata ayiklama ekrani: karakter bazli onbellek isabeti. */
    @Query(
        """
        SELECT characterId AS characterId,
               COALESCE(SUM(cacheHitTokens), 0) AS hit,
               COALESCE(SUM(cacheMissTokens), 0) AS miss,
               COUNT(promptTokens) AS requests
        FROM messages
        GROUP BY characterId
        ORDER BY requests DESC
        """,
    )
    fun observeCacheStats(): Flow<List<CacheStatRow>>
}

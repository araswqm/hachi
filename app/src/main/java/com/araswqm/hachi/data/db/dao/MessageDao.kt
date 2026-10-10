package com.araswqm.hachi.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.araswqm.hachi.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Karakter bazli onbellek kullanimi ozeti. Yalnizca hata ayiklama ekrani okur;
 * [requests] prompt token'i raporlanmis (yani gercek bir API cagrisi olan)
 * mesaj sayisidir.
 */
data class CacheStatRow(
    val characterId: String,
    val hit: Long,
    val miss: Long,
    val requests: Long,
)

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

    /**
     * §2.1 — [beforeId]'den onceki en buyuk mesaj id'si; yoksa null.
     * Silme sonrasi ozetleme imlecini guvenli bir noktaya cekmek icin kullanilir.
     */
    @Query(
        "SELECT MAX(id) FROM messages WHERE characterId = :characterId AND id < :beforeId",
    )
    suspend fun lastIdBefore(characterId: String, beforeId: Long): Long?

    /** §2.1 — [maxId] dahil olmak uzere ozetlenmis sayisi. */
    @Query("SELECT COUNT(*) FROM messages WHERE characterId = :characterId AND id <= :maxId")
    suspend fun countUpTo(characterId: String, maxId: Long): Int

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

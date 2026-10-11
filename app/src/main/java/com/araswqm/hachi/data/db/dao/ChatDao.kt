package com.araswqm.hachi.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.araswqm.hachi.data.db.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    /** Karakter icindeki sohbetler: en son konusulan en ustte. */
    @Query(
        "SELECT * FROM chats WHERE characterId = :characterId " +
            "ORDER BY COALESCE(lastMessageAt, createdAt) DESC, id DESC",
    )
    fun observeForCharacter(characterId: String): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getById(id: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE id = :id")
    fun observeById(id: String): Flow<ChatEntity?>

    /** Karakterin en son kullanilan sohbeti (yoksa null). */
    @Query(
        "SELECT * FROM chats WHERE characterId = :characterId " +
            "ORDER BY COALESCE(lastMessageAt, createdAt) DESC, id DESC LIMIT 1",
    )
    suspend fun getLatestForCharacter(characterId: String): ChatEntity?

    @Query("SELECT COUNT(*) FROM chats WHERE characterId = :characterId")
    suspend fun countForCharacter(characterId: String): Int

    /** Karakterin tum sohbetleri (dosya temizligi icin; siralama onemsiz). */
    @Query("SELECT * FROM chats WHERE characterId = :characterId")
    suspend fun getForCharacter(characterId: String): List<ChatEntity>

    /** Karakterin butun sohbetlerindeki toplam okunmamis sayisi. */
    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM chats WHERE characterId = :characterId")
    suspend fun unreadForCharacter(characterId: String): Int

    /** Tum sohbetler (arka plan ozetleyicisi; siralama onemsiz). */
    @Query("SELECT * FROM chats")
    suspend fun getAll(): List<ChatEntity>

    @Insert
    suspend fun insert(chat: ChatEntity)

    @Update
    suspend fun update(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chats WHERE characterId = :characterId")
    suspend fun deleteAllForCharacter(characterId: String)

    @Query("UPDATE chats SET lastMessageAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun touchLastMessage(id: String, at: Long)

    @Query("UPDATE chats SET unreadCount = :count WHERE id = :id")
    suspend fun setUnreadCount(id: String, count: Int)

    @Query("UPDATE chats SET personaId = :personaId, updatedAt = :now WHERE id = :id")
    suspend fun setPersona(id: String, personaId: String?, now: Long)

    /** Persona silinince ona bagli sohbet atamalarini temizler (varsayilana doner). */
    @Query("UPDATE chats SET personaId = NULL WHERE personaId = :personaId")
    suspend fun clearPersona(personaId: String)
}

package com.araswqm.hachi.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Karakter icindeki tek bir sohbet (§8).
 *
 * Bir karakterin birden fazla sohbeti olabilir; her sohbetin kendi mesaj
 * gecmisi, kendi hafizasi (`files/memory/<chatId>.md`) ve kendi personasi
 * vardir. Ana sayfa karakteri TEK satir olarak gosterir; sohbet secimi
 * karakterin icinde yapilir.
 */
@Entity(
    tableName = "chats",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("characterId"),
        Index(value = ["characterId", "lastMessageAt"]),
    ],
)
data class ChatEntity(
    @PrimaryKey val id: String,

    val characterId: String,

    /** Sohbete ozel persona; null ise karakterin personasi, o da yoksa varsayilan. */
    val personaId: String? = null,

    /** Kullaniciya gosterilen baslik (ilk mesajdan turetilir). */
    val title: String = "",

    val createdAt: Long,
    val updatedAt: Long,

    /** Bu sohbetteki son mesaj zamani (siralama icin). */
    val lastMessageAt: Long? = null,

    /** Bu sohbetteki okunmamis mesaj sayisi. */
    val unreadCount: Int = 0,
)

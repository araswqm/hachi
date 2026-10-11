package com.araswqm.hachi.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sohbet basina BIR satir (1:1). Hafiza (.md) dosyasinin ve gizli yakinlik
 * motorunun durumunu tutar (§8: hafiza artik sohbet bazlidir). Hafiza metni
 * `files/memory/<chatId>.md` dosyasinda durur; burada yalnizca isaretciler var.
 */
@Entity(
    tableName = "memory_state",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chatId"), Index("characterId")],
)
data class MemoryStateEntity(
    @PrimaryKey val chatId: String,

    /** Sohbetin ait oldugu karakter; hata ayiklama ekrani gruplama icin. */
    val characterId: String = "",

    /** En son ozetlenen mesajin id'si; sonrasi "ozetlenmemis" sayilir. */
    val lastSummarizedMessageId: Long = 0L,

    val summarizedMessageCount: Int = 0,

    /** Hafiza dosyasindaki bolum sayisi; bir sonraki numara icin. */
    val sectionCount: Int = 0,

    // --- Gizli yakinlik motoru ---
    val intimacyScore: Double = 0.0,
    val intimacyRawHistoryJson: String = "[]",
    val lastDecayAt: Long = 0L,

    // --- Ozetleyicinin cikardigi iliski durumu ---
    val chatState: String = "",
    val characterFeeling: String = "",

    val updatedAt: Long = 0L,
)

package com.araswqm.hatir.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Karakter basina BIR satir (1:1). Hafiza (.md) dosyasinin ve gizli yakinlik
 * motorunun durumunu tutar. Hafiza metninin kendisi dosyada
 * (`files/memory/<characterId>.md`) durur; burada yalnizca isaretciler var.
 */
@Entity(
    tableName = "memory_state",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MemoryStateEntity(
    @PrimaryKey val characterId: String,

    /** En son ozetlenen mesajin id'si; bu id'den sonrasi "ozetlenmemis" sayilir. */
    val lastSummarizedMessageId: Long = 0L,

    /**
     * Simdiye kadar ozetlenen toplam mesaj sayisi (kullanici + karakter).
     * Yeniden uretme / kaydirma SAYILMAZ. Bir sonraki ozet tetigi bu sayidan
     * [com.araswqm.hatir.core.Tuning.SUMMARY_EVERY_MESSAGES] farkina bakilarak atilir.
     */
    val summarizedMessageCount: Int = 0,

    /** Hafiza dosyasindaki `## Bölüm N` sayisi; bir sonraki numara icin. */
    val sectionCount: Int = 0,

    // --- Gizli yakinlik motoru ---

    /** Nihai puan 0..100 (bkz. IntimacyScorer). Arayuzde ASLA gosterilmez. */
    val intimacyScore: Double = 0.0,

    /** Ozetleyicinin urettigi ham puanlarin son [INTIMACY_MOVING_AVERAGE_WINDOW] tanesi. */
    val intimacyRawHistoryJson: String = "[]",

    /** Puanin en son gunluk azalma (decay) uygulandigi an. */
    val lastDecayAt: Long = 0L,

    // --- Ozetleyicinin cikardigi iliski durumu ---

    /** "açık konu" | "doğal kapandı" | "gerginlik-küslük" */
    val chatState: String = "",

    /** Ozetleyicinin serbest metinle yazdigi "karakterin hissi". */
    val characterFeeling: String = "",

    val updatedAt: Long = 0L,
)

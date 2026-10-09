package com.araswqm.hatir.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tek bir sohbet mesaji. Karakter silinince CASCADE ile birlikte gider.
 *
 * Alternatif yanitlar ("kaydir/swipe"):
 *  - [variantsJson] tum alternatifleri bir JSON dizi olarak tutar.
 *  - [variantIndex] su an gosterileni secer.
 *  - [content] aktif metnin kopyasidir; sorgu/siralama ve ozetleyici bunu okur.
 * Bu kucuk tekrar, "aktif metni bulmak icin JSON'u coz" ihtiyacini ortadan kaldirir.
 */
@Entity(
    tableName = "messages",
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
        Index(value = ["characterId", "createdAt"]),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,

    /** [ROLE_USER] veya [ROLE_ASSISTANT]. */
    val role: String,
    val content: String,

    val variantsJson: String = "[]",
    val variantIndex: Int = 0,

    val createdAt: Long,

    /** Karakter kendiliğinden yazdigi ve kullanici henuz gormedigi mesaj. */
    val isUnread: Boolean = false,

    /** Bu mesaj kendiliğinden (proactive) gonderildi mi. */
    val isProactive: Boolean = false,

    val isEdited: Boolean = false,

    /** Uretim hatasi sonucu olusan yer tutucu; tekrar denenebilir. */
    val isError: Boolean = false,

    // --- Hata ayiklama ekrani icin token kullanimi ---
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val cacheHitTokens: Int? = null,
    val cacheMissTokens: Int? = null,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

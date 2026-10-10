package com.araswqm.hachi.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.araswqm.hachi.core.Tuning

/**
 * Bir karakter. Kart alanlari (name/description/personality/...) Character Card
 * V2/V3 alanlariyla birebir eslesir ki ice/disa aktarma kayipsiz olsun.
 *
 * [avatarPath] uygulama-ici goreli yoldur (`avatars/<id>.png`), mutlak degil.
 * Bkz. [com.araswqm.hachi.core.AppFiles] ve `@xml/file_paths`.
 */
@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey val id: String,
    val name: String,

    /** `avatars/<id>.png` — kullanici resmi secmediyse null. */
    val avatarPath: String? = null,

    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val exampleDialogs: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",

    /** JSON dizi (String[]). Room'da TypeConverter yerine JSON tutuyoruz. */
    val alternateGreetingsJson: String = "[]",
    val tagsJson: String = "[]",

    val creator: String = "",
    val characterVersion: String = "",

    /** lorebook — saklanir, AMA prompt'a asla eklenmez (bkz. plan). */
    val characterBookJson: String? = null,

    /** Kartın `extensions` blogu; spec alanlari dahil. */
    val extensionsJson: String? = null,

    /** "manual" | "v1" | "v2" | "v3" — kart nereden geldi. */
    val sourceSpec: String = SOURCE_MANUAL,

    val temperature: Float = Tuning.DEFAULT_TEMPERATURE,
    val maxTokens: Int = Tuning.DEFAULT_MAX_TOKENS,

    /** Kullanici bu karakter icin kendiliğinden mesaji kapatti mi. */
    val proactiveEnabled: Boolean = true,

    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long? = null,

    /** Sohbet listesindeki okunmamis rozeti. Mesajlarla senkron tutulur. */
    val unreadCount: Int = 0,

    // --- Kendiliğinden mesaj (proactive) zamanlayici durumu ---

    /** Bir sonraki kapi (gate) kontrolunun zamani. LLM `next_check_hours` yazar. */
    val nextCheckAt: Long? = null,
    val lastProactiveAt: Long? = null,

    /** Son kendiliğinden mesaj yanitlanmadiysa true; aralik katlanarak buyur. */
    val lastProactiveUnanswered: Boolean = false,

    /** Yanitlanmayan mesajlardan dolayi birikmis geri-cekilme (saat). */
    val proactiveBackoffHours: Double = 0.0,

    /** Gunluk gonderim sayaci — [dailyProactiveDate] degisince sifirlanir. */
    val dailyProactiveCount: Int = 0,

    /** Sayacin ait oldugu yerel tarih, `yyyy-MM-dd`. */
    val dailyProactiveDate: String = "",
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_V1 = "v1"
        const val SOURCE_V2 = "v2"
        const val SOURCE_V3 = "v3"
    }
}

package com.araswqm.hatir.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Kendiliğinden mesaj sisteminin her karari buraya yazilir. Hata ayiklama
 * ekranindaki "neden yazmadi?" gorunumu bu tabloyu okur.
 *
 * [stage] = "gate" (yerel, ucretsiz on eleme) veya "decision" (LLM karari).
 * [result] = "blocked" | "silent" | "sent" | "error".
 */
@Entity(
    tableName = "proactive_log",
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
        Index("timestamp"),
        Index(value = ["characterId", "timestamp"]),
    ],
)
data class ProactiveLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,
    val timestamp: Long,

    val stage: String,
    val result: String,

    /** Kapida hangi kural engelledi (or. "dnd", "daily_limit", "level"). */
    val rule: String = "",

    /** Insan okunur gerekce. */
    val reason: String = "",

    /** Gonderildiyse mesaj metni. */
    val message: String? = null,

    /** LLM'in onerdigi bir sonraki kontrol suresi (karar asamasinda). */
    val nextCheckHours: Double? = null,

    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val cacheHitTokens: Int? = null,
    val cacheMissTokens: Int? = null,
) {
    companion object {
        const val STAGE_GATE = "gate"
        const val STAGE_DECISION = "decision"

        const val RESULT_BLOCKED = "blocked"
        const val RESULT_SILENT = "silent"
        const val RESULT_SENT = "sent"
        const val RESULT_ERROR = "error"
    }
}

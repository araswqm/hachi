package com.araswqm.hatir.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ozetleyicinin cikardigi "ileride sorulacak/hatirlanacak" konu.
 * Kendiliğinden mesaj kararinda, vadesi gelmis bir takip, "konusma kapandi,
 * bekle" kuralini gecersiz kilar (bkz. plan).
 */
@Entity(
    tableName = "followups",
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
        Index(value = ["characterId", "done"]),
    ],
)
data class FollowupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,

    val text: String,

    /** Vade; zaman belirsizse null (o zaman "vadesi gelmis" sayilmaz). */
    val dueAt: Long? = null,

    val done: Boolean = false,
    val createdAt: Long,
    val doneAt: Long? = null,
)

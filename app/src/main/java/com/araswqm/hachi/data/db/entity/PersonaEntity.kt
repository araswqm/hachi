package com.araswqm.hachi.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Kullanicinin kendi karakteri ("persona"). Birden fazla olabilir; biri secilidir.
 * Karakterlerden bagimsizdir (silinen karakter personayi etkilemez).
 */
@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val about: String = "",

    /** `avatars/persona-<id>.png` — yoksa null. */
    val avatarPath: String? = null,

    /** Ayni anda yalnizca bir persona secili olabilir. */
    val isSelected: Boolean = false,

    val createdAt: Long,
    val updatedAt: Long,
)

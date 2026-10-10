package com.araswqm.hachi.data.card

import com.araswqm.hachi.core.Tuning
import com.araswqm.hachi.data.db.entity.CharacterEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Karakter kartinin (V1/V2/V3) tek bir ortak bicime indirgenmis hali.
 *
 * Ham JSON'daki alan adlari surumden surume degistigi icin ayristirma
 * [CardParser]'da yapilir; burada yalnizca normalize edilmis veri durur.
 */
data class CharacterCard(
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val exampleDialogs: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",
    val creator: String = "",
    val characterVersion: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),

    /** lorebook — saklanir, prompt'a EKLENMEZ. */
    val characterBookJson: String? = null,
    val extensionsJson: String? = null,

    /** [CharacterEntity.SOURCE_V1] / [CharacterEntity.SOURCE_V2] / [CharacterEntity.SOURCE_V3]. */
    val sourceSpec: String = CharacterEntity.SOURCE_V1,
) {
    fun toEntity(id: String, avatarPath: String?, now: Long): CharacterEntity = CharacterEntity(
        id = id,
        name = name,
        avatarPath = avatarPath,
        description = description,
        personality = personality,
        scenario = scenario,
        firstMessage = firstMessage,
        exampleDialogs = exampleDialogs,
        systemPrompt = systemPrompt,
        postHistoryInstructions = postHistoryInstructions,
        creatorNotes = creatorNotes,
        alternateGreetingsJson = encodeStringList(alternateGreetings),
        tagsJson = encodeStringList(tags),
        creator = creator,
        characterVersion = characterVersion,
        characterBookJson = characterBookJson,
        extensionsJson = extensionsJson,
        sourceSpec = sourceSpec,
        temperature = Tuning.DEFAULT_TEMPERATURE,
        maxTokens = Tuning.DEFAULT_MAX_TOKENS,
        createdAt = now,
        updatedAt = now,
    )
}

/** Room'da TypeConverter kullanmiyoruz; listeler JSON metni olarak durur. */
internal fun encodeStringList(values: List<String>): String =
    JsonArray(values.map { JsonPrimitive(it) }).toString()

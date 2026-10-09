package com.araswqm.hatir.data.card

import com.araswqm.hatir.data.db.entity.CharacterEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Karakteri Character Card **V2** JSON'una cevirir.
 *
 * V2 secildi cunku en yaygin desteklenen surumdur; V3'e ozgu alanlar (assets,
 * creation_date ...) tekrar ice aktarimda zaten kayboluyor.
 */
@Singleton
class CardWriter @Inject constructor(private val json: Json) {

    fun toV2Json(character: CharacterEntity): String = buildJsonObject {
        put("spec", SPEC_V2)
        put("spec_version", SPEC_V2_VERSION)
        put("data", buildData(character))
    }.toString()

    private fun buildData(character: CharacterEntity): JsonObject = buildJsonObject {
        put("name", character.name)
        put("description", character.description)
        put("personality", character.personality)
        put("scenario", character.scenario)
        put("first_mes", character.firstMessage)
        put("mes_example", character.exampleDialogs)
        put("creator_notes", character.creatorNotes)
        put("system_prompt", character.systemPrompt)
        put("post_history_instructions", character.postHistoryInstructions)
        put("alternate_greetings", parseArray(character.alternateGreetingsJson))
        put("tags", parseArray(character.tagsJson))
        put("creator", character.creator)
        put("character_version", character.characterVersion)
        character.characterBookJson?.let { raw ->
            (parseElement(raw) as? JsonObject)?.let { put("character_book", it) }
        }
        put("extensions", parseObject(character.extensionsJson))
    }

    /** Kaydedilmis JSON dizisini oldugu gibi bozar; bozuksa bos dizi dondurur. */
    private fun parseArray(raw: String): JsonArray =
        (parseElement(raw) as? JsonArray) ?: JsonArray(emptyList())

    private fun parseObject(raw: String?): JsonObject =
        (raw?.let(::parseElement) as? JsonObject) ?: buildJsonObject { }

    private fun parseElement(raw: String): JsonElement? =
        runCatching { json.parseToJsonElement(raw) }.getOrNull()

    private companion object {
        const val SPEC_V2 = "chara_card_v2"
        const val SPEC_V2_VERSION = "2.0"
    }
}

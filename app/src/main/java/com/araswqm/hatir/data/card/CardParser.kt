package com.araswqm.hatir.data.card

import com.araswqm.hatir.data.db.entity.CharacterEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Karakter karti JSON'unu [CharacterCard]'a cevirir.
 *
 * Uc surumu de okur:
 *  - **V1**: alanlar dogrudan kokte (`first_mes`, `mes_example`, ...).
 *  - **V2**: `{"spec":"chara_card_v2","data":{...}}`.
 *  - **V3**: `{"spec":"chara_card_v3","data":{...}}` — V2 alanlari + ek alanlar;
 *    tanimadigimiz her sey `ignoreUnknownKeys` ile sessizce atlanir.
 *
 * `spec` alani olmayan ama `data` blogu bulunan ("V2'yi sarmalayicisiz yazan"
 * araclar) kartlar da dogru sekilde cozulur.
 */
@Singleton
class CardParser @Inject constructor(private val json: Json) {

    /** @return ayristirilamazsa veya `name` bos ise null. */
    fun parse(jsonText: String): CharacterCard? {
        val root = runCatching { json.parseToJsonElement(jsonText) }.getOrNull() as? JsonObject
            ?: return null
        return fromRoot(root)
    }

    private fun fromRoot(root: JsonObject): CharacterCard? {
        val spec = root.str("spec")
        val nested = root["data"] as? JsonObject
        val wrapped = spec == SPEC_V2 || spec == SPEC_V3 || (nested != null && !root.has("name"))
        val data = if (wrapped && nested != null) nested else root

        val name = data.str("name").trim()
        if (name.isEmpty()) return null

        val sourceSpec = when {
            spec == SPEC_V3 -> CharacterEntity.SOURCE_V3
            spec == SPEC_V2 -> CharacterEntity.SOURCE_V2
            wrapped -> CharacterEntity.SOURCE_V2
            else -> CharacterEntity.SOURCE_V1
        }

        return CharacterCard(
            name = name,
            description = data.str("description"),
            personality = data.str("personality"),
            scenario = data.str("scenario"),
            firstMessage = data.str("first_mes"),
            exampleDialogs = data.str("mes_example"),
            systemPrompt = data.str("system_prompt"),
            postHistoryInstructions = data.str("post_history_instructions"),
            creatorNotes = data.str("creator_notes").ifEmpty { data.str("creatorcomment") },
            creator = data.str("creator"),
            characterVersion = data.str("character_version"),
            alternateGreetings = data.stringList("alternate_greetings"),
            tags = data.stringList("tags"),
            characterBookJson = data.objJson("character_book"),
            extensionsJson = data.objJson("extensions"),
            sourceSpec = sourceSpec,
        )
    }

    // ------------------------------------------------------------------
    // Kucuk yardimcilar; eksik/yanlis tipli alanlar sessizce yok sayilir.
    // ------------------------------------------------------------------

    private fun JsonObject.has(key: String): Boolean = this[key] != null

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.stringOrNull().orEmpty()

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.stringOrNull() }
            .orEmpty()

    private fun JsonObject.objJson(key: String): String? =
        (this[key] as? JsonObject)?.toString()

    /** Yalnizca JSON string ise icerigini verir; sayi/bool alanlari yok sayar. */
    private fun JsonPrimitive.stringOrNull(): String? = if (isString) content else null

    private companion object {
        const val SPEC_V2 = "chara_card_v2"
        const val SPEC_V3 = "chara_card_v3"
    }
}

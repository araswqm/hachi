package com.araswqm.hachi.card

import com.araswqm.hachi.data.card.CardParser
import com.araswqm.hachi.data.db.entity.CharacterEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardParserTest {

    private val parser = CardParser(
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
            coerceInputValues = true
        },
    )

    @Test
    fun `V1 karti kokteki alanlardan okunur`() {
        val card = parser.parse(
            """
            {
              "name": "Elif",
              "description": "bir yazar",
              "personality": "sakin",
              "scenario": "istanbul",
              "first_mes": "merhaba",
              "mes_example": "<START>",
              "creator_notes": "notlar",
              "creator": "aras",
              "character_version": "1.2",
              "tags": ["a", "b"]
            }
            """.trimIndent(),
        )!!

        assertEquals("Elif", card.name)
        assertEquals("bir yazar", card.description)
        assertEquals("sakin", card.personality)
        assertEquals("istanbul", card.scenario)
        assertEquals("merhaba", card.firstMessage)
        assertEquals("<START>", card.exampleDialogs)
        assertEquals("notlar", card.creatorNotes)
        assertEquals("aras", card.creator)
        assertEquals("1.2", card.characterVersion)
        assertEquals(listOf("a", "b"), card.tags)
        assertEquals(CharacterEntity.SOURCE_V1, card.sourceSpec)
    }

    @Test
    fun `V2 karti data blogundan okunur`() {
        val card = parser.parse(
            """
            {
              "spec": "chara_card_v2",
              "spec_version": "2.0",
              "data": {
                "name": "Elif",
                "first_mes": "hos geldin",
                "alternate_greetings": ["selam", "hey"],
                "creator": "aras"
              }
            }
            """.trimIndent(),
        )!!

        assertEquals("Elif", card.name)
        assertEquals("hos geldin", card.firstMessage)
        assertEquals(listOf("selam", "hey"), card.alternateGreetings)
        assertEquals(CharacterEntity.SOURCE_V2, card.sourceSpec)
    }

    @Test
    fun `V3 karti okunur ve bilinmeyen alanlar yok sayilir`() {
        val card = parser.parse(
            """
            {
              "spec": "chara_card_v3",
              "data": {
                "name": "Elif",
                "first_mes": "merhaba",
                "creation_date": 1700000000,
                "assets": [{"type": "icon", "uri": "ccdefault:"}]
              }
            }
            """.trimIndent(),
        )!!

        assertEquals("Elif", card.name)
        assertEquals("merhaba", card.firstMessage)
        assertEquals(CharacterEntity.SOURCE_V3, card.sourceSpec)
    }

    @Test
    fun `spec olmayan sarmalanmis V2 karti cozulur`() {
        // Bazi araclar V2'yi sarmalayicisiz, sadece "data" blogu ile yazar.
        val card = parser.parse(
            """
            { "data": { "name": "Elif", "description": "x" } }
            """.trimIndent(),
        )!!

        assertEquals("Elif", card.name)
        assertEquals("x", card.description)
        assertEquals(CharacterEntity.SOURCE_V2, card.sourceSpec)
    }

    @Test
    fun `koki name iceren kart V1 sayilir ve data blogu yok sayilir`() {
        val card = parser.parse(
            """
            { "name": "Kok", "data": { "name": "Ic" } }
            """.trimIndent(),
        )!!

        assertEquals("Kok", card.name)
        assertEquals(CharacterEntity.SOURCE_V1, card.sourceSpec)
    }

    @Test
    fun `creator_notes bos ise creatorcomment kullanilir`() {
        val card = parser.parse(
            """{ "name": "Elif", "creatorcomment": "eski alan" }""",
        )!!

        assertEquals("eski alan", card.creatorNotes)
    }

    @Test
    fun `character_book ve extensions JSON metni olarak korunur`() {
        val card = parser.parse(
            """
            {
              "spec": "chara_card_v2",
              "data": {
                "name": "Elif",
                "character_book": { "name": "lore", "entries": [] },
                "extensions": { "talkativeness": "0.5" }
              }
            }
            """.trimIndent(),
        )!!

        assertTrue(card.characterBookJson!!.contains("\"lore\""))
        assertTrue(card.extensionsJson!!.contains("talkativeness"))
    }

    @Test
    fun `tags icindeki string olmayan ogeler atlanir`() {
        val card = parser.parse(
            """{ "name": "Elif", "tags": ["a", 5, true, "b"] }""",
        )!!

        assertEquals(listOf("a", "b"), card.tags)
    }

    @Test
    fun `name yoksa null doner`() {
        assertNull(parser.parse("""{ "description": "x" }"""))
        assertNull(parser.parse("""{ "spec": "chara_card_v2", "data": { "description": "x" } }"""))
    }

    @Test
    fun `name bosluk ise null doner`() {
        assertNull(parser.parse("""{ "name": "   " }"""))
    }

    @Test
    fun `name string degilse null doner`() {
        assertNull(parser.parse("""{ "name": 42 }"""))
    }

    @Test
    fun `gecersiz JSON null doner`() {
        assertNull(parser.parse("bu json degil"))
        assertNull(parser.parse(""))
    }

    @Test
    fun `dizi koku null doner`() {
        assertNull(parser.parse("""[1, 2, 3]"""))
    }
}

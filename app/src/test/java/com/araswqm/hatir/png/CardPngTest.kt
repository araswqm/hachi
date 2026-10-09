package com.araswqm.hatir.png

import com.araswqm.hatir.core.png.CardPng
import com.araswqm.hatir.core.png.PngStructure
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardPngTest {

    private val v2Json = "{\"spec\":\"chara_card_v2\",\"data\":{\"name\":\"Elif\"}}"
    private val v3Json = "{\"spec\":\"chara_card_v3\",\"data\":{\"name\":\"Elif\",\"v\":3}}"

    private fun base64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    @Test
    fun `chara chunk'i okunur`() {
        val png = PngTestData.png(
            PngTestData.chunk("IHDR", ByteArray(13)),
            PngTestData.textChunk(CardPng.KEYWORD_V2, base64(v2Json)),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        assertEquals(v2Json, CardPng.readCardJson(png))
    }

    @Test
    fun `ccv3 ve chara birlikte varsa ccv3 tercih edilir`() {
        val png = PngTestData.png(
            PngTestData.chunk("IHDR", ByteArray(13)),
            PngTestData.textChunk(CardPng.KEYWORD_V2, base64(v2Json)),
            PngTestData.textChunk(CardPng.KEYWORD_V3, base64(v3Json)),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        assertEquals(v3Json, CardPng.readCardJson(png))
    }

    @Test
    fun `anahtar sozcuk buyuk-kucuk harf duyarsiz eslesir`() {
        val png = PngTestData.png(
            PngTestData.chunk("IHDR", ByteArray(13)),
            PngTestData.textChunk("ChArA", base64(v2Json)),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        assertEquals(v2Json, CardPng.readCardJson(png))
    }

    @Test
    fun `ilgisiz chunk'lar yok sayilir`() {
        val png = PngTestData.png(
            PngTestData.chunk("IHDR", ByteArray(13)),
            PngTestData.textChunk("Comment", "sadece not"),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        assertNull(CardPng.readCardJson(png))
    }

    @Test
    fun `base64 olmayan icerik null doner`() {
        val png = PngTestData.png(
            PngTestData.chunk("IHDR", ByteArray(13)),
            PngTestData.textChunk(CardPng.KEYWORD_V2, "bu base64 degil !!!"),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        assertNull(CardPng.readCardJson(png))
    }

    @Test
    fun `metin chunk'i olmayan PNG null doner`() {
        assertNull(CardPng.readCardJson(PngTestData.minimalPng()))
    }

    @Test
    fun `yazilan kart ayni JSON ile geri okunur`() {
        val original = PngTestData.minimalPng()

        val written = CardPng.writeCardPng(original, v2Json)

        assertEquals(v2Json, CardPng.readCardJson(written))
    }

    @Test
    fun `yazma sonrasi IEND hala son chunk`() {
        val original = PngTestData.minimalPng()

        val written = CardPng.writeCardPng(original, v2Json)
        val types = PngStructure.scanChunks(written).map { it.type }

        assertEquals(listOf("IHDR", "IDAT", "tEXt", "IEND"), types)
    }

    @Test
    fun `yazma orijinal baytlarin on ekini korur`() {
        val original = PngTestData.minimalPng()

        val written = CardPng.writeCardPng(original, v2Json)

        assertArrayEquals(
            original.copyOfRange(0, PngTestData.SIGNATURE.size + 4 + 4 + 13 + 4),
            written.copyOfRange(0, PngTestData.SIGNATURE.size + 4 + 4 + 13 + 4),
        )
    }
}

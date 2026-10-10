package com.araswqm.hachi.png

import com.araswqm.hachi.core.png.PngChunkReader
import com.araswqm.hachi.core.png.PngFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PngChunkReaderTest {

    @Test
    fun `tEXt chunk'ini Latin-1 cozer`() {
        val bytes = PngTestData.png(
            PngTestData.textChunk("chara", "{\"name\":\"Elif\"}"),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        val chunks = PngChunkReader.readTextChunks(bytes)

        assertEquals(1, chunks.size)
        assertEquals("chara", chunks[0].keyword)
        assertEquals("{\"name\":\"Elif\"}", chunks[0].text)
        assertEquals("tEXt", chunks[0].chunkType)
    }

    @Test
    fun `zTXt chunk'ini sikistirmasini acarak cozer`() {
        val bytes = PngTestData.png(
            PngTestData.ztxtChunk("chara", "{\"name\":\"Zeynep\"}"),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        val chunks = PngChunkReader.readTextChunks(bytes)

        assertEquals(1, chunks.size)
        assertEquals("{\"name\":\"Zeynep\"}", chunks[0].text)
        assertEquals("zTXt", chunks[0].chunkType)
    }

    @Test
    fun `iTXt sikistirilmamis chunk'i UTF-8 cozer`() {
        val bytes = PngTestData.png(
            PngTestData.itxtChunk("ccv3", "{\"ad\":\"Çağrı\"}", languageTag = "tr"),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        val chunks = PngChunkReader.readTextChunks(bytes)

        assertEquals(1, chunks.size)
        assertEquals("{\"ad\":\"Çağrı\"}", chunks[0].text)
        assertEquals("tr", chunks[0].languageTag)
    }

    @Test
    fun `iTXt sikistirilmis chunk'i acar`() {
        val bytes = PngTestData.png(
            PngTestData.itxtChunk("ccv3", "{\"x\":\"üöş\"}", compressed = true),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        val chunks = PngChunkReader.readTextChunks(bytes)

        assertEquals("{\"x\":\"üöş\"}", chunks[0].text)
    }

    @Test
    fun `metin olmayan chunk'lar atlanir`() {
        val bytes = PngTestData.png(
            PngTestData.chunk("IHDR", ByteArray(13)),
            PngTestData.chunk("IDAT", ByteArray(32)),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        assertTrue(PngChunkReader.readTextChunks(bytes).isEmpty())
    }

    @Test
    fun `birden fazla metin chunk'i dosya sirasinda doner`() {
        val bytes = PngTestData.png(
            PngTestData.textChunk("chara", "bir"),
            PngTestData.textChunk("ccv3", "iki"),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        val keywords = PngChunkReader.readTextChunks(bytes).map { it.keyword }

        assertEquals(listOf("chara", "ccv3"), keywords)
    }

    @Test
    fun `bos metin de okunur`() {
        val bytes = PngTestData.png(
            PngTestData.textChunk("chara", ""),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        val chunks = PngChunkReader.readTextChunks(bytes)

        assertEquals(1, chunks.size)
        assertEquals("", chunks[0].text)
    }

    @Test
    fun `ayirici olmayan tEXt yok sayilir`() {
        val bytes = PngTestData.png(
            PngTestData.chunk("tEXt", PngTestData.latin1("ayiricisiz")),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        assertTrue(PngChunkReader.readTextChunks(bytes).isEmpty())
    }

    @Test
    fun `zTXt bilinmeyen sikistirma yonteminde hata verir`() {
        val bytes = PngTestData.png(
            PngTestData.ztxtChunk("chara", "veri", method = 1),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        val error = expectThrows { PngChunkReader.readTextChunks(bytes) }
        assertTrue(error.message!!.contains("yontem"))
    }

    @Test
    fun `iTXt gecersiz sikistirma bayraginda hata verir`() {
        val bytes = PngTestData.png(
            PngTestData.itxtChunk("chara", "veri", flag = 7),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        val error = expectThrows { PngChunkReader.readTextChunks(bytes) }
        assertTrue(error.message!!.contains("bayrag"))
    }

    @Test
    fun `iTXt sonlandiricilari eksikse hata verir`() {
        // Dil etiketi NUL ile sonlanmiyor.
        val broken = PngTestData.chunk(
            "iTXt",
            PngTestData.latin1("chara") + byteArrayOf(0, 0, 0) + PngTestData.latin1("tr"),
        )
        val bytes = PngTestData.png(broken, PngTestData.chunk("IEND", ByteArray(0)))

        expectThrows { PngChunkReader.readTextChunks(bytes) }
    }

    @Test
    fun `bos anahtarli chunk yok sayilir`() {
        val bytes = PngTestData.png(
            PngTestData.chunk("tEXt", byteArrayOf(0) + PngTestData.latin1("deger")),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        assertNull(PngChunkReader.readTextChunks(bytes).firstOrNull())
    }

    private fun expectThrows(block: () -> Unit): PngFormatException {
        try {
            block()
        } catch (e: PngFormatException) {
            return e
        }
        throw AssertionError("PngFormatException beklendi ama firlatilmadi.")
    }
}

package com.araswqm.hachi.png

import com.araswqm.hachi.core.png.PngChunkReader
import com.araswqm.hachi.core.png.PngChunkWriter
import com.araswqm.hachi.core.png.PngStructure
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PngChunkWriterTest {

    @Test
    fun `eklenen chunk okunabilir`() {
        val original = PngTestData.minimalPng()

        val written = PngChunkWriter.appendTextChunk(original, "chara", "{\"name\":\"Ali\"}")

        val chunks = PngChunkReader.readTextChunks(written)
        assertEquals(1, chunks.size)
        assertEquals("chara", chunks[0].keyword)
        assertEquals("{\"name\":\"Ali\"}", chunks[0].text)
    }

    @Test
    fun `IEND hala son chunk`() {
        val written = PngChunkWriter.appendTextChunk(PngTestData.minimalPng(), "chara", "x")

        val types = PngStructure.scanChunks(written).map { it.type }
        assertEquals("IEND", types.last())
        assertEquals(listOf("IHDR", "IDAT", "tEXt", "IEND"), types)
    }

    @Test
    fun `orijinal baytlar degismeden korunur`() {
        val original = PngTestData.minimalPng()
        val iendOffset = PngStructure.scanChunks(original).last().headerOffset

        val written = PngChunkWriter.appendTextChunk(original, "chara", "{\"a\":1}")

        // IEND oncesi her sey ayni olmali.
        assertArrayEquals(original.copyOfRange(0, iendOffset), written.copyOfRange(0, iendOffset))
        // IEND ve sonrasi da aynen tasinmis olmali.
        assertArrayEquals(
            original.copyOfRange(iendOffset, original.size),
            written.copyOfRange(written.size - (original.size - iendOffset), written.size),
        )
        assertTrue(written.size > original.size)
    }

    @Test
    fun `buyuk base64 yuku yazilip okunabilir`() {
        // Gercek kart gibi: IDAT'li PNG + base64 chara chunk'i.
        val payload = Base64.getEncoder().encodeToString("{\"name\":\"Uzun\"}".toByteArray())
        val original = PngTestData.png(
            PngTestData.chunk("IHDR", ByteArray(13)),
            PngTestData.chunk("IDAT", ByteArray(4096)),
            PngTestData.chunk("IEND", ByteArray(0)),
        )

        val written = PngChunkWriter.appendTextChunk(original, "chara", payload)

        val chunks = PngChunkReader.readTextChunks(written)
        assertEquals(payload, chunks.single().text)
        // IDAT bozulmadi.
        assertArrayEquals(
            ByteArray(4096),
            written.copyOfRange(
                PngStructure.scanChunks(written)[1].dataOffset,
                PngStructure.scanChunks(written)[1].dataOffset + 4096,
            ),
        )
    }

    @Test
    fun `bos anahtar reddedilir`() {
        try {
            PngChunkWriter.appendTextChunk(PngTestData.minimalPng(), "", "x")
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("IllegalArgumentException beklendi.")
    }
}

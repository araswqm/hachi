package com.araswqm.hatir.core.png

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * PNG'ye metin chunk'i EKLER; var olan baytlara dokunmaz.
 *
 * Karakter karti disa aktariminda avatar YENIDEN SIKISTIRILMAZ (yeniden
 * kodlama, dosyadaki tum diger metin chunk'larini ve renk profilini yok eder).
 * Bu yuzden yeni `tEXt` chunk'i dogrudan IEND'den hemen once araya sokulur;
 * geri kalan her bayt aynen kopyalanir.
 */
object PngChunkWriter {

    /**
     * [original] PNG'sinin IEND chunk'indan hemen once, verilen anahtar ve
     * metinle bir `tEXt` chunk'i ekler.
     *
     * @throws PngFormatException girdi gecerli bir PNG degilse veya IEND yoksa.
     */
    fun appendTextChunk(original: ByteArray, keyword: String, text: String): ByteArray {
        require(keyword.isNotEmpty()) { "PNG metin anahtari bos olamaz." }

        val iend = PngStructure.scanChunks(original)
            .lastOrNull { it.type == PngStructure.TYPE_IEND }
            ?: throw PngFormatException("PNG'de IEND chunk'i bulunamadi.")

        val chunk = buildTextChunk(keyword, text)
        val out = ByteArrayOutputStream(original.size + chunk.size)
        out.write(original, 0, iend.headerOffset)
        out.write(chunk)
        out.write(original, iend.headerOffset, original.size - iend.headerOffset)
        return out.toByteArray()
    }

    /** `[length:4][type:4][keyword\0text][crc:4]`; hepsi Latin-1/ASCII. */
    private fun buildTextChunk(keyword: String, text: String): ByteArray {
        val data = ByteArrayOutputStream()
        data.write(keyword.toByteArray(Charsets.ISO_8859_1))
        data.write(0)
        data.write(text.toByteArray(Charsets.ISO_8859_1))
        val dataBytes = data.toByteArray()

        val typeBytes = TYPE_TEXT.toByteArray(Charsets.US_ASCII)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(dataBytes)

        val out = ByteArrayOutputStream(dataBytes.size + 12)
        writeUInt32(out, dataBytes.size.toLong())
        out.write(typeBytes)
        out.write(dataBytes)
        writeUInt32(out, crc.value)
        return out.toByteArray()
    }

    /** Buyuk-endian 32 bit (alt 32 bit kullanilir). */
    private fun writeUInt32(out: ByteArrayOutputStream, value: Long) {
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }
}

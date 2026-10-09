package com.araswqm.hatir.png

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Testler icin elle PNG baytlari uretir. Uretim koduna bagimli DEGIL; boylece
 * testler uretim koduyla ayni hatayi paylasmaz.
 */
object PngTestData {

    val SIGNATURE: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun latin1(value: String): ByteArray = value.toByteArray(Charsets.ISO_8859_1)

    /** Gecerli CRC ile tam bir chunk uretir. */
    fun chunk(type: String, data: ByteArray, crcOverride: Long? = null): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)

        val out = ByteArrayOutputStream(data.size + 12)
        writeUInt32(out, data.size.toLong())
        out.write(typeBytes)
        out.write(data)
        writeUInt32(out, crcOverride ?: crc.value)
        return out.toByteArray()
    }

    fun png(vararg chunks: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        for (chunk in chunks) out.write(chunk)
        return out.toByteArray()
    }

    /** Gecerli ama metin icermeyen bir PNG. */
    fun minimalPng(): ByteArray = png(
        chunk("IHDR", ByteArray(13)),
        chunk("IDAT", ByteArray(8)),
        chunk("IEND", ByteArray(0)),
    )

    fun textChunk(keyword: String, text: String): ByteArray =
        chunk("tEXt", latin1(keyword) + byteArrayOf(0) + latin1(text))

    fun ztxtChunk(keyword: String, text: String, method: Int = 0): ByteArray =
        chunk(
            "zTXt",
            latin1(keyword) + byteArrayOf(0) + byteArrayOf(method.toByte()) + deflate(latin1(text)),
        )

    fun itxtChunk(
        keyword: String,
        text: String,
        compressed: Boolean = false,
        languageTag: String = "",
        translatedKeyword: String = "",
        flag: Int = if (compressed) 1 else 0,
        method: Int = 0,
    ): ByteArray {
        val body = when (flag) {
            0 -> text.toByteArray(Charsets.UTF_8)
            else -> deflate(text.toByteArray(Charsets.UTF_8))
        }
        return chunk(
            "iTXt",
            latin1(keyword) + byteArrayOf(0) +
                byteArrayOf(flag.toByte(), method.toByte()) +
                latin1(languageTag) + byteArrayOf(0) +
                latin1(translatedKeyword) + byteArrayOf(0) +
                body,
        )
    }

    /** zlib cercevesiyle (Inflater'in bekledigi bicim) sikistirir. */
    fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        while (!deflater.finished()) {
            val produced = deflater.deflate(buffer)
            out.write(buffer, 0, produced)
        }
        deflater.end()
        return out.toByteArray()
    }

    fun writeUInt32(out: ByteArrayOutputStream, value: Long) {
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }

    /** Imza + ham (CRC'siz dogrulanmayan) chunk basligi; sinir testleri icin. */
    fun rawHeaderOnly(length: Long, type: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        writeUInt32(out, length)
        out.write(type.toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }
}

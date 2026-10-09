package com.araswqm.hatir.core.png

import com.araswqm.hatir.core.Tuning
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/** PNG dosya imzasi (8 bayt). */
internal val PNG_SIGNATURE: ByteArray = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)

/**
 * Bir PNG chunk'inin YERI. Verinin kendisi kopyalanmaz; bu, avatar gibi birkaç
 * megabaytlik IDAT bloklarinin gereksiz yere belleğe kopyalanmasini onler.
 *
 * Bayt duzeni: `[length:4][type:4][data:length][crc:4]`
 */
internal class ChunkHeader(
    /** 4 harfli tip, ör. "IHDR", "tEXt", "IEND". */
    val type: String,
    /** `length` alaninin dosyadaki ofseti. Araya chunk eklemek icin bu kullanilir. */
    val headerOffset: Int,
    val dataOffset: Int,
    val dataLength: Int,
)

/**
 * PNG'nin iskeletini cozer: imzayi dogrular, chunk'lari dolasir, her chunk'in
 * CRC-32'sini ve boyutunu kontrol eder. Metin cozumlemesi [PngChunkReader]'da.
 */
internal object PngStructure {

    private const val LENGTH_SIZE = 4
    private const val TYPE_SIZE = 4
    private const val CRC_SIZE = 4
    private const val HEADER_SIZE = LENGTH_SIZE + TYPE_SIZE

    /**
     * Tum chunk'lari IEND dahil sirayla dondurur.
     *
     * @throws PngFormatException imza yanlissa, bir chunk basligi/CRC'si bozuksa,
     *   beklenen baytlar dosyada yoksa veya bir chunk boyut sinirini asiyorsa.
     */
    fun scanChunks(bytes: ByteArray): List<ChunkHeader> {
        if (bytes.size < PNG_SIGNATURE.size) {
            throw PngFormatException("Dosya PNG icin cok kucuk (${bytes.size} bayt).")
        }
        for (i in PNG_SIGNATURE.indices) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                throw PngFormatException("PNG imzasi gecersiz; dosya PNG degil.")
            }
        }

        val chunks = ArrayList<ChunkHeader>()
        var offset = PNG_SIGNATURE.size

        while (true) {
            if (offset + HEADER_SIZE > bytes.size) {
                // IEND'e ulasmadan dosya bitti.
                throw PngFormatException("PNG kirpilmis: chunk basligi eksik (ofset $offset).")
            }

            val dataLength = readInt(bytes, offset)
            if (dataLength < 0) {
                throw PngFormatException("PNG bozuk: negatif chunk uzunlugu.")
            }
            if (dataLength > Tuning.PNG_CHUNK_SIZE_LIMIT_BYTES) {
                throw PngFormatException(
                    "PNG bozuk: chunk uzunlugu sinir asiyor " +
                        "($dataLength > ${Tuning.PNG_CHUNK_SIZE_LIMIT_BYTES}).",
                )
            }

            val typeOffset = offset + LENGTH_SIZE
            val type = String(bytes, typeOffset, TYPE_SIZE, Charsets.US_ASCII)
            if (!isAlphabeticType(type)) {
                throw PngFormatException("PNG bozuk: gecersiz chunk tipi '$type'.")
            }

            val dataOffset = typeOffset + TYPE_SIZE
            val dataEnd = dataOffset + dataLength
            val crcEnd = dataEnd + CRC_SIZE
            if (crcEnd > bytes.size) {
                throw PngFormatException(
                    "PNG kirpilmis: '$type' chunk'i icin yeterli bayt yok " +
                        "(gereken $crcEnd, dosya ${bytes.size}).",
                )
            }

            verifyCrc(bytes, typeOffset, dataEnd, crcEnd, type)

            chunks += ChunkHeader(
                type = type,
                headerOffset = offset,
                dataOffset = dataOffset,
                dataLength = dataLength,
            )

            if (type == TYPE_IEND) break
            offset = crcEnd
        }

        return chunks
    }

    /** Beklenen degeri dosyadaki CRC alanindan okur ve karsilastirir. */
    private fun verifyCrc(
        bytes: ByteArray,
        typeOffset: Int,
        dataEnd: Int,
        crcEnd: Int,
        type: String,
    ) {
        val crc = CRC32()
        crc.update(bytes, typeOffset, dataEnd - typeOffset)
        val actual = crc.value
        val expected = readIntUnsigned(bytes, dataEnd)
        if (actual != expected) {
            throw PngFormatException(
                "PNG bozuk: '$type' chunk'inin CRC'si tutmuyor " +
                    "(beklenen ${hex(expected)}, bulunan ${hex(actual)}).",
            )
        }
    }

    /**
     * zlib ile sikistirilmis veriyi acar.
     *
     * [limit] asilirsa durur: kucuk bir girdiden devasa cikti ureten "zip bomb"
     * dosyalarina karsi koruma.
     */
    fun inflate(compressed: ByteArray, limit: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream(minOf(compressed.size * 4, limit).coerceIn(64, limit))
            val buffer = ByteArray(BUFFER_SIZE)
            while (!inflater.finished()) {
                val produced = inflater.inflate(buffer)
                if (produced > 0) {
                    if (out.size() + produced > limit) {
                        throw PngFormatException("Sikistirilmis metin sinir asiyor (zip bomb?).")
                    }
                    out.write(buffer, 0, produced)
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    break
                }
            }
            if (!inflater.finished()) {
                throw PngFormatException("zlib akisi bozuk veya kirpilmis.")
            }
            return out.toByteArray()
        } catch (e: DataFormatException) {
            throw PngFormatException("Sikistirilmis metin cozulemedi: ${e.message}", e)
        } finally {
            inflater.end()
        }
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    /** Buyuk-endian isaretli 32 bit okur. */
    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    /** Isaretsiz 32 bit olarak okur (CRC karsilastirmasi icin). */
    private fun readIntUnsigned(bytes: ByteArray, offset: Int): Long =
        readInt(bytes, offset).toLong() and 0xFFFFFFFFL

    /** PNG spec: chunk tipi yalnizca ASCII harflerden olusur. */
    private fun isAlphabeticType(type: String): Boolean =
        type.length == TYPE_SIZE && type.all { it in 'A'..'Z' || it in 'a'..'z' }

    private fun hex(value: Long): String = "0x" + value.toString(16).uppercase()

    private const val BUFFER_SIZE = 8 * 1024

    const val TYPE_IEND = "IEND"
}

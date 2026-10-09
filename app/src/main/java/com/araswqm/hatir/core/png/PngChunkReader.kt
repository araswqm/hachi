package com.araswqm.hatir.core.png

import com.araswqm.hatir.core.Tuning

/**
 * PNG icindeki metin chunk'larini okur: `tEXt`, `zTXt`, `iTXt`.
 *
 * Metin olmayan chunk'lar (IHDR, IDAT, IEND ...) sessizce atlanir. Bozuk yapi
 * (CRC, kirpiklik, sinir asimi) [PngStructure] tarafindan [PngFormatException]
 * ile bildirilir.
 *
 * Karakter karti dosyalarinda metin cogu zaman `iTXt` icinde UTF-8, bazen de
 * `tEXt` icinde Latin-1 olarak durur. Ikisi de desteklenir.
 */
object PngChunkReader {

    const val TYPE_TEXT = "tEXt"
    const val TYPE_ZTXT = "zTXt"
    const val TYPE_ITXT = "iTXt"

    /** Dosyadaki tum metin chunk'larini dosya sirasinda dondurur. */
    fun readTextChunks(bytes: ByteArray): List<PngTextChunk> =
        PngStructure.scanChunks(bytes).mapNotNull { readTextChunk(bytes, it) }

    private fun readTextChunk(bytes: ByteArray, header: ChunkHeader): PngTextChunk? {
        val data = bytes.copyOfRange(header.dataOffset, header.dataOffset + header.dataLength)
        return when (header.type) {
            TYPE_TEXT -> parseText(data)
            TYPE_ZTXT -> parseZtxt(data)
            TYPE_ITXT -> parseItxt(data)
            else -> null
        }
    }

    /** `keyword\0text`; ikisi de Latin-1. */
    private fun parseText(data: ByteArray): PngTextChunk? {
        val separator = data.indexOfByte(NUL)
        if (separator <= 0) return null
        val keyword = String(data, 0, separator, Charsets.ISO_8859_1)
        val text = String(data, separator + 1, data.size - separator - 1, Charsets.ISO_8859_1)
        return PngTextChunk(keyword, text, TYPE_TEXT)
    }

    /** `keyword\0` + compressionMethod(1) + zlib(text). */
    private fun parseZtxt(data: ByteArray): PngTextChunk? {
        val separator = data.indexOfByte(NUL)
        if (separator <= 0) return null
        val keyword = String(data, 0, separator, Charsets.ISO_8859_1)
        val methodOffset = separator + 1
        if (methodOffset >= data.size) {
            throw PngFormatException("zTXt bozuk: sikistirma yontemi bayti eksik.")
        }
        val method = data[methodOffset].toInt() and 0xFF
        if (method != COMPRESSION_METHOD_DEFLATE) {
            throw PngFormatException("zTXt: bilinmeyen sikistirma yontemi ($method).")
        }
        val compressed = data.copyOfRange(methodOffset + 1, data.size)
        val text = String(
            PngStructure.inflate(compressed, Tuning.PNG_TEXT_DECODE_LIMIT_BYTES),
            Charsets.ISO_8859_1,
        )
        return PngTextChunk(keyword, text, TYPE_ZTXT)
    }

    /**
     * `keyword\0` + compressionFlag(1) + compressionMethod(1) +
     * `languageTag\0` + `translatedKeyword\0` + text (UTF-8; bayrak 1 ise zlib).
     */
    private fun parseItxt(data: ByteArray): PngTextChunk? {
        val keywordEnd = data.indexOfByte(NUL)
        if (keywordEnd <= 0) return null
        val keyword = String(data, 0, keywordEnd, Charsets.ISO_8859_1)

        var pos = keywordEnd + 1
        if (pos + 2 > data.size) {
            throw PngFormatException("iTXt bozuk: sikistirma alanlari eksik.")
        }
        val compressionFlag = data[pos].toInt() and 0xFF
        val compressionMethod = data[pos + 1].toInt() and 0xFF
        pos += 2

        val languageEnd = data.indexOfByte(NUL, pos)
        if (languageEnd < 0) throw PngFormatException("iTXt bozuk: dil etiketi sonlanmiyor.")
        val languageTag = String(data, pos, languageEnd - pos, Charsets.ISO_8859_1)
        pos = languageEnd + 1

        val translatedEnd = data.indexOfByte(NUL, pos)
        if (translatedEnd < 0) {
            throw PngFormatException("iTXt bozuk: cevrilmis anahtar sozcuk sonlanmiyor.")
        }
        pos = translatedEnd + 1

        val raw = data.copyOfRange(pos, data.size)
        val textBytes = when (compressionFlag) {
            ITXT_UNCOMPRESSED -> raw
            ITXT_COMPRESSED -> {
                if (compressionMethod != COMPRESSION_METHOD_DEFLATE) {
                    throw PngFormatException(
                        "iTXt: bilinmeyen sikistirma yontemi ($compressionMethod).",
                    )
                }
                PngStructure.inflate(raw, Tuning.PNG_TEXT_DECODE_LIMIT_BYTES)
            }
            else -> throw PngFormatException("iTXt: gecersiz sikistirma bayragi ($compressionFlag).")
        }
        val text = String(textBytes, Charsets.UTF_8)
        return PngTextChunk(keyword, text, TYPE_ITXT, languageTag.ifEmpty { null })
    }

    /** [start]'tan itibaren ilk [value] baytinin konumu; bulunamazsa -1. */
    private fun ByteArray.indexOfByte(value: Byte, start: Int = 0): Int {
        for (i in start until size) {
            if (this[i] == value) return i
        }
        return -1
    }

    private const val NUL: Byte = 0
    private const val COMPRESSION_METHOD_DEFLATE = 0
    private const val ITXT_UNCOMPRESSED = 0
    private const val ITXT_COMPRESSED = 1
}

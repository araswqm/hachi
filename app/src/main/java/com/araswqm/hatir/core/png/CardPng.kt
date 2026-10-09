package com.araswqm.hatir.core.png

import java.util.Base64

/**
 * Karakter kartinin PNG icine gomulmus JSON yukunu okur/yazar.
 *
 * **Okuma once `ccv3`, sonra `chara`:** V3 kartlar yuklerini `ccv3`
 * anahtar sozcugu altinda tasir; V2 araclari `chara` kullanir. Ayni dosyada
 * ikisi de bulunabilir; bu durumda `ccv3` (daha yeni spec) tercih edilir.
 *
 * **Yazma `chara` olarak yapilir:** en genis desteklenen anahtar sozcuk budur
 * ve icerik zaten V2 JSON'udur. Avatar PNG'si **yeniden sikistirilmaz**;
 * chunk mevcut baytlarin sonuna eklenir (yeniden kodlama meta veriyi bozar).
 */
object CardPng {

    const val KEYWORD_V3 = "ccv3"
    const val KEYWORD_V2 = "chara"

    /** Yuku bulunamaz/cozulemezse null. */
    fun readCardJson(bytes: ByteArray): String? {
        val chunks = PngChunkReader.readTextChunks(bytes)
        val byKeyword = chunks.groupBy { it.keyword.lowercase() }
        val candidates = buildList {
            addAll(byKeyword[KEYWORD_V3].orEmpty())
            addAll(byKeyword[KEYWORD_V2].orEmpty())
        }
        return candidates.firstNotNullOfOrNull { CardPayloadDecoder.decode(it.text) }
    }

    /** Verilen V2 JSON'unu orijinal PNG'nin sonuna `chara` chunk'i olarak ekler. */
    fun writeCardPng(originalBytes: ByteArray, jsonText: String): ByteArray {
        val encoded = Base64.getEncoder().encodeToString(jsonText.toByteArray(Charsets.UTF_8))
        return PngChunkWriter.appendTextChunk(originalBytes, KEYWORD_V2, encoded)
    }
}

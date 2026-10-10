package com.araswqm.hachi.core.png

/**
 * PNG metin yiginin (chunk) cozulmus hali.
 *
 * @param keyword chunk'in anahtari; karakter kartlari icin "chara" veya "ccv3".
 * @param text cozulmus metin. `chara` icin genellikle base64 kodlu JSON.
 * @param chunkType kaynak chunk tipi: "tEXt", "zTXt" veya "iTXt".
 * @param languageTag yalnizca iTXt icin; bos ise null.
 */
data class PngTextChunk(
    val keyword: String,
    val text: String,
    val chunkType: String,
    val languageTag: String? = null,
)

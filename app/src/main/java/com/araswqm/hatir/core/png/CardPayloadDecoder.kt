package com.araswqm.hatir.core.png

import com.araswqm.hatir.core.Tuning
import java.util.Base64

/**
 * Bir metin chunk'inin icerigini karakter karti JSON metnine cevirir.
 *
 * Kabul edilen iki bicim:
 *  - dogrudan JSON (`{` ile baslar),
 *  - base64 kodlu JSON (`chara` chunk'larinin ezici cogunlugu boyle).
 *
 * `java.util.Base64` (Android'in `android.util.Base64`'u DEGIL) kullanilir;
 * boylece mantik saf JVM birim testlerinde de aynen calisir.
 */
object CardPayloadDecoder {

    /**
     * @return JSON metni veya cozulemezse null.
     */
    fun decode(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // Zaten JSON'sa dokunma.
        if (trimmed.startsWith("{")) return trimmed

        val compact = trimmed.filterNot { it.isWhitespace() }
        if (compact.length > Tuning.PNG_TEXT_DECODE_LIMIT_BYTES) return null

        val decoded = decodeBase64(compact) ?: return null
        if (decoded.size > Tuning.PNG_TEXT_DECODE_LIMIT_BYTES) return null

        val asText = String(decoded, Charsets.UTF_8).trim()
        return asText.takeIf { it.startsWith("{") }
    }

    private fun decodeBase64(value: String): ByteArray? {
        val padded = pad(value)
        return try {
            Base64.getDecoder().decode(padded)
        } catch (_: IllegalArgumentException) {
            try {
                Base64.getUrlDecoder().decode(padded)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    /** Bazi kartlar dolgusuz base64 uretir; standart cozucu dolgu ister. */
    private fun pad(value: String): String {
        val remainder = value.length % 4
        return if (remainder == 0) value else value + "=".repeat(4 - remainder)
    }
}

package com.araswqm.hatir.data.ai

/**
 * Karakter kartlarinda kullanilan `{{char}}` / `{{user}}` yer tutucularini cozer.
 *
 * Kartlar (Character Card V1/V2/V3) neredeyse her zaman `description`,
 * `first_mes`, `mes_example` gibi alanlarda `{{char}}` ve `{{user}}` yazar.
 * Bu sinif o metinleri somut adlarla degistirir.
 *
 * - `{{char}}` → karakterin adi
 * - `{{user}}` / `{{persona}}` → secili personanin adi; bos ise
 *   [DEFAULT_USER_NAME]
 *
 * Cozme uc katmanda da yapilir: prompt kurulurken ([PromptBuilder]), mesaj
 * veritabanina yazilirken ([com.araswqm.hatir.data.chat.ChatRepository]) ve
 * gosterilirken ([com.araswqm.hatir.ui.chat.ChatViewModel]). Zaten cozulmus bir
 * metinde `{{` kalmadigi icin tekrar cagirmak zararsizdir.
 */
object Placeholders {

    /** Persona adi bos oldugunda `{{user}}` yerine gececek metin. */
    const val DEFAULT_USER_NAME = "Kullanıcı"

    /** `{{ char }}` gibi bosluklu/esnek yazimlari da yakalar (buyuk/kucuk harf duyarsiz). */
    private val PATTERN = Regex(
        pattern = "\\{\\{\\s*(char|user|persona)\\s*\\}\\}",
        option = RegexOption.IGNORE_CASE,
    )

    /**
     * [text] icindeki yer tutuculari cozer. Hizli cikis: metinde `{{` yoksa
     * hicbir islem yapilmaz (en sicak yol — neredeyse tum mesajlar).
     */
    fun resolve(text: String, characterName: String, userName: String?): String {
        if (text.isEmpty() || !text.contains("{{")) return text
        val user = userName?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_NAME
        return PATTERN.replace(text) { match ->
            if (match.groupValues[1].equals("char", ignoreCase = true)) characterName else user
        }
    }
}

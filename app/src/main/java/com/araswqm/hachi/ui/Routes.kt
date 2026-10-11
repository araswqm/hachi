package com.araswqm.hachi.ui

import android.net.Uri

/**
 * Basit metin tabanli rotalar. Karakter kimlikleri UUID oldugu icin URL
 * kodlamasi gerekmez, ama ileride baska parametre eklenirse diye [Uri.encode]
 * kullaniyoruz.
 */
object Routes {

    const val CHATS = "chats"
    const val SETTINGS = "settings"

    /** `characterId` yoksa yeni karakter olusturulur. */
    const val EDITOR_PATTERN = "editor?characterId={characterId}"

    const val ARG_CHARACTER_ID = "characterId"
    const val ARG_CHAT_ID = "chatId"

    /**
     * Karakter ayrintilari. [ARG_CHAT_ID] verilirse persona secimi o SOHBETE
     * ozel uygulanir (§8); yoksa karakterin genel personasi degisir.
     */
    const val DETAIL_PATTERN = "detail/{characterId}?chatId={chatId}"

    /** Sohbet ekrani. [ARG_CHAT_ID] verilirse o sohbet, yoksa karakterin son sohbeti acilir. */
    const val CHAT_PATTERN = "chat/{characterId}?chatId={chatId}"

    /** Persona yonetimi (§3); Ayarlar'dan erisilir. */
    const val PERSONAS = "personas"

    fun editor(characterId: String? = null): String =
        if (characterId.isNullOrBlank()) "editor"
        else "editor?$ARG_CHARACTER_ID=${Uri.encode(characterId)}"

    fun detail(characterId: String, chatId: String? = null): String =
        if (chatId.isNullOrBlank()) "detail/${Uri.encode(characterId)}"
        else "detail/${Uri.encode(characterId)}?$ARG_CHAT_ID=${Uri.encode(chatId)}"

    fun chat(characterId: String, chatId: String? = null): String =
        if (chatId.isNullOrBlank()) "chat/${Uri.encode(characterId)}"
        else "chat/${Uri.encode(characterId)}?$ARG_CHAT_ID=${Uri.encode(chatId)}"
}

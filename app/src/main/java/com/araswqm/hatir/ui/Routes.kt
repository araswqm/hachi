package com.araswqm.hatir.ui

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

    const val DETAIL_PATTERN = "detail/{characterId}"

    /** Sohbet ekrani. */
    const val CHAT_PATTERN = "chat/{characterId}"

    /** Gizli hata ayiklama ekrani; Ayarlar'dan erisilir. */
    const val DEBUG = "debug"

    fun editor(characterId: String? = null): String =
        if (characterId.isNullOrBlank()) "editor"
        else "editor?$ARG_CHARACTER_ID=${Uri.encode(characterId)}"

    fun detail(characterId: String): String = "detail/${Uri.encode(characterId)}"

    fun chat(characterId: String): String = "chat/${Uri.encode(characterId)}"
}

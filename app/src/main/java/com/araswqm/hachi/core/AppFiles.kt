package com.araswqm.hachi.core

import java.util.Locale

/**
 * Uygulama ici dosya duzeni. Tum yollar `filesDir` (veya `cacheDir`) GORELIdir;
 * mutlak yol tutmak, uygulama tasindiginda/veri kopyalandiginda kirilir.
 *
 * Buradaki klasor adlari `res/xml/file_paths.xml` ile birebir eslesmeli,
 * aksi halde FileProvider paylasimi calismaz.
 */
object AppFiles {

    /** Karakter ve persona resimleri: `files/avatars/`. */
    const val AVATARS_DIR = "avatars"

    /** Hafiza dosyalari: `files/memory/<characterId>.md`. */
    const val MEMORY_DIR = "memory"

    /** Disa aktarilan kartlar icin gecici klasor: `cache/exports/`. */
    const val EXPORTS_DIR = "exports"

    /** Karakter ve persona kimlikleri UUID seklindedir. */
    fun newId(): String = java.util.UUID.randomUUID().toString()

    fun avatarRelPath(characterId: String): String = "$AVATARS_DIR/$characterId.png"

    /** Persona resimlerinin karakterlerle cakismamasi icin ayri on ek. */
    fun personaAvatarRelPath(personaId: String): String = "$AVATARS_DIR/persona-$personaId.png"

    fun memoryRelPath(characterId: String): String = "$MEMORY_DIR/$characterId.md"

    /**
     * Disa aktarilan kart dosyasi icin guvenli ad: `Ada Lovelace` ->
     * `ada-lovelace-<id ilk 8>.png`. Turkce karakterler ASCII'ye indirilir.
     */
    fun exportFileName(characterName: String, characterId: String): String {
        val slug = characterName
            .lowercase(Locale.ROOT)
            .map { c ->
                when (c) {
                    'ç' -> "c"; 'ğ' -> "g"; 'ı' -> "i"; 'ö' -> "o"; 'ş' -> "s"; 'ü' -> "u"
                    'â' -> "a"; 'î' -> "i"; 'û' -> "u"
                    else -> c
                }
            }
            .joinToString("")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifBlank { "karakter" }
        val shortId = characterId.take(8)
        return "$slug-$shortId.png"
    }
}
